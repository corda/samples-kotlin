package net.corda.samples.solana.bridging.token

import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import net.corda.core.solana.Pubkey
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import com.r3.corda.lib.solana.core.AccountManagement
import com.r3.corda.lib.solana.core.FileSigner
import com.r3.corda.lib.solana.core.SolanaClient
import com.r3.corda.lib.solana.core.cordautils.Token2022
import com.r3.corda.lib.solana.core.tokens.TokenManagement
import com.r3.corda.lib.solana.core.tokens.TokenProgram
import net.corda.samples.stockpaydividend.flows.CreateAndIssueStock
import net.corda.samples.stockpaydividend.flows.GetStockBalance
import net.corda.samples.stockpaydividend.flows.IssueMoney
import net.corda.samples.stockpaydividend.flows.MoveStock
import net.corda.samples.stockpaydividend.states.StockState
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
import software.sava.core.accounts.token.Token2022Account
import software.sava.rpc.json.http.client.SolanaRpcClient
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.file.Path
import java.util.concurrent.ExecutionException

open class BridgingTokenDriverTest {

    protected val log = LoggerFactory.getLogger(BridgingTokenDriverTest::class.java)
    protected lateinit var tokenManagement: TokenManagement
    protected lateinit var accountManagement: AccountManagement
    protected lateinit var solanaNotarySigner: FileSigner
    protected lateinit var solanaClient: SolanaClient

    protected val solanaNotaryName = CordaX500Name("Solana Notary", "London", "GB")
    protected val generalNotaryName = CordaX500Name("Notary", "London", "GB")
    private val bridgeAuthority = CordaX500Name("Bridge Authority", "New York", "US")
    private val issuerName = CordaX500Name("Issuer", "New York", "US")
    private val custodianName = CordaX500Name("Custodian", "Frankfurt", "DE")

    // Default to local Solana test validator URLs; override in subclasses for other networks
    protected open val solanaRpcUrl = "http://localhost:8899"
    protected open val solanaWssUrl = "ws://localhost:8900"

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

    // Stockpaydividend Cordapp doesn't use fraction digits however Solana token mint has 2 fraction digits,
    // bridging handles the conversion between Corda and Solana token amounts automatically.
    protected val TOKEN_DECIMALS: Int = 2

    // A directory for Notary to store Corda participant key pairs for sining Solana transactions,
    // intentionally these are located in a different directory than Corda Notary Program key pair
    @TempDir
    protected lateinit var custodiedKeysDir: Path

    @TempDir
    protected lateinit var otherDir: Path

    protected lateinit var bridgeAuthoritySigner: FileSigner
    protected lateinit var issuerWallet: FileSigner
    protected lateinit var custodianWallet: FileSigner
    protected lateinit var investorWallet: FileSigner
    protected lateinit var redemptionWalletForIssuer: FileSigner
    protected lateinit var redemptionWalletForCustodian: FileSigner
    protected lateinit var mintAuthoritySigner: FileSigner
    protected lateinit var tokenMint: PublicKey
    protected lateinit var tokenMint2: PublicKey

    /** Stablecoin mint used for DvP payment leg. Defaults to the bridging token mint.
     *  Override in subclasses to use a different stablecoin (e.g. devnet USDC). */
    protected open val dvpStablecoinMint: String
        get() = tokenMint.toBase58()

    protected lateinit var issuerNode: NodeHandle
    protected lateinit var custodianNode: NodeHandle
    protected lateinit var bridgeAuthorityNode: NodeHandle

    @BeforeEach
    fun setup() {
        startTestValidator()
        setupAccounts()
    }

    open fun startTestValidator() {
        // Override in subclass to start the appropriate Solana validator / connect to a network.
        // For local validator tests use SolanaNotaryExtension (see fraction-decimals-2 branch).
    }

