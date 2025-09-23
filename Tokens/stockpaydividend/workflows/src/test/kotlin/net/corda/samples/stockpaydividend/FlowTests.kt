package net.corda.samples.stockpaydividend

import com.r3.corda.lib.tokens.bridging.rpc.BridgeStock
import com.r3.corda.lib.tokens.bridging.states.BridgedAssetLockState
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.workflows.utilities.tokenBalance
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.samples.stockpaydividend.flows.*
import net.corda.samples.stockpaydividend.states.StockState
import net.corda.solana.aggregator.common.RpcParams
import net.corda.solana.aggregator.common.Signer
import net.corda.solana.aggregator.common.checkResponse
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import net.corda.testing.solana.SolanaTestValidator
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.ExecutionException
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.internal.DUMMY_CONTRACTS_CORDAPP

class FlowTests {
    private var network: MockNetwork? = null
    private var company: StartedMockNode? = null
    private var observer: StartedMockNode? = null
    private var shareholder: StartedMockNode? = null
    private var bank: StartedMockNode? = null
    private var exDate: Date? = null
    private var payDate: Date? = null

    private var notary: StartedMockNode? = null
    private var notaryParty: Party? = null

    var COMPANY = TestIdentity(CordaX500Name("Company", "TestVillage", "US"))
    var SHAREHOLDER = TestIdentity(CordaX500Name("Shareholder", "TestVillage", "US"))
    var BANK = TestIdentity(CordaX500Name("Bank", "Rulerland", "US"))
    var OBSERVER = TestIdentity(CordaX500Name("Observer", "Rulerland", "US"))

    val STOCK_SYMBOL = "TEST"
    val STOCK_NAME = "Test Stock"
    val STOCK_CURRENCY = "USD"
    val STOCK_PRICE = BigDecimal.valueOf(7.4)
    val ISSUING_STOCK_QUANTITY = 2000
    val BUYING_STOCK = java.lang.Long.valueOf(500)

    companion object {
        val notaryName = CordaX500Name("Solana Notary Service", "Zurich", "CH")

        private lateinit var testValidator: SolanaTestValidator

        @BeforeClass
        @JvmStatic
        fun startTestValidator() {
            testValidator = SolanaTestValidator()
            testValidator.fundDevAccounts()
            testValidator.defaultNotarySetup()
        }

        @AfterClass
        @JvmStatic
        fun stopTestValidator() {
            if (::testValidator.isInitialized) {
                testValidator.close()
            }
        }
    }

    @Before
    fun setup() {
        network = MockNetwork(
            MockNetworkParameters(
                cordappsForAllNodes = listOf(
                    TestCordapp.findCordapp("net.corda.samples.stockpaydividend.contracts"),
                    TestCordapp.findCordapp("net.corda.samples.stockpaydividend.flows"),
                    TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                    TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                    DUMMY_CONTRACTS_CORDAPP
                ), notarySpecs = listOf(MockNetworkNotarySpec(notaryName, notaryConfig = createNotaryConfig())),
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
            )
        )

        company = network!!.createPartyNode(COMPANY.name)
        observer = network!!.createPartyNode(OBSERVER.name)
        shareholder = network!!.createPartyNode(SHAREHOLDER.name)
        bank = network!!.createPartyNode(BANK.name)
        notary = network!!.notaryNodes[0]
        notaryParty = notary!!.info.legalIdentities[0]

        // Set execution date as tomorrow
        val c = Calendar.getInstance()
        c.add(Calendar.DATE, 1)
        exDate = c.time

        // Set pay date as the day after tomorrow
        c.add(Calendar.DATE, 1)
        payDate = c.time
        network!!.startNodes()
    }

    @After
    fun tearDown() {
        network!!.stopNodes()
    }

    private fun createNotaryConfig(): String = """
                            validating = false
                            notaryLegalIdentity = "$notaryName"
                            solana {
                                rpcUrl = "${SolanaTestValidator.RPC_URL}"
                                wallet = "${SolanaTestValidator.DEV_NOTARY_FILE}"
                            }
                        """.trimIndent()

    @Test
    @Throws(ExecutionException::class, InterruptedException::class)
    fun issueTest() {
        // Issue Stock
        val future = company!!.startFlow(
            CreateAndIssueStock(
                STOCK_SYMBOL,
                STOCK_NAME,
                STOCK_CURRENCY,
                STOCK_PRICE,
                ISSUING_STOCK_QUANTITY,
                notaryParty!!
            )
        )
        network!!.runNetwork()
        val stx = future.get()
        val stxID = stx.substring(stx.lastIndexOf(" ") + 1)
        val stxIDHash: SecureHash = SecureHash.parse(stxID)

        //Check if company and observer of the stock have recorded the transactions
        val issuerTx = company!!.services.validatedTransactions.getTransaction(stxIDHash)
        val observerTx = observer!!.services.validatedTransactions.getTransaction(stxIDHash)
        Assert.assertNotNull(issuerTx)
        Assert.assertNotNull(observerTx)
        Assert.assertEquals(issuerTx, observerTx)
    }


