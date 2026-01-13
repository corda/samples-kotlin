package net.corda.samples.solana.bridging.token

import com.lmax.solana4j.Solana
import com.lmax.solana4j.api.PublicKey
import com.lmax.solana4j.client.api.AccountInfo
import com.lmax.solana4j.client.jsonrpc.SolanaJsonRpcClient
import com.lmax.solana4j.programs.AssociatedTokenProgram
import com.lmax.solana4j.programs.Token2022Program
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.samples.stockpaydividend.flows.CreateAndIssueStock
import net.corda.samples.stockpaydividend.flows.GetStockBalance
import net.corda.samples.stockpaydividend.flows.IssueMoney
import net.corda.samples.stockpaydividend.flows.MoveStock
import net.corda.solana.notary.common.Signer
import net.corda.solana.notary.common.rpc.DefaultRpcParams
import net.corda.solana.notary.common.rpc.checkResponse
import net.corda.solana.notary.common.rpc.sendAndConfirm
import net.corda.solana.sdk.Token2022
import net.corda.solana.sdk.instruction.Pubkey
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
import net.corda.testing.solana.randomKeypairFile
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.net.http.HttpClient
import java.nio.file.Path
import java.util.UUID


class BridgingTokenDriverTest {

    companion object {
        private val log = LoggerFactory.getLogger(BridgingTokenDriverTest::class.java)

        private val validator = SolanaTestValidator()
        private lateinit var solanaNotaryKeyFile: Path
        private lateinit var solanaNotaryKey: Signer

        // A directory with Corda Notary key pair for singing Corda Program on Solana
        @TempDir
        private lateinit var notaryKeyDir: Path

        @JvmStatic
        @BeforeAll
        fun startTestValidator() {
            solanaNotaryKeyFile = randomKeypairFile(notaryKeyDir)
            solanaNotaryKey = Signer.fromFile(solanaNotaryKeyFile)
            validator.start()
            validator.defaultNotaryProgramSetup(solanaNotaryKey.account)
        }

        @JvmStatic
        @AfterAll
        fun stopTestValidator() {
            validator.close()
        }
    }

    private val solanaNotaryName = CordaX500Name("Solana Notary", "London", "GB")
    private val generalNotaryName = CordaX500Name("Notary", "London", "GB")
    private val bridgeAuthority = CordaX500Name("Bridge Authority", "New York", "US")
    private val shareholderName = CordaX500Name("Shareholder", "New York", "US")
    private val otherShareholderName = CordaX500Name("Other Shareholder", "Frankfurt", "DE")
    private lateinit var solanaNotaryConfig: Map<String, Any>

    private val cordappsForAllNodes =
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
    private val TOKEN_DECIMALS = 0

    // A directory for Notary to store Corda participant key pairs for sining Solana transactions,
    // intentionally these are located in a different directory than Corda Notary Program key pair
    @TempDir
    private lateinit var custodiedKeysDir: Path

    private lateinit var bridgeAuthorityWalletFile: Path
    private lateinit var bridgeAuthorityWallet: Signer

    private val shareholderWallet: Signer = Signer.random()
    private val otherShareholderWallet: Signer = Signer.random()

    private lateinit var redemptionWalletForShareholder: Signer
    private lateinit var redemptionWalletForOtherShareholder: Signer
    private lateinit var mintAuthoritySigner: Signer
    private lateinit var tokenMint: PublicKey

    @BeforeEach
    fun setup() {
        solanaNotaryConfig = mapOf<String, Any>(
            "notary" to mapOf(
                "validating" to false,
                "solana" to mapOf(
                    "rpcUrl" to SolanaTestValidator.RPC_URL,
                    "websocketUrl" to SolanaTestValidator.WS_URL,
                    "notaryKeypairFile" to "$solanaNotaryKeyFile",
                    "custodiedKeysDir" to "$custodiedKeysDir",
                    "programWhitelist" to listOf(Token2022.PROGRAM_ID.toPublicKey().base58())
                )
            )
        )
        bridgeAuthorityWalletFile = randomKeypairFile(custodiedKeysDir)
        bridgeAuthorityWallet = Signer.fromFile(bridgeAuthorityWalletFile)
        redemptionWalletForShareholder = Signer.fromFile(randomKeypairFile(custodiedKeysDir))
        redemptionWalletForOtherShareholder = Signer.fromFile(randomKeypairFile(custodiedKeysDir))

        mintAuthoritySigner = Signer.fromFile(randomKeypairFile(custodiedKeysDir))
        validator.fundAccount(10, mintAuthoritySigner)

        tokenMint = validator.createToken(mintAuthoritySigner, decimals = TOKEN_DECIMALS.toByte())

        validator.fundAccount(10, bridgeAuthorityWallet)
        validator.fundAccount(10, shareholderWallet)
        validator.fundAccount(10, otherShareholderWallet)
        validator.fundAccount(10, redemptionWalletForShareholder)
        validator.fundAccount(10, redemptionWalletForOtherShareholder)

        validator.createAta(mintAuthoritySigner, tokenMint, otherShareholderWallet.account)
        validator.createAta(mintAuthoritySigner, tokenMint, redemptionWalletForOtherShareholder.account)

        validator.createAta(mintAuthoritySigner, tokenMint, redemptionWalletForShareholder.account)
    }