    open fun setupAccounts() {
        bridgeAuthoritySigner = FileSigner.random(custodiedKeysDir)
        redemptionWalletForIssuer = FileSigner.random(custodiedKeysDir)
        redemptionWalletForCustodian = FileSigner.random(custodiedKeysDir)
        mintAuthoritySigner = FileSigner.random(custodiedKeysDir)
        issuerWallet = FileSigner.random(custodiedKeysDir)
        custodianWallet = FileSigner.random(custodiedKeysDir)
        investorWallet = FileSigner.random(custodiedKeysDir)

        log.info("\nSolana wallet account addresses:")
        log.info("  Issuer: ${issuerWallet.publicKey().toBase58()}")
        log.info("  Custodian: ${custodianWallet.publicKey().toBase58()}")
        log.info("  Bridge Authority: ${bridgeAuthoritySigner.publicKey().toBase58()}")
        log.info("  Mint Authority: ${mintAuthoritySigner.publicKey().toBase58()}")
        log.info("  Redemption on behalf of Issuer: ${redemptionWalletForIssuer.publicKey().toBase58()}")
        log.info(
            "  Redemption on behalf of Custodian: ${
                redemptionWalletForCustodian.publicKey().toBase58()
            }"
        )

        log.info("  Airdrop for: ${mintAuthoritySigner.publicKey().toBase58()}")
        accountManagement.airdropSol(mintAuthoritySigner.publicKey(), 1)

        tokenMint =
            tokenManagement.createToken(mintAuthoritySigner, TokenProgram.TOKEN_2022, decimals = TOKEN_DECIMALS)
        //tokenMint2 not in use in the test, it's a showcase that can be multiple asset mapping
        tokenMint2 =
            tokenManagement.createToken(mintAuthoritySigner, TokenProgram.TOKEN_2022, decimals = TOKEN_DECIMALS)

        log.info("  Issuer Token Account: ${issuerWallet.deriveATA().toBase58()}")
        log.info("  Custodian Token Account: ${custodianWallet.deriveATA().toBase58()}")

        accountManagement.airdropSol(bridgeAuthoritySigner.publicKey(), 1)
        accountManagement.airdropSol(issuerWallet.publicKey(), 1)
        accountManagement.airdropSol(custodianWallet.publicKey(), 1)
        accountManagement.airdropSol(redemptionWalletForIssuer.publicKey(), 1)
        accountManagement.airdropSol(redemptionWalletForCustodian.publicKey(), 1)