    @Test
    @Throws(ExecutionException::class, InterruptedException::class)
    fun moveTest() {
        // Issue Stock
        var future = company!!.startFlow<String?>(
            CreateAndIssueStock(
                STOCK_SYMBOL,
                STOCK_NAME,
                STOCK_CURRENCY,
                STOCK_PRICE,
                ISSUING_STOCK_QUANTITY,
                notaryParty!!
            )
        )
        network!!.runNetwork()
        future.get()

        // Move Stock
        future = company!!.startFlow(MoveStock(STOCK_SYMBOL, BUYING_STOCK, shareholder!!.info.legalIdentities[0]))
        network!!.runNetwork()
        future.get()

        //Retrieve states from receiver
        val receivedStockStatesPages = shareholder!!.services.vaultService.queryBy(StockState::class.java).states
        val receivedStockState = receivedStockStatesPages[0].state.data
        val (quantity) = shareholder!!.services.vaultService.tokenBalance(
            receivedStockState.toPointer(
                receivedStockState.javaClass
            )
        )

        //Check
        Assert.assertEquals(quantity, java.lang.Long.valueOf(500).toLong())

        //Retrieve states from sender
        val remainingStockStatesPages = company!!.services.vaultService.queryBy(StockState::class.java).states
        val remainingStockState = remainingStockStatesPages[0].state.data
        val (quantity1) = company!!.services.vaultService.tokenBalance(remainingStockState.toPointer(remainingStockState.javaClass))

        //Check
        Assert.assertEquals(quantity1, java.lang.Long.valueOf(1500).toLong())
    }

    @Test
    @Throws(ExecutionException::class, InterruptedException::class)
    fun bridgeTest() {

        val mintAuthority = SolanaTestValidator.DEV_NOTARY
        val accountOwner = Signer.random()

        testValidator.fundAccount(10, accountOwner)

        val tokenMint = testValidator.createToken(mintAuthority)
        val tokenAccount = testValidator.createTokenAccount(accountOwner, tokenMint)

        // Issue Stock
        var future = company!!.startFlow<String?>(
            CreateAndIssueStock(
                STOCK_SYMBOL,
                STOCK_NAME,
                STOCK_CURRENCY,
                STOCK_PRICE,
                ISSUING_STOCK_QUANTITY,
                notaryParty!!
            )
        )
        network!!.runNetwork()
        future.get()

        val stockStatesPages = company!!.services.vaultService.queryBy(StockState::class.java).states
        val stockState = stockStatesPages[0].state.data
        val stockStatePointer = stockState.toPointer(stockState.javaClass)
        val (startCordaQuantity) = company!!.services.vaultService.tokenBalance(stockStatePointer)
        Assert.assertEquals(2000L, startCordaQuantity)

        val startSolanaBalance =
            testValidator.client.getTokenAccountBalance(tokenAccount.base58(), RpcParams())
                .checkResponse("getTokenAccountBalance")
        Assert.assertEquals("0", startSolanaBalance!!.amount)

        // TODO Spend all to avoid having a change to yourself - then can't distinguish which amount si to mint which is a change
        future = company!!.startFlow(
            BridgeStock(
                STOCK_SYMBOL,
                ISSUING_STOCK_QUANTITY.toLong() /*BUYING_STOCK*/,
                company!!.info.legalIdentities[0],
                tokenAccount.base58(),
                tokenMint.base58(),
                mintAuthority.account.base58()
            )
        )

        network!!.runNetwork()
        val result = future.get()
        println(result)

        val remainingStockStatesPages = company!!.services.vaultService.queryBy(StockState::class.java).states
        val remainingStockState = remainingStockStatesPages[0].state.data
        val (quantity1) = company!!.services.vaultService.tokenBalance(remainingStockState.toPointer(remainingStockState.javaClass))

        Assert.assertEquals(quantity1, java.lang.Long.valueOf(2000).toLong())

        val tokenPointer: TokenPointer<StockState> = stockState.toPointer(stockState.javaClass)
        val token: StateAndRef<FungibleToken>? =
            company!!.services.vaultService.queryBy(FungibleToken::class.java).states.firstOrNull { it.state.data.amount.token.tokenType == tokenPointer }
        Assert.assertNotNull(token)
        val bridgingState: StateAndRef<BridgedAssetLockState>? =
            company!!.services.vaultService.queryBy(BridgedAssetLockState::class.java).states.firstOrNull()
        Assert.assertNotNull(bridgingState)

        Assert.assertTrue(
            "Bridge state and token are outputs of the same transaction",
            bridgingState!!.ref.txhash == token!!.ref.txhash
        )

        val (finalCordaQuantity) = company!!.services.vaultService.tokenBalance(stockStatePointer)
        Assert.assertEquals(
            2000L,
            finalCordaQuantity
        ) //TODO this is Corda move token to self, so it still the same amount as at the beginning

        val finalSolanaBalance =
            testValidator.client.getTokenAccountBalance(tokenAccount.base58(), RpcParams())
                .checkResponse("getTokenAccountBalance")

        Assert.assertEquals("2000", finalSolanaBalance!!.amount)
    }

}