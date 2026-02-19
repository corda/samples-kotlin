package net.corda.samples.solana.bridge.token

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
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ExecutionException
import com.r3.corda.lib.solana.testing.SolanaTestValidator
import net.corda.solana.notary.testing.Notary
import net.corda.solana.notary.testing.SolanaNotaryExtension
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(SolanaNotaryExtension::class)
open class BridgingTokenDriverTest {

    protected val log = LoggerFactory.getLogger(BridgingTokenDriverTest::class.java)
    private lateinit var validator: SolanaTestValidator
    protected lateinit var tokenManagement: TokenManagement
    protected lateinit var accountManagement: AccountManagement
    protected lateinit var solanaClient: SolanaClient

    protected val solanaNotaryName = CordaX500Name("Solana Notary", "London", "GB")
    protected val generalNotaryName = CordaX500Name("Notary", "London", "GB")
    private val bridgeAuthority = CordaX500Name("Bridge Authority", "New York", "US")
    private val shareholderName = CordaX500Name("Shareholder", "New York", "US")
    private val otherShareholderName = CordaX500Name("Other Shareholder", "Frankfurt", "DE")

    protected val cordappsForAllNodes =
        listOf(
            TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
            TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
            TestCordapp.findCordapp("net.corda.samples.stockpaydividend.states"),
            TestCordapp.findCordapp("net.corda.samples.stockpaydividend.contracts"),
            TestCordapp.findCordapp("net.corda.samples.stockpaydividend.flows").withConfig(
                mapOf("notary" to "O=Notary,L=London,C=GB") // Solana Notary is an additional notary in the Corda network in this sample
                // set preferred notary for flows that don't receive a notary as parameters (e.g. flows in Corda Tokens SDK)
            )
        )
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

    protected lateinit var shareholderNode: NodeHandle
    protected lateinit var otherShareholderNode: NodeHandle
    protected lateinit var bridgeAuthorityNode: NodeHandle

    @BeforeEach
    fun setup(validator: SolanaTestValidator) {
        this.validator = validator
        solanaClient = validator.client()
        tokenManagement = validator.tokens()
        accountManagement = validator.accounts()
        setupAccounts()
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

        tokenManagement.createAssociatedTokenAccount(
            mintAuthoritySigner,
            tokenMint,
            otherShareholderWallet.publicKey(),
        )
        tokenManagement.createAssociatedTokenAccount(
            mintAuthoritySigner,
            tokenMint,
            redemptionWalletForOtherShareholder.publicKey(),
        )
        tokenManagement.createAssociatedTokenAccount(
            mintAuthoritySigner,
            tokenMint,
            redemptionWalletForShareholder.publicKey(),
        )
    }

