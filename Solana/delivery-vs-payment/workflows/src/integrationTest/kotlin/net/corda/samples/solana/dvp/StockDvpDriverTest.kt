package net.corda.samples.solana.dvp

import com.lmax.solana4j.Solana
import com.lmax.solana4j.api.PublicKey
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.samples.solana.dvp.flows.CreateAndIssueStock
import net.corda.samples.solana.dvp.flows.SharesDvP
import net.corda.solana.notary.common.Signer
import net.corda.solana.notary.common.rpc.checkResponse
import net.corda.solana.sdk.SplToken
import net.corda.solana.sdk.instruction.Pubkey
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.TestCordapp
import net.corda.testing.solana.SolanaTestValidator
import net.corda.testing.solana.randomKeypairFile
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.nio.file.Path
import kotlin.collections.emptyList
import kotlin.lazy
import kotlin.test.assertEquals

// This is a sample of full-fledged test with both Corda Nodes and Solana Local Validator
class StockDvpDriverTest {

    private val STOCK_SYMBOL = "AAPL"
    private val STOCK_NAME = "Apple"
    private val STOCK_CURRENCY = "USD"
    private val STOCK_PRICE = BigDecimal.valueOf(7.4)
    private val ISSUING_STOCK_QUANTITY = 200000L
    private val DELIVERY_STOCK_QUANTITY = 100L

    private val SOLANA_TOKEN_AMOUNT = 1000000L
    private val SOLANA_TOKEN_DECIMALS = 3
    private val SOLANA_BUYER_INITIAL_AMOUNT = BigDecimal(1000)
    private val SOLANA_PAYMENT = BigDecimal(740) // STOCK_PRICE * DELIVERY_STOCK_QUANTITY

    private val seller = TestIdentity(CordaX500Name("BankA", "", "GB"))
    private val buyer = TestIdentity(CordaX500Name("BankB", "", "US"))
    private val observer = CordaX500Name("Observer", "New York", "US")
    private val solanaNotaryName = CordaX500Name("Notary", "London", "GB")

    private val validator = SolanaTestValidator()
    private lateinit var solanaNotaryKeyFile: Path
    private lateinit var solanaNotaryKey: Signer
    private val mintAuthoritySigner by lazy { Signer.fromFile(randomKeypairFile(custodiedKeysDir)) }
    private lateinit var tokenMint: PublicKey
    private val sellerWallet = Signer.random()
    private val buyerWallet by lazy { Signer.fromFile(randomKeypairFile(custodiedKeysDir)) }
    private lateinit var sellerTokenAccount: PublicKey
    private lateinit var buyerTokenAccount: PublicKey

    @TempDir
    private lateinit var custodiedKeysDir: Path

    @TempDir
    private lateinit var generalDir: Path
    private val notaryConfig: Map<String, Any> by lazy {
        mapOf(
            "notary" to mapOf(
                "validating" to false,
                "solana" to mapOf(
                    "rpcUrl" to SolanaTestValidator.RPC_URL,
                    "websocketUrl" to SolanaTestValidator.WS_URL,
                    "notaryKeypairFile" to "$solanaNotaryKeyFile",
                    "custodiedKeysDir" to "$custodiedKeysDir",
                    "programWhitelist" to listOf(SplToken.PROGRAM_ID.toPublicKey().base58()),
                )
            )
        )
    }
    private val dvpFlowCordapp = TestCordapp.findCordapp("net.corda.samples.solana.dvp.flows")
    private val cordappsForAllNodes: List<TestCordapp> =
        setOf(
            "com.r3.corda.lib.tokens.contracts",
            "com.r3.corda.lib.tokens.workflows",
            "net.corda.samples.solana.dvp.contracts",
            "net.corda.samples.solana.dvp.states",
        ).map { TestCordapp.findCordapp(it) }

    private val sellerDvpCordappConfig: Map<String, Any> by lazy {
        mapOf(
            "solanaTokenMint" to tokenMint.base58(),
            "solanaTokenAccount" to sellerTokenAccount.base58(),
            "solanaWalletAccount" to sellerWallet.account.base58(), // not used in  the test
            "solanaRpcUrl" to "http://127.0.0.1:8899",
            "solanaWsUrl" to "ws://127.0.0.1:8900"
        )
    }

    private val buyerDvpCordappConfig: Map<String, Any> by lazy {
        mapOf(
            "solanaTokenMint" to tokenMint.base58(),
            "solanaTokenAccount" to buyerTokenAccount.base58(),
            "solanaWalletAccount" to buyerWallet.account.base58(),
            "solanaRpcUrl" to "http://127.0.0.1:8899",
            "solanaWsUrl" to "ws://127.0.0.1:8900"
        )
    }

