package net.corda.samples.solana.bridging.token

import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import net.corda.core.solana.Pubkey
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.node.utilities.solana.AccountManagement
import net.corda.node.utilities.solana.TokenManagement
import net.corda.node.utilities.solana.TokenProgram
import net.corda.samples.stockpaydividend.flows.CreateAndIssueStock
import net.corda.samples.stockpaydividend.flows.GetStockBalance
import net.corda.samples.stockpaydividend.flows.IssueMoney
import net.corda.samples.stockpaydividend.flows.MoveStock
import net.corda.samples.stockpaydividend.states.StockState
import net.corda.solana.notary.common.FileSigner
import net.corda.solana.notary.common.SolanaClient
import net.corda.solana.sdk.Token2022
import net.corda.testing.common.internal.eventually
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.TestCordapp
import net.corda.testing.node.User
import net.corda.testing.solana.SolanaTestValidator
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import software.sava.core.accounts.PublicKey
import software.sava.core.accounts.Signer
import software.sava.core.accounts.SolanaAccounts
import software.sava.core.accounts.meta.AccountMeta
import software.sava.core.accounts.token.Token2022Account
import software.sava.core.tx.Instruction
import software.sava.rpc.json.http.client.SolanaRpcClient
import java.math.BigDecimal
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ExecutionException

open class BridgingTokenDriverTest {

    protected val log = LoggerFactory.getLogger(BridgingTokenDriverTest::class.java)
    private lateinit var validator: SolanaTestValidator
    protected lateinit var tokenManagement: TokenManagement
    protected lateinit var accountManagement: AccountManagement
    protected lateinit var solanaNotarySigner: FileSigner
    protected lateinit var solanaClient: SolanaClient

    // A directory with Corda Notary key pair for singing Corda Program on Solana
    @TempDir
    private lateinit var notaryKeyDir: Path

    protected val solanaNotaryName = CordaX500Name("Solana Notary", "London", "GB")
    protected val generalNotaryName = CordaX500Name("Notary", "London", "GB")
    private val bridgeAuthority = CordaX500Name("Bridge Authority", "New York", "US")
    private val shareholderName = CordaX500Name("Shareholder", "New York", "US")
    private val otherShareholderName = CordaX500Name("Other Shareholder", "Frankfurt", "DE")

    protected open val solanaRpcUrl = SolanaTestValidator.RPC_URL
    protected open val solanaWssUrl = SolanaTestValidator.WS_URL

    protected val cordappsForAllNodes =
        listOf(
            TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
            TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
            TestCordapp.findCordapp("net.corda.samples.stockpaydividend.states"),
            TestCordapp.findCordapp("net.corda.samples.stockpaydividend.contracts"),
            TestCordapp.findCordapp("net.corda.samples.stockpaydividend.flows").withConfig(
                mapOf("notary" to "O=Notary,L=London,C=GB") // Solana Notary is an additional notary in the Corda network in this sample
                // set preferred notary for flows that don't receive a notary as parameters (e.g. flows in Corda Tokens SDK)
            ),
            TestCordapp.findCordapp("net.corda.samples.solana.bridging.token.dvp.contracts"),
            TestCordapp.findCordapp("net.corda.samples.solana.bridging.token.dvp.states"),
        )
    protected val dvpFlowCordapp = TestCordapp.findCordapp("net.corda.samples.solana.bridging.token.dvp.flows")
    val bridgingContracts = TestCordapp.findCordapp("com.r3.corda.lib.solana.bridging.token.contracts")
    var bridgingWorkflowsWithoutConfig = TestCordapp.findCordapp("com.r3.corda.lib.solana.bridging.token.flows")

    val rpcUsers = listOf(User("user1", "test", permissions = setOf("ALL")))

    // Stockpaydividend doesn't use fractionDigits, in order to maintain 1:1 conversion with Solana token,
    // Solana token will not have fraction digits as well
    protected val TOKEN_DECIMALS: Int = 0

    // A directory for Notary to store Corda participant key pairs for sining Solana transactions,
    // intentionally these are located in a different directory than Corda Notary Program key pair
    @TempDir
    protected lateinit var custodiedKeysDir: Path

    @TempDir
    protected lateinit var otherDir: Path