        tokenManagement.createAssociatedTokenAccount(
            mintAuthoritySigner,
            tokenMint,
            custodianWallet.publicKey()
        )
        tokenManagement.createAssociatedTokenAccount(
            mintAuthoritySigner,
            tokenMint,
            redemptionWalletForCustodian.publicKey()
        )
        tokenManagement.createAssociatedTokenAccount(
            mintAuthoritySigner,
            tokenMint,
            redemptionWalletForIssuer.publicKey()
        )
    }

    @AfterEach
    open fun stopTestValidator() {
        // Override in subclass to stop/disconnect from the validator.
    }

    open fun getSolanaNotaryConfig(): Map<String, Any> = mapOf(
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
                "$issuerName" to issuerWallet.publicKey().toBase58(),
                "$custodianName" to custodianWallet.publicKey().toBase58(),
            ),
            "redemptionWalletAccountToHolder" to mapOf(
                redemptionWalletForIssuer.publicKey().toBase58() to "$issuerName",
                redemptionWalletForCustodian.publicKey().toBase58() to "$custodianName",
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
            "solanaNotaryName" to "$solanaNotaryName",
            "generalNotaryName" to "$generalNotaryName",
            "solanaRpcUrl" to solanaRpcUrl,
            "solanaWebsocketUrl" to solanaWssUrl,
            "bridgeAuthorityWalletFile" to bridgeAuthoritySigner.file.toString()
        )
    )

    fun DriverDSL.runtimeSetup() {
        val otherKeysDir = "src/integrationTest/resources/custodiedKeys"

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

        log.info("Starting Issuer node (RPC port 10345)...")
        issuerNode = startNode(
            NodeParameters(
                providedName = issuerName,
                rpcUsers = rpcUsers,
                rpcAddress = NetworkHostAndPort("localhost", 10345),
                additionalCordapps = listOf(
                    dvpFlowCordapp.withConfig(mapOf(
                        "stablecoinTokenMint" to dvpStablecoinMint,
                        "solanaWalletFile" to Path.of("$otherKeysDir/issuerWallet.json").toAbsolutePath().toString(),
                        "solanaRpcUrl" to solanaRpcUrl,
                        "solanaWsUrl" to solanaWssUrl
                    ))
                )
            )
        ).getOrThrow()
        log.info("  Issuer node started.")

        log.info("Starting Custodian node (RPC port 10349)...")
        custodianNode = startNode(
            NodeParameters(
                providedName = custodianName,
                rpcUsers = rpcUsers,
                rpcAddress = NetworkHostAndPort("localhost", 10349),
                additionalCordapps = listOf(
                    dvpFlowCordapp.withConfig(mapOf(
                        "stablecoinTokenMint" to dvpStablecoinMint,
                        "solanaWalletFile" to Path.of("$otherKeysDir/custodianWallet.json").toAbsolutePath().toString(),
                        "solanaRpcUrl" to solanaRpcUrl,
                        "solanaWsUrl" to solanaWssUrl
                    ))
                )
            )
        ).getOrThrow()
        log.info("  Custodian node started.")

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

        log.info("Moving 100 BCRED from WayneCo to Issuer...")
        wayneCoNode.rpc.startFlow(
            ::MoveStock,
            "BCRED",
            100,
            issuerNode.nodeInfo.singleIdentity()
        ).returnValue.get()
        log.info("  100 BCRED moved to Issuer.")

        log.info("Moving 10 ARCC from WayneCo to Issuer...")
        wayneCoNode.rpc.startFlow(
            ::MoveStock,
            "ARCC",
            10,
            issuerNode.nodeInfo.singleIdentity()
        ).returnValue.get()
        log.info("  10 ARCC moved to Issuer.")

        val result = issuerNode.rpc.startFlow(
            ::GetStockBalance,
            "BCRED"
        ).returnValue.get()!!.trimIndent()
        assertEquals(
            "You currently have 100 BCRED stocks",
            result,
            "Issuer received stocks on Corda network"
        )

        log.info("\n========== SETUP COMPLETE ==========")
        log.info("\nState before bridging:")
        log.info("  Issuer Corda balance: ${issuerNode.cordaBalance()}")
        log.info("  Custodian Corda balance: ${custodianNode.cordaBalance()}")
        log.info("  Bridge Authority Corda balance: ${bridgeAuthorityNode.cordaBalance()}")
        log.info("\nSolana state before bridging:")
        log.info("  Issuer: ${issuerWallet.solanaBalance()}")
        log.info("  Custodian: ${custodianWallet.solanaBalance()}")
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
        issuerNode.rpc.startFlow(
            ::MoveStock,
            "BCRED",
            90,
            bridgeAuthorityNode.nodeInfo.singleIdentity()
        ).returnValue.get()
        eventually(duration = 10.seconds) {
            assertNotNull(
                solanaClient.getAccountInfo(issuerWallet.deriveATA()),
                "ATA should be created",
            )
        }
        eventually(duration = 10.seconds) {
            val balance = solanaClient.getSolanaTokenUiBalance(issuerWallet.deriveATA())
            val bridgedAmount = "90"
            assertEquals(
                bridgedAmount,
                balance
            ) {
                "Issuer bridged $bridgedAmount tokens to Solana"
            }
        }
        log.info("\nCorda state after bridging:")
        log.info("  Issuer: ${issuerNode.cordaBalance()}")
        log.info("  Custodian: ${custodianNode.cordaBalance()}")
        log.info("  Bridge Authority: ${bridgeAuthorityNode.cordaBalance()}")
        log.info("\nSolana state after bridging:")
        log.info("  Issuer: ${issuerWallet.solanaBalance()}")
        log.info("  Custodian: ${custodianWallet.solanaBalance()}")

        tokenManagement.transfer(
            issuerWallet,
            issuerWallet.deriveATA(),
            custodianWallet.deriveATA(),
            50 * 100
        )

        log.info("\nSolana state after on-chain transfer of 50 from Issuer to Custodian:")
        log.info("  Issuer: ${issuerWallet.solanaBalance()}")
        log.info("  Custodian: ${custodianWallet.solanaBalance()}")

        tokenManagement.transfer(
            custodianWallet,
            custodianWallet.deriveATA(),
            redemptionWalletForCustodian.deriveATA(),
            25 * 100
        )

        eventually(duration = 1.seconds) {
            val balance = solanaClient.getSolanaTokenUiBalance(custodianWallet.deriveATA())
            val bridgedAmount = "25"
            assertEquals(
                bridgedAmount,
                balance
            ) {
                "Custodian has sent $bridgedAmount tokens on Solana to redeem on Corda"
            }
        }

        eventually(duration = 20.seconds, waitBefore = 10.seconds, waitBetween = 1.seconds) {
            val result2 = custodianNode.rpc.startFlow(
                ::GetStockBalance,
                "BCRED"
            ).returnValue.get()!!.trimIndent()

            assertEquals(
                "You currently have 25 BCRED stocks",
                result2,
                "Custodian received stocks on Corda that they had redeemed on Solana"
            )
        }
        log.info("\nSolana state before redemptions:")
        log.info("  Issuer: ${issuerWallet.solanaBalance()}")
        log.info("  Custodian: ${custodianWallet.solanaBalance()}")

        tokenManagement.transfer(
            issuerWallet,
            issuerWallet.deriveATA(),
            redemptionWalletForIssuer.deriveATA(),
            20 * 100
        )

        eventually(duration = 20.seconds, waitBefore = 10.seconds, waitBetween = 1.seconds) {
            val result2 = issuerNode.rpc.startFlow(
                ::GetStockBalance,
                "BCRED"
            ).returnValue.get()!!.trimIndent()

            assertEquals(
                "You currently have 30 BCRED stocks",
                result2,
                "Custodian received stocks on Corda that they had redeemed on Solana"
            )
        }
        log.info("\nCorda state after redemptions:")
        log.info("  Issuer: ${issuerNode.cordaBalance()}")
        log.info("  Custodian: ${custodianNode.cordaBalance()}")
        log.info("  Bridge Authority: ${bridgeAuthorityNode.cordaBalance()}")
        log.info("\nSolana state after redemptions:")
        log.info("  Issuer: ${issuerWallet.solanaBalance()}")
        log.info("  Custodian: ${custodianWallet.solanaBalance()}")
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
            "${solanaClient.getSolanaTokenUiBalance(this.deriveATA())} coins"
        } else {
            "no stablecoin account"
        }

    fun NodeHandle.getCordaTokenTypeIdentifier(symbol: String): String {
        return this.rpc.vaultQuery(StockState::class.java).states.single {
            it.state.data.symbol == symbol
        }.state.data.linearId.toString()
    }

}

fun SolanaClient.getAccountInfo(tokenAccount: PublicKey): Token2022Account? {
    val raw = this.call(SolanaRpcClient::getAccountInfo, tokenAccount)
    return if (raw?.data != null) Token2022Account.read(raw.pubKey, raw.data) else null
}

fun SolanaClient.getSolanaTokenBalance(tokenAccount: PublicKey): BigInteger =
    this.call(SolanaRpcClient::getTokenAccountBalance, tokenAccount).amount

fun SolanaClient.getSolanaTokenUiBalance(tokenAccount: PublicKey): String =
    this.call(SolanaRpcClient::getTokenAccountBalance, tokenAccount).toDecimal().toPlainString()

fun Pubkey.toPublicKey(): PublicKey = PublicKey.createPubKey(bytes)