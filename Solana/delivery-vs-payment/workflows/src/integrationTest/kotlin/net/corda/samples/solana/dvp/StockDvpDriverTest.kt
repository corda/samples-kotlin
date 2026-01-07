package net.corda.samples.solana.dvp

import com.lmax.solana4j.Solana
import com.lmax.solana4j.api.PublicKey
import com.lmax.solana4j.client.jsonrpc.SolanaJsonRpcClient
import com.lmax.solana4j.programs.AssociatedTokenProgram
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.samples.solana.dvp.flows.CreateAndIssueStock
import net.corda.samples.solana.dvp.flows.SharesDvP
import net.corda.samples.solana.dvp.flows.tokenProgramId
import net.corda.solana.notary.common.Signer
import net.corda.solana.notary.common.rpc.checkResponse
import net.corda.solana.notary.common.rpc.sendAndConfirm
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
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.net.http.HttpClient
import java.nio.file.Path
import kotlin.test.assertEquals

// This is a sample of full-fledged test with both Corda Nodes and Solana Local Validator
class StockDvpDriverTest {

    companion object {
        private val validator = SolanaTestValidator()

        @JvmStatic
        @AfterAll
        fun stopTestValidator() {
            validator.close()
        }
    }

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

    private lateinit var solanaNotaryKeyFile: Path
    private lateinit var solanaNotaryKey: Signer

    // A directory with Corda Notary key pair for singing Corda Program on Solana
    @TempDir
    private lateinit var notaryKeyDir: Path

    // A directory for Notary to store Corda participant key pairs for sining Solana transactions,
    // intentionally these are located in a different directory than Corda Notary Program key pair
    @TempDir
    private lateinit var custodiedKeysDir: Path

    private lateinit var stablecoinAuthority: Signer
    private lateinit var stablecoinAccount: PublicKey

    private lateinit var sellerTokenAccount: PublicKey
    private lateinit var buyerTokenAccount: PublicKey

    @BeforeEach
    fun setup() {
        solanaNotaryKeyFile = randomKeypairFile(notaryKeyDir)
        solanaNotaryKey = Signer.fromFile(solanaNotaryKeyFile)
        validator.start()
        validator.defaultNotaryProgramSetup(solanaNotaryKey.account)

        val buyerWalletFilePath = randomKeypairFile(custodiedKeysDir)
        val buyerWallet = Signer.fromFile(buyerWalletFilePath)

        val sellerWalletFilePath = randomKeypairFile(custodiedKeysDir)
        val sellerWallet = Signer.fromFile(sellerWalletFilePath)
        stablecoinAuthority = Signer.random()

        setOf(stablecoinAuthority, sellerWallet, buyerWallet).forEach {
            validator.fundAccount(100000, it)
        }
        stablecoinAccount =
            validator.createToken(stablecoinAuthority, decimals = SOLANA_TOKEN_DECIMALS.toByte(), isToken2022 = false)
        sellerTokenAccount =
            AssociatedTokenProgram.deriveAddress(sellerWallet.account, tokenProgramId, stablecoinAccount).address()
        buyerTokenAccount = validator.createAta(stablecoinAuthority, stablecoinAccount, buyerWallet.account)

        validator.mintTo(
            stablecoinAuthority,
            stablecoinAccount,
            buyerTokenAccount,
            SOLANA_TOKEN_AMOUNT,
            isToken2022 = false
        )

        // corda configs
        notaryConfig = mapOf(
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
        sellerDvpCordappConfig = mapOf(
            "solanaTokenMint" to stablecoinAccount.base58(),
            "solanaWalletFile" to sellerWalletFilePath.toString(),
            "solanaRpcUrl" to SolanaTestValidator.RPC_URL,
            "solanaWsUrl" to SolanaTestValidator.WS_URL
        )
        buyerDvpCordappConfig = mapOf(
            "solanaTokenMint" to stablecoinAccount.base58(),
            "solanaWalletFile" to buyerWalletFilePath.toString(),
            "solanaRpcUrl" to SolanaTestValidator.RPC_URL,
            "solanaWsUrl" to SolanaTestValidator.WS_URL
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

    private fun Pubkey.toPublicKey(): PublicKey = Solana.account(bytes)

    private fun SolanaTestValidator.getTokenBalance(publicKey: PublicKey): BigDecimal =
        client
            .getTokenAccountBalance(publicKey.base58(), rpcParams)
            .checkResponse("getTokenAccountBalance")!!
            .uiAmountString
            .toBigDecimal()

    //TODO temporary method code, it will be replaced by new method from Solana estValidator using Sava client
    fun SolanaTestValidator.createAta(feePayer: Signer, mintAccount: PublicKey, ownerAccount: PublicKey): PublicKey {

        val rpcClient = SolanaJsonRpcClient(HttpClient.newHttpClient(), SolanaTestValidator.RPC_URL)
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
}