    protected lateinit var bridgeAuthoritySigner: FileSigner
    protected lateinit var shareholderWallet: FileSigner
    protected lateinit var otherShareholderWallet: FileSigner
    protected lateinit var redemptionWalletForShareholder: FileSigner
    protected lateinit var redemptionWalletForOtherShareholder: FileSigner
    protected lateinit var mintAuthoritySigner: FileSigner
    protected lateinit var tokenMint: PublicKey
    protected lateinit var tokenMint2: PublicKey

    /** Stablecoin mint used for DvP payment leg. Defaults to the bridging token mint.
     *  Override in subclasses to use a different stablecoin (e.g. devnet USDC). */
    protected open val dvpStablecoinMint: String
        get() = tokenMint.toBase58()

    protected lateinit var shareholderNode: NodeHandle
    protected lateinit var otherShareholderNode: NodeHandle
    protected lateinit var bridgeAuthorityNode: NodeHandle

    @BeforeEach
    fun setup() {
        startTestValidator()
        setupAccounts()
    }

    open fun startTestValidator() {
        solanaNotarySigner = FileSigner.random(notaryKeyDir)
        validator = SolanaTestValidator()
        validator.startAndWait()
        validator.defaultNotaryProgramSetup(solanaNotarySigner.publicKey())
        solanaClient = validator.client
        tokenManagement = TokenManagement(validator.client)
        accountManagement = AccountManagement(validator.client)
    }

    open fun setupAccounts() {
        bridgeAuthoritySigner = FileSigner.random(custodiedKeysDir)
        redemptionWalletForShareholder = FileSigner.random(custodiedKeysDir)
        redemptionWalletForOtherShareholder = FileSigner.random(custodiedKeysDir)
        mintAuthoritySigner = FileSigner.random(custodiedKeysDir)
        shareholderWallet = FileSigner.random(otherDir)
        otherShareholderWallet = FileSigner.random(otherDir)

        log.info("\nSolana wallet account addresses:")
        log.info("  Shareholder: ${shareholderWallet.publicKey().toBase58()}")
        log.info("  Other Shareholder: ${otherShareholderWallet.publicKey().toBase58()}")
        log.info("  Bridge Authority: ${bridgeAuthoritySigner.publicKey().toBase58()}")
        log.info("  Mint Authority: ${mintAuthoritySigner.publicKey().toBase58()}")
        log.info("  Redemption on behalf of Shareholder: ${redemptionWalletForShareholder.publicKey().toBase58()}")
        log.info(
            "  Redemption on behalf of Other Shareholder: ${
                redemptionWalletForOtherShareholder.publicKey().toBase58()
            }"
        )

        log.info("  Airdrop for: ${mintAuthoritySigner.publicKey().toBase58()}")
        accountManagement.airdropSol(mintAuthoritySigner.publicKey(), 1)

        tokenMint =
            tokenManagement.createToken(mintAuthoritySigner, TokenProgram.TOKEN_2022, decimals = TOKEN_DECIMALS)
        //tokenMint2 not in use in the test, it's a showcase that can be multiple asset mapping
        tokenMint2 =
            tokenManagement.createToken(mintAuthoritySigner, TokenProgram.TOKEN_2022, decimals = TOKEN_DECIMALS)

        log.info("  Shareholder Token Account: ${shareholderWallet.deriveATA().toBase58()}")
        log.info("  Other Shareholder Token Account: ${otherShareholderWallet.deriveATA().toBase58()}")

        accountManagement.airdropSol(bridgeAuthoritySigner.publicKey(), 1)
        accountManagement.airdropSol(shareholderWallet.publicKey(), 1)
        accountManagement.airdropSol(otherShareholderWallet.publicKey(), 1)
        accountManagement.airdropSol(redemptionWalletForShareholder.publicKey(), 1)
        accountManagement.airdropSol(redemptionWalletForOtherShareholder.publicKey(), 1)