    @BeforeEach
    fun setup() {
        solanaNotaryKeyFile = randomKeypairFile(generalDir)
        solanaNotaryKey = Signer.fromFile(solanaNotaryKeyFile)
        validator.start()
        validator.defaultNotaryProgramSetup(solanaNotaryKey.account)
        setOf(mintAuthoritySigner, sellerWallet, buyerWallet).forEach {
            validator.fundAccount(100000, it)
        }
        tokenMint =
            validator.createToken(mintAuthoritySigner, decimals = SOLANA_TOKEN_DECIMALS.toByte(), isToken2022 = false)
        sellerTokenAccount = validator.createTokenAccount(sellerWallet, tokenMint, isToken2022 = false)
        buyerTokenAccount = validator.createTokenAccount(buyerWallet, tokenMint, isToken2022 = false)
        validator.mintTo(mintAuthoritySigner, tokenMint, buyerTokenAccount, SOLANA_TOKEN_AMOUNT, isToken2022 = false)
    }

    @AfterEach
    fun stopTestValidator() {
        validator.close()
    }

    @Test
    fun `dvp test`() = withDriver {
        val seller = startNode(
            NodeParameters().withAdditionalCordapps(setOf(dvpFlowCordapp.withConfig(sellerDvpCordappConfig))),
            seller.name
        ).getOrThrow()
        val buyer = startNode(
            NodeParameters().withAdditionalCordapps(setOf(dvpFlowCordapp.withConfig(buyerDvpCordappConfig))),
            buyer.name
        ).getOrThrow()
        startNode(providedName = observer).getOrThrow()

        assertEquals(
            BigDecimal.ZERO,
            validator.getTokenBalance(sellerTokenAccount),
            "Seller's initial Solana balance is zero"
        )
        assertEquals(
            SOLANA_BUYER_INITIAL_AMOUNT,
            validator.getTokenBalance(buyerTokenAccount),
            "Buyer's initial Solana balance is non-zero"
        )

        seller.rpc.startFlow(
            ::CreateAndIssueStock,
            STOCK_SYMBOL,
            STOCK_NAME,
            STOCK_CURRENCY,
            STOCK_PRICE,
            ISSUING_STOCK_QUANTITY
        ).returnValue.get()

        assertTrue(
            buyer.rpc.vaultQuery(FungibleToken::class.java).states.isEmpty(),
            "Initially Buyer has no assets on Corda network"
        )

        seller.rpc.startFlow(
            ::SharesDvP,
            STOCK_SYMBOL,
            DELIVERY_STOCK_QUANTITY,
            buyer.nodeInfo.legalIdentities[0]
        ).returnValue.get()

        val buyerAssetsOnCorda = buyer.rpc.vaultQuery(FungibleToken::class.java).states
        val amount = buyerAssetsOnCorda
            // Simplified as Corda network has a one asset type, we don't need to check Corda state details (issuer and token pointer)
            .sumOf { it.state.data.amount.quantity }
        assertEquals(DELIVERY_STOCK_QUANTITY, amount, "Buyer received assets on Corda network")

        assertEquals(
            SOLANA_PAYMENT,
            validator.getTokenBalance(sellerTokenAccount),
            "Seller's Solana balance equals payment from buyer"
        )
        assertEquals(
            SOLANA_BUYER_INITIAL_AMOUNT - SOLANA_PAYMENT,
            validator.getTokenBalance(buyerTokenAccount),
            "Buyer's Solana balance is reduced by it's payment"
        )
    }

    // Runs a test inside the Driver DSL
    private fun withDriver(test: DriverDSL.() -> Unit) = driver(
        DriverParameters(
            isDebug = true,
            startNodesInProcess = false,
            cordappsForAllNodes = cordappsForAllNodes,
            notarySpecs = listOf(NotarySpec(solanaNotaryName, notaryConfig, startInProcess = false)),
            networkParameters = testNetworkParameters(minimumPlatformVersion = 4).copy(notaries = emptyList())
        )
    ) { test() }

    private fun Pubkey.toPublicKey(): PublicKey = Solana.account(bytes)

    private fun SolanaTestValidator.getTokenBalance(publicKey: PublicKey): BigDecimal =
        client
            .getTokenAccountBalance(publicKey.base58(), rpcParams)
            .checkResponse("getTokenAccountBalance")!!
            .uiAmountString
            .toBigDecimal()
}