package net.corda.samples.solana.dvp

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.samples.solana.dvp.flows.CreateAndIssueStock
import net.corda.samples.solana.dvp.flows.SharesDvP
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.TestCordapp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import software.sava.core.accounts.PublicKey
import software.sava.core.accounts.Signer
import software.sava.rpc.json.http.client.SolanaRpcClient
import java.math.BigDecimal
import java.nio.file.Path
import com.r3.corda.lib.solana.core.FileSigner
import com.r3.corda.lib.solana.core.SolanaUtils
import com.r3.corda.lib.solana.core.tokens.TokenManagement.Companion.getAssociatedTokenAccountAddress
import com.r3.corda.lib.solana.testing.SolanaTestValidator
import net.corda.solana.notary.testing.Notary
import net.corda.solana.notary.testing.SolanaNotaryExtension

// This is a sample of full-fledged test with Corda Nodes and Solana Local Validator
@ExtendWith(SolanaNotaryExtension::class)
class StockDvpDriverTest {
    private lateinit var validator: SolanaTestValidator

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
    private lateinit var notaryConfig: Map<String, Any>
    private val dvpFlowCordapp = TestCordapp.findCordapp("net.corda.samples.solana.dvp.flows")
    private val cordappsForAllNodes: List<TestCordapp> =
        setOf(
            "com.r3.corda.lib.tokens.contracts",
            "com.r3.corda.lib.tokens.workflows",
            "net.corda.samples.solana.dvp.contracts",
            "net.corda.samples.solana.dvp.states",
        ).map { TestCordapp.findCordapp(it) }

    private lateinit var sellerDvpCordappConfig: Map<String, Any>
    private lateinit var buyerDvpCordappConfig: Map<String, Any>

    // A directory for Notary to store Corda participant key pairs for sining Solana transactions,
    // intentionally these are located in a different directory than Corda Notary Program key pair
    @TempDir
    private lateinit var custodiedKeysDir: Path

    private lateinit var stablecoinAuthority: Signer
    private lateinit var stablecoinAccount: PublicKey

    private lateinit var sellerTokenAccount: PublicKey
    private lateinit var buyerTokenAccount: PublicKey

    @BeforeEach
    fun setup(validator: SolanaTestValidator, @Notary solanaNotarySigner: FileSigner) {
        this.validator = validator
        val buyerWallet = FileSigner.random(custodiedKeysDir)
        val sellerWallet = FileSigner.random(custodiedKeysDir)
        stablecoinAuthority = SolanaUtils.randomSigner()

        setOf(stablecoinAuthority, sellerWallet, buyerWallet).forEach {
            validator.accounts().airdropSol(it.publicKey(), 10)
        }
        stablecoinAccount =
            validator.tokens().createToken(stablecoinAuthority, decimals = SOLANA_TOKEN_DECIMALS)
        sellerTokenAccount = getAssociatedTokenAccountAddress(
            stablecoinAccount,
            sellerWallet.publicKey()
        )
        buyerTokenAccount = validator.tokens().createAssociatedTokenAccount(
            stablecoinAuthority,
            stablecoinAccount,
            buyerWallet.publicKey()
        )
        validator.tokens().mintTo(
            buyerTokenAccount,
            stablecoinAccount,
            stablecoinAuthority,
            SOLANA_TOKEN_AMOUNT
        )

        // corda configs
        notaryConfig = mapOf(
            "notary" to mapOf(
                "validating" to false,
                "solana" to mapOf(
                    "rpcUrl" to "${validator.rpcUrl()}",
                    "websocketUrl" to "${validator.websocketUrl()}",
                    "notaryKeypairFile" to "${solanaNotarySigner.file}",
                    "custodiedKeysDir" to "$custodiedKeysDir"
                )
            )
        )
        sellerDvpCordappConfig = mapOf(
            "stablecoinTokenMint" to stablecoinAccount.toBase58(),
            "solanaWalletFile" to sellerWallet.file.toString(),
            "solanaRpcUrl" to "${validator.rpcUrl()}",
            "solanaWebsocketUrl" to "${validator.websocketUrl()}"
        )
        buyerDvpCordappConfig = mapOf(
            "stablecoinTokenMint" to stablecoinAccount.toBase58(),
            "solanaWalletFile" to buyerWallet.file.toString(),
            "solanaRpcUrl" to "${validator.rpcUrl()}",
            "solanaWebsocketUrl" to "${validator.websocketUrl()}"
        )
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

        assertThrows<Exception>("Seller's initial Solana balance is zero") {
            validator.getTokenBalance(sellerTokenAccount)
        }
        assertEquals(
            SOLANA_BUYER_INITIAL_AMOUNT,
            validator.getTokenBalance(buyerTokenAccount),
            "Buyer's initial Solana balance is non-zero"
        )
        // Test
        seller.rpc.startFlow(
            ::CreateAndIssueStock,
            STOCK_SYMBOL,
            STOCK_NAME,
            STOCK_CURRENCY,
            STOCK_PRICE,
            ISSUING_STOCK_QUANTITY,
            solanaNotaryName
        ).returnValue.get()

        assertTrue(
            buyer.rpc.vaultQuery(FungibleToken::class.java).states.isEmpty(),
            "Initially Buyer has no assets on Corda network"
        )

        seller.rpc.startFlow(
            ::SharesDvP,
            STOCK_SYMBOL,
            DELIVERY_STOCK_QUANTITY,
            buyer.nodeInfo.legalIdentities[0],
            solanaNotaryName
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
            networkParameters = testNetworkParameters(minimumPlatformVersion = 160).copy(notaries = emptyList())
        )
    ) { test() }

    private fun SolanaTestValidator.getTokenBalance(publicKey: PublicKey): BigDecimal =
        client().call(SolanaRpcClient::getTokenAccountBalance, publicKey)
            .toDecimal()
            .setScale(0) // normalize scale e.g. value as 1E+3 to 1000 to allow easier quality check
}