        tokenManagement.createAta(
            mintAuthoritySigner,
            otherShareholderWallet.publicKey(),
            tokenMint,
            Token2022.PROGRAM_ID.toPublicKey()
        )
        tokenManagement.createAta(
            mintAuthoritySigner,
            redemptionWalletForOtherShareholder.publicKey(),
            tokenMint,
            Token2022.PROGRAM_ID.toPublicKey()
        )
        tokenManagement.createAta(
            mintAuthoritySigner,
            redemptionWalletForShareholder.publicKey(),
            tokenMint,
            Token2022.PROGRAM_ID.toPublicKey()
        )
    }

    @AfterEach
    open fun stopTestValidator() {
        validator.close()
    }

    open fun getSolanaNotaryConfig() = mapOf<String, Any>(
        "notary" to mapOf(
            "validating" to false,
            "solana" to mapOf(
                "rpcUrl" to solanaRpcUrl,
                "websocketUrl" to solanaWssUrl,
                "notaryKeypairFile" to "${solanaNotarySigner.file}",
                "custodiedKeysDir" to "$custodiedKeysDir"
            )
        )
    )

    fun TestCordapp.withBridgeAuthorityConfig(
        cordaTokenTypeIdentifier1: String,
        cordaTokenTypeIdentifier2: String
    ): TestCordapp = this.withConfig(
        mapOf(
            "participants" to mapOf(
                "$shareholderName" to shareholderWallet.publicKey().toBase58(),
                "$otherShareholderName" to otherShareholderWallet.publicKey().toBase58(),
            ),
            "redemptionWalletAccountToHolder" to mapOf(
                redemptionWalletForShareholder.publicKey().toBase58() to "$shareholderName",
                redemptionWalletForOtherShareholder.publicKey().toBase58() to "$otherShareholderName",
            ),
            "mintsWithAuthorities" to mapOf(
                cordaTokenTypeIdentifier1 to
                        mapOf(
                            "tokenMint" to tokenMint.toBase58(),
                            "mintAuthority" to mintAuthoritySigner.publicKey().toBase58()
                        ),
                cordaTokenTypeIdentifier2 to
                        mapOf(
                            "tokenMint" to tokenMint2.toBase58(),
                            "mintAuthority" to mintAuthoritySigner.publicKey().toBase58()
                        )
            ),
            "lockingIdentityLabel" to UUID.randomUUID().toString(),
            "solanaNotaryName" to "$solanaNotaryName",
            "generalNotaryName" to "$generalNotaryName",
            "solanaWsUrl" to solanaWssUrl,
            "solanaRpcUrl" to solanaRpcUrl,
            "bridgeAuthorityWalletFile" to bridgeAuthoritySigner.file.toString()
        )
    )

    fun DriverDSL.runtimeSetup() {
        val otherKeysDir = "src/integrationTest/resources/other"

        log.info("\n========== STARTING CORDA NODES ==========")

        log.info("Starting WayneCo node...")
        val wayneCoNode = startNode(
            NodeParameters(
                CordaX500Name("WayneCo", "SF", "US"),
                rpcUsers
            )
        ).getOrThrow()
        log.info("  WayneCo node started.")

        log.info("DvP stablecoin mint: $dvpStablecoinMint")

        log.info("Starting Shareholder node (RPC port 10345)...")
        shareholderNode = startNode(
            NodeParameters(
                providedName = shareholderName,
                rpcUsers = rpcUsers,
                rpcAddress = NetworkHostAndPort("localhost", 10345),
                additionalCordapps = listOf(
                    dvpFlowCordapp.withConfig(mapOf(
                        "stablecoinTokenMint" to dvpStablecoinMint,
                        "solanaWalletFile" to Path.of("$otherKeysDir/shareholderWallet.json").toAbsolutePath().toString(),
                        "solanaRpcUrl" to solanaRpcUrl,
                        "solanaWsUrl" to solanaWssUrl
                    ))
                )
            )
        ).getOrThrow()
        log.info("  Shareholder node started.")

        log.info("Starting OtherShareholder node (RPC port 10349)...")
        otherShareholderNode = startNode(
            NodeParameters(
                providedName = otherShareholderName,
                rpcUsers = rpcUsers,
                rpcAddress = NetworkHostAndPort("localhost", 10349),
                additionalCordapps = listOf(
                    dvpFlowCordapp.withConfig(mapOf(
                        "stablecoinTokenMint" to dvpStablecoinMint,
                        "solanaWalletFile" to Path.of("$otherKeysDir/otherShareholderWallet.json").toAbsolutePath().toString(),
                        "solanaRpcUrl" to solanaRpcUrl,
                        "solanaWsUrl" to solanaWssUrl
                    ))
                )
            )
        ).getOrThrow()
        log.info("  OtherShareholder node started.")

        log.info("Starting Bank node...")
        val bankNode = startNode(
            NodeParameters(
                CordaX500Name("Bank", "Washington DC", "US"),
                rpcUsers
            )
        ).getOrThrow()
        log.info("  Bank node started.")

        log.info("Starting Observer node...")
        startNode(
            NodeParameters(
                CordaX500Name("Observer", "Washington DC", "US"),
                rpcUsers
            )
        ).getOrThrow()
        log.info("  Observer node started.")

        log.info("\n========== ALL NODES STARTED ==========")

        log.info("\n========== ISSUING MONEY & STOCK ==========")

        log.info("Issuing \$500,000 USD to WayneCo...")
        bankNode.rpc.startFlow(
            ::IssueMoney,
            "USD",
            500000L,
            wayneCoNode.nodeInfo.singleIdentity()
        ).returnValue.get()
        log.info("  USD issued to WayneCo.")

        log.info("Creating and issuing 1000 BCRED (Blackstone Private Credit Fund) stock...")
        wayneCoNode.rpc.startFlow(
            ::CreateAndIssueStock,
            "BCRED",
            "Blackstone Private Credit Fund",
            "USD",
            BigDecimal.TEN,
            1000,
            notaryHandles.single { it.identity.name == generalNotaryName }.identity
        ).returnValue.get()
        val appleCordaTokenTypeIdentifier = wayneCoNode.getCordaTokenTypeIdentifier("BCRED")
        log.info("  BCRED created (tokenId: $appleCordaTokenTypeIdentifier)")

        log.info("Creating and issuing 2000 ARCC (Ares Capital Corporation) stock...")
        wayneCoNode.rpc.startFlow(
            ::CreateAndIssueStock,
            "ARCC",
            "Ares Capital Corporation",
            "USD",
            BigDecimal.TEN,
            2000,
            notaryHandles.single { it.identity.name == generalNotaryName }.identity
        ).returnValue.get()
        val msftCordaTokenTypeIdentifier = wayneCoNode.getCordaTokenTypeIdentifier("ARCC")
        log.info("  ARCC created (tokenId: $msftCordaTokenTypeIdentifier)")

        log.info("\n========== STARTING BRIDGE AUTHORITY ==========")

        log.info("Starting Bridge Authority node with bridging config...")
        bridgeAuthorityNode = startNode(
            NodeParameters(
                providedName = bridgeAuthority,
                rpcUsers = rpcUsers,
                additionalCordapps = listOf(
                    bridgingContracts,
                    bridgingWorkflowsWithoutConfig
                        .withBridgeAuthorityConfig(
                            appleCordaTokenTypeIdentifier,
                            msftCordaTokenTypeIdentifier
                        )
                )
            )
        ).getOrThrow()
        log.info("  Bridge Authority node started.")

        log.info("\nCorda BCRED stock ($appleCordaTokenTypeIdentifier) is mapped to Solana tokenMint: ${tokenMint.toBase58()}")
        log.info("Corda ARCC stock ($msftCordaTokenTypeIdentifier) is mapped to Solana tokenMint: ${tokenMint2.toBase58()}")

        log.info("\n========== DISTRIBUTING STOCK ==========")

        log.info("Moving 100 BCRED from WayneCo to Shareholder...")
        wayneCoNode.rpc.startFlow(
            ::MoveStock,
            "BCRED",
            100,
            shareholderNode.nodeInfo.singleIdentity()
        ).returnValue.get()
        log.info("  100 BCRED moved to Shareholder.")

        log.info("Moving 10 ARCC from WayneCo to Shareholder...")
        wayneCoNode.rpc.startFlow(
            ::MoveStock,
            "ARCC",
            10,
            shareholderNode.nodeInfo.singleIdentity()
        ).returnValue.get()
        log.info("  10 ARCC moved to Shareholder.")

        val result = shareholderNode.rpc.startFlow(
            ::GetStockBalance,
            "BCRED"
        ).returnValue.get()!!.trimIndent()
        assertEquals(
            "You currently have 100 BCRED stocks",
            result,
            "Shareholder received stocks on Corda network"
        )

        log.info("\n========== SETUP COMPLETE ==========")
        log.info("\nState before bridging:")
        log.info("  Shareholder Corda balance: ${shareholderNode.cordaBalance()}")
        log.info("  Other ShareholderNode Corda balance: ${otherShareholderNode.cordaBalance()}")
        log.info("  Bridge Authority Corda balance: ${bridgeAuthorityNode.cordaBalance()}")
        log.info("\nSolana state before bridging:")
        log.info("  Shareholder: ${shareholderWallet.solanaBalance()}")
        log.info("  Other ShareholderNode: ${otherShareholderWallet.solanaBalance()}")
    }

    @Test
    open fun `briding token test`() = driver(
        DriverParameters(
            isDebug = false,
            inMemoryDB = false,
            startNodesInProcess = false,
            cordappsForAllNodes = cordappsForAllNodes,
            networkParameters = testNetworkParameters(minimumPlatformVersion = 160).copy(notaries = emptyList()),
            notarySpecs = listOf(
                NotarySpec(generalNotaryName, validating = false, startInProcess = false),
                NotarySpec(solanaNotaryName, getSolanaNotaryConfig(), startInProcess = false)
            ),
            waitForAllNodesToFinish = false
        )
    ) {
        log.info("\nStarting bridging test using local Solana validator...")
        runtimeSetup()
        test()

        log.info("\nBridging test is completed.")
    }

    fun test() {
        shareholderNode.rpc.startFlow(
            ::MoveStock,
            "BCRED",
            90,
            bridgeAuthorityNode.nodeInfo.singleIdentity()
        ).returnValue.get()
        eventually(duration = 10.seconds) {
            assertNotNull(
                solanaClient.getAccountInfo(shareholderWallet.deriveATA()),
                "ATA should be created",
            )
        }
        eventually(duration = 10.seconds) {
            val balance = solanaClient.getSolanaTokenBalance(shareholderWallet.deriveATA())
            val bridgedAmount = BigDecimal(90)
            assertEquals(
                bridgedAmount,
                balance
            ) {
                "Shareholder bridged $bridgedAmount tokens to Solana"
            }
        }
        log.info("\nCorda state after bridging:")
        log.info("  Shareholder: ${shareholderNode.cordaBalance()}")
        log.info("  Other ShareholderNode: ${otherShareholderNode.cordaBalance()}")
        log.info("  Bridge Authority: ${bridgeAuthorityNode.cordaBalance()}")
        log.info("\nSolana state after bridging:")
        log.info("  Shareholder: ${shareholderWallet.solanaBalance()}")
        log.info("  Other ShareholderNode: ${otherShareholderWallet.solanaBalance()}")

        tokenManagement.transfer(
            shareholderWallet,
            shareholderWallet.deriveATA(),
            otherShareholderWallet.deriveATA(),
            50
        )

        log.info("\nSolana state after on-chain transfer of 50 from Shareholder to Other Shareholder:")
        log.info("  Shareholder: ${shareholderWallet.solanaBalance()}")
        log.info("  Other ShareholderNode: ${otherShareholderWallet.solanaBalance()}")

        tokenManagement.transfer(
            otherShareholderWallet,
            otherShareholderWallet.deriveATA(),
            redemptionWalletForOtherShareholder.deriveATA(),
            25
        )

        eventually(duration = 1.seconds) {
            val balance = solanaClient.getSolanaTokenBalance(otherShareholderWallet.deriveATA())
            val bridgedAmount = BigDecimal(25)
            assertEquals(
                bridgedAmount,
                balance
            ) {
                "Other shareholder has sent $bridgedAmount tokens on Solana to redeem on Corda"
            }
        }

        eventually(duration = 20.seconds, waitBefore = 10.seconds, waitBetween = 1.seconds) {
            val result2 = otherShareholderNode.rpc.startFlow(
                ::GetStockBalance,
                "BCRED"
            ).returnValue.get()!!.trimIndent()

            assertEquals(
                "You currently have 25 BCRED stocks",
                result2,
                "Other Shareholder received stocks on Corda that he had redeemed on Solana"
            )
        }
        log.info("\nSolana state before redemptions:")
        log.info("  Shareholder: ${shareholderWallet.solanaBalance()}")
        log.info("  Other ShareholderNode: ${otherShareholderWallet.solanaBalance()}")

        tokenManagement.transfer(
            shareholderWallet,
            shareholderWallet.deriveATA(),
            redemptionWalletForShareholder.deriveATA(),
            20
        )

        eventually(duration = 20.seconds, waitBefore = 10.seconds, waitBetween = 1.seconds) {
            val result2 = shareholderNode.rpc.startFlow(
                ::GetStockBalance,
                "BCRED"
            ).returnValue.get()!!.trimIndent()

            assertEquals(
                "You currently have 30 BCRED stocks",
                result2,
                "Other Shareholder received stocks on Corda that he had redeemed on Solana"
            )
        }
        log.info("\nCorda state after redemptions:")
        log.info("  Shareholder: ${shareholderNode.cordaBalance()}")
        log.info("  Other ShareholderNode: ${otherShareholderNode.cordaBalance()}")
        log.info("  Bridge Authority: ${bridgeAuthorityNode.cordaBalance()}")
        log.info("\nSolana state after redemptions:")
        log.info("  Shareholder: ${shareholderWallet.solanaBalance()}")
        log.info("  Other ShareholderNode: ${otherShareholderWallet.solanaBalance()}")
    }

    /** Driven ATA for Token2022 and token mint for Apple */
    fun PublicKey.deriveATA(): PublicKey {
        val ataProgram = SolanaAccounts.MAIN_NET.associatedTokenAccountProgram()
        val pda = PublicKey.findProgramAddress(
            listOf(
                this.toByteArray(),
                Token2022.PROGRAM_ID.toPublicKey().toByteArray(),
                tokenMint.toByteArray()
            ),
            ataProgram
        )
        return pda.publicKey()
    }

    /** Driven ATA for Token2022 and token mint for Apple */
    fun Signer.deriveATA(): PublicKey = this.publicKey().deriveATA()

    fun NodeHandle.cordaBalance(): String = try {
        this.rpc.startFlow(
            ::GetStockBalance,
            "BCRED"
        ).returnValue.get()!!.trimIndent()
    } catch (_: ExecutionException) {
        "No BCRED shares"
    }

    fun Signer.solanaBalance(): String =
        if (solanaClient.getAccountInfo(this.deriveATA()) != null) {
            "${solanaClient.getSolanaTokenBalance(this.deriveATA())} coins"
        } else {
            "no stablecoin account"
        }

    fun NodeHandle.getCordaTokenTypeIdentifier(symbol: String): String {
        return this.rpc.vaultQuery(StockState::class.java).states.single {
            it.state.data.symbol == symbol
        }.state.data.linearId.toString()
    }

    //TODO move the method to TokenManagement class
    fun TokenManagement.createAta(
        payer: Signer,
        owner: PublicKey,
        mint: PublicKey,
        tokenProgram: PublicKey
    ): PublicKey {
        val solana = SolanaAccounts.MAIN_NET
        val ata = owner.deriveATA()
        val createIdempotentIx = Instruction.createInstruction(
            solana.associatedTokenAccountProgram(),
            listOf(
                AccountMeta.createFeePayer(payer.publicKey()),
                AccountMeta.createWrite(ata),
                AccountMeta.createRead(owner),
                AccountMeta.createRead(mint),
                AccountMeta.createRead(solana.systemProgram()),
                AccountMeta.createRead(tokenProgram)
            ),
            byteArrayOf(1)
        )
        /*validator.client*/solanaClient.sendAndConfirm(
            {
                it.createTransaction(listOf(createIdempotentIx))
            },
            payer,
            listOf()
        )
        return ata
    }
}

fun SolanaClient.getAccountInfo(tokenAccount: PublicKey): Token2022Account? {
    val raw = this.call(SolanaRpcClient::getAccountInfo, tokenAccount)
    return if (raw?.data != null) Token2022Account.read(raw.pubKey, raw.data) else null
}

fun SolanaClient.getSolanaTokenBalance(tokenAccount: PublicKey): BigDecimal =
    this.call(SolanaRpcClient::getTokenAccountBalance, tokenAccount).amount.toBigDecimal()

fun Pubkey.toPublicKey(): PublicKey = PublicKey.createPubKey(bytes)