    open fun getSolanaNotaryConfig(solanaNotarySigner: FileSigner) = mapOf<String, Any>(
        "notary" to mapOf(
            "validating" to false,
            "solana" to mapOf(
                "rpcUrl" to  "${validator.rpcUrl()}",
                "websocketUrl" to "${validator.websocketUrl()}",
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
            "solanaRpcUrl" to "${validator.rpcUrl()}",
            "solanaWsUrl" to "${validator.websocketUrl()}",
            "bridgeAuthorityWalletFile" to bridgeAuthoritySigner.file.toString()
        )
    )

    fun DriverDSL.runtimeSetup() {
        val wayneCoNode = startNode(
            NodeParameters(
                CordaX500Name("WayneCo", "SF", "US"),
                rpcUsers
            )
        ).getOrThrow()

        shareholderNode = startNode(
            NodeParameters(
                shareholderName,
                rpcUsers,
                rpcAddress = NetworkHostAndPort("localhost", 10345)
            )
        ).getOrThrow()

        otherShareholderNode = startNode(
            NodeParameters(
                otherShareholderName,
                rpcUsers,
                rpcAddress = NetworkHostAndPort("localhost", 10349)
            )
        ).getOrThrow()

        val bankNode = startNode(
            NodeParameters(
                CordaX500Name("Bank", "Washington DC", "US"),
                rpcUsers
            )
        ).getOrThrow()

        startNode(
            NodeParameters(
                CordaX500Name("Observer", "Washington DC", "US"),
                rpcUsers
            )
        ).getOrThrow()

        bankNode.rpc.startFlow(
            ::IssueMoney,
            "USD",
            500000L,
            wayneCoNode.nodeInfo.singleIdentity()
        ).returnValue.get()

        wayneCoNode.rpc.startFlow(
            ::CreateAndIssueStock,
            "AAPL",
            "Apple",
            "USD",
            BigDecimal.TEN,
            1000,
            notaryHandles.single { it.identity.name == generalNotaryName }.identity
        ).returnValue.get()
        val appleCordaTokenTypeIdentifier = wayneCoNode.getCordaTokenTypeIdentifier("AAPL")

        wayneCoNode.rpc.startFlow(
            ::CreateAndIssueStock,
            "MSFT",
            "Microsoft",
            "USD",
            BigDecimal.TEN,
            2000,
            notaryHandles.single { it.identity.name == generalNotaryName }.identity
        ).returnValue.get()
        val msftCordaTokenTypeIdentifier = wayneCoNode.getCordaTokenTypeIdentifier("MSFT")

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

        log.info("\nCorda APPL stock ($appleCordaTokenTypeIdentifier) is mapped to Solana tokenMint: ${tokenMint.toBase58()}")
        log.info("\nCorda MSFT stock ($msftCordaTokenTypeIdentifier) is mapped to Solana tokenMint: ${tokenMint2.toBase58()}")

        wayneCoNode.rpc.startFlow(
            ::MoveStock,
            "AAPL",
            100,
            shareholderNode.nodeInfo.singleIdentity()
        ).returnValue.get()

        wayneCoNode.rpc.startFlow(
            ::MoveStock,
            "MSFT",
            10,
            shareholderNode.nodeInfo.singleIdentity()
        ).returnValue.get()

        val result = shareholderNode.rpc.startFlow(
            ::GetStockBalance,
            "AAPL"
        ).returnValue.get()!!.trimIndent()
        assertEquals(
            "You currently have 100 AAPL stocks",
            result,
            "Shareholder received stocks on Corda network"
        )
        log.info("\nState before bridging:")
        log.info("  Shareholder Corda balance: ${shareholderNode.cordaBalance()}")
        log.info("  Other ShareholderNode Corda balance: ${otherShareholderNode.cordaBalance()}")
        log.info("  Bridge Authority Corda balance: ${bridgeAuthorityNode.cordaBalance()}")
        log.info("\nSolana state before bridging:")
        log.info("  Shareholder: ${shareholderWallet.solanaBalance()}")
        log.info("  Other ShareholderNode: ${otherShareholderWallet.solanaBalance()}")
    }

    @Test
    open fun `briding token test`(@Notary notaryKey: FileSigner) = driver(
        DriverParameters(
            isDebug = false,
            inMemoryDB = false,
            startNodesInProcess = false,
            cordappsForAllNodes = cordappsForAllNodes,
            networkParameters = testNetworkParameters(minimumPlatformVersion = 160).copy(notaries = emptyList()),
            notarySpecs = listOf(
                NotarySpec(generalNotaryName, validating = false, startInProcess = false),
                NotarySpec(solanaNotaryName, getSolanaNotaryConfig(notaryKey), startInProcess = false)
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
            "AAPL",
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
                "AAPL"
            ).returnValue.get()!!.trimIndent()

            assertEquals(
                "You currently have 25 AAPL stocks",
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
                "AAPL"
            ).returnValue.get()!!.trimIndent()

            assertEquals(
                "You currently have 30 AAPL stocks",
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
            "AAPL"
        ).returnValue.get()!!.trimIndent()
    } catch (_: ExecutionException) {
        "No AAPL shares"
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
}

fun SolanaClient.getAccountInfo(tokenAccount: PublicKey): Token2022Account? {
    val raw = this.call(SolanaRpcClient::getAccountInfo, tokenAccount)
    return if (raw?.data != null) Token2022Account.read(raw.pubKey, raw.data) else null
}

fun SolanaClient.getSolanaTokenBalance(tokenAccount: PublicKey): BigDecimal =
    this.call(SolanaRpcClient::getTokenAccountBalance, tokenAccount).amount.toBigDecimal()

fun Pubkey.toPublicKey(): PublicKey = PublicKey.createPubKey(bytes)