    fun TestCordapp.withBridgeAuthorityConfig(cordaTokenTypeIdentifier: String): TestCordapp = this.withConfig(
        mapOf(
            "participants" to mapOf(
                "$shareholderName" to shareholderWallet.account.base58(),
                "$otherShareholderName" to otherShareholderWallet.account.base58(),
            ),
            "redemptionWalletAccountToHolder" to mapOf(
                redemptionWalletForShareholder.account.base58() to "$shareholderName",
                redemptionWalletForOtherShareholder.account.base58() to "$otherShareholderName",
            ),
            "mintsWithAuthorities" to mapOf(
                cordaTokenTypeIdentifier to
                        mapOf(
                            "tokenMint" to tokenMint.base58(),
                            "mintAuthority" to mintAuthoritySigner.account.base58()
                        )
            ),
            "lockingIdentityLabel" to UUID.randomUUID().toString(),
            "solanaNotaryName" to "$solanaNotaryName",
            "generalNotaryName" to "$generalNotaryName",
            "solanaWsUrl" to SolanaTestValidator.WS_URL,
            "solanaRpcUrl" to SolanaTestValidator.RPC_URL,
            "bridgeAuthorityWalletFile" to bridgeAuthorityWalletFile.toString()
        )
    )

    @Test
    fun `briding token test`() = withDriver {
        log.info("Starting bridging demo ...")

        val wayneCoNode = startNode(
            NodeParameters(
                CordaX500Name("WayneCo", "SF", "US"),
                rpcUsers
            )
        ).getOrThrow()

        val shareholderNode = startNode(
            NodeParameters(
                shareholderName,
                rpcUsers
            )
        ).getOrThrow()

        val otherShareholderNode = startNode(
            NodeParameters(
                otherShareholderName,
                rpcUsers
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

        val bridgingAuthorityNode = startNode(
            NodeParameters(
                providedName = bridgeAuthority,
                rpcUsers = rpcUsers,
                additionalCordapps = listOf(
                    bridgingContracts,
                    bridgingWorkflowsWithoutConfig
                        .withBridgeAuthorityConfig(wayneCoNode.getCordaTokenTypeIdentifier())
                )
            )
        ).getOrThrow()

        wayneCoNode.rpc.startFlow(
            ::MoveStock,
            "AAPL",
            100,
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
        // TODO show Corda balance of Stockholder, OtherStockholder, and confidential identity / BA

        assertNull(
            validator.getAccountInfo(shareholderWallet.deriveATA()),
            "ATA should not be created yet",
        )

        shareholderNode.rpc.startFlow(
            ::MoveStock,
            "AAPL",
            90,
            bridgingAuthorityNode.nodeInfo.singleIdentity()
        ).returnValue.get()
        eventually(duration = 10.seconds) {
            assertNotNull(
                validator.getAccountInfo(shareholderWallet.deriveATA()),
                "ATA should be created",
            )
        }
        eventually(duration = 10.seconds) {
            val balance = validator.getSolanaTokenBalance(shareholderWallet.deriveATA())
            val bridgedAmount = BigDecimal(90)
            assertEquals(
                BigDecimal(90),
                balance
            ) {
                "Shareholder bridged $bridgedAmount tokens to Solana"
            }
        }
        // TODO show Corda balance of Stockholder, OtherStockholder and confidential identity / BA
        // TODO show Solana balance of Stockholder, OtherStockholder

        validator.transfer(
            shareholderWallet,
            shareholderWallet.deriveATA(),
            otherShareholderWallet.deriveATA(),
            50
        )

        validator.transfer(
            otherShareholderWallet,
            otherShareholderWallet.deriveATA(),
            redemptionWalletForOtherShareholder.deriveATA(),
            25
        )

        eventually(duration = 1.seconds) {
            val balance = validator.getSolanaTokenBalance(otherShareholderWallet.deriveATA())
            val bridgedAmount = BigDecimal(25)
            assertEquals(
                BigDecimal(25),
                balance
            ) {
                "Other shareholder has send $bridgedAmount tokens on Solana to redeem on Corda"
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
        // TODO show Corda balance of OtherStockholder, and confidential identity / BA
        // TODO show Solana balance of OtherStockholder

        validator.transfer(
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
        // TODO show Corda balance of Stockholder, and confidential identity / BA
        // TODO show Solana balance of Stockholder
        log.info("Bridging demo is completed.")
    }

    // Runs a test inside the Driver DSL
    private fun withDriver(test: DriverDSL.() -> Unit) = driver(
        DriverParameters(
            isDebug = false,
            inMemoryDB = false,
            startNodesInProcess = false,
            cordappsForAllNodes = cordappsForAllNodes,
            networkParameters = testNetworkParameters(minimumPlatformVersion = 160).copy(notaries = emptyList()),
            notarySpecs = listOf(
                NotarySpec(generalNotaryName, validating = false, startInProcess = false),
                NotarySpec(solanaNotaryName, solanaNotaryConfig, startInProcess = false)
            )
        )
    ) { test() }

    fun Signer.deriveATA(): PublicKey = AssociatedTokenProgram
        .deriveAddress(
            this.account,
            Token2022.PROGRAM_ID.toPublicKey(),
            tokenMint
        ).address()

    fun NodeHandle.getSharesNumber(): Long {
        val wayneCoStocks = this.rpc.vaultQuery(FungibleToken::class.java).states
        return wayneCoStocks
            // Simplified as Corda network has a one asset type, we don't need to check Corda state details (issuer and token pointer)
            .sumOf { it.state.data.amount.quantity }
    }

    fun NodeHandle.getCordaTokenTypeIdentifier(): String {
        val states = this.rpc.vaultQuery(FungibleToken::class.java).states
        return states
            // Simplified as Corda network has a one asset type, we don't need to check Corda state details (issuer and token pointer)
            .first { it.state.data.amount.token.tokenIdentifier != "USD" }.state.data.amount.token.tokenIdentifier
    }
}

fun Pubkey.toPublicKey(): PublicKey = Solana.account(bytes)


fun SolanaTestValidator.getAccountInfo(publicKey: PublicKey?): AccountInfo? {
    requireNotNull(publicKey) { "PublicKey must not be null" }
    return client
        .getAccountInfo(publicKey.base58(), this.rpcParams)
        .checkResponse("getAccountInfo")
}

fun SolanaTestValidator.getSolanaTokenBalance(publicKey: PublicKey): BigDecimal {
    return client
        .getTokenAccountBalance(publicKey.base58(), this.rpcParams)
        .checkResponse("getTokenAccountBalance")!!
        .uiAmountString
        .toBigDecimal()
}

fun SolanaTestValidator.transfer(
    fromOwner: Signer,
    fromTokenAccount: PublicKey,
    toTokenAccount: PublicKey,
    amount: Long,
) {
    val error = client
        .sendAndConfirm(
            { txBuilder ->
                Token2022Program.factory(txBuilder).transfer(
                    fromTokenAccount,
                    toTokenAccount,
                    fromOwner.account,
                    amount,
                    emptyList()
                )
            },
            fromOwner,
            emptyList(),
            DefaultRpcParams()
        ).metadata.err
    assertNull(error, "Token transfer failed with error: $error")
}

//TODO temporary method code, it will be replaced by new method from Solana estValidator using Sava client
fun SolanaTestValidator.createAta(feePayer: Signer, mintAccount: PublicKey, ownerAccount: PublicKey): PublicKey {

    val rpcClient = SolanaJsonRpcClient(HttpClient.newHttpClient(), SolanaTestValidator.RPC_URL)
    val tokenProgramId = Token2022.PROGRAM_ID.toPublicKey()
    val pda = AssociatedTokenProgram.deriveAddress(ownerAccount, tokenProgramId, mintAccount)
    val instruction = AssociatedTokenProgram.createAssociatedTokenAccount(
        pda,
        mintAccount,
        ownerAccount,
        feePayer.account,
        tokenProgramId,
        false,
    )
    rpcClient.sendAndConfirm(
        { txBuilder ->
            txBuilder.append(instruction)
        },
        feePayer,
        emptyList(),
        rpcParams
    )
    return pda.address()
}