package net.corda.samples.stockpaydividend

import com.r3.corda.lib.tokens.workflows.utilities.tokenBalance
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.samples.stockpaydividend.flows.*
import net.corda.samples.stockpaydividend.states.StockState
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.ExecutionException

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

    @Before
    fun setup() {
        network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("net.corda.samples.stockpaydividend.contracts"),
                TestCordapp.findCordapp("net.corda.samples.stockpaydividend.flows"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows")
        ), networkParameters = testNetworkParameters(minimumPlatformVersion = 4)))

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

    @Test
    @Throws(ExecutionException::class, InterruptedException::class)
    fun issueTest() {
        // Issue Stock
        val future = company!!.startFlow(CreateAndIssueStock(STOCK_SYMBOL, STOCK_NAME, STOCK_CURRENCY, STOCK_PRICE, ISSUING_STOCK_QUANTITY, notaryParty!!))
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
        var future = company!!.startFlow<String?>(CreateAndIssueStock(STOCK_SYMBOL, STOCK_NAME, STOCK_CURRENCY, STOCK_PRICE, ISSUING_STOCK_QUANTITY, notaryParty!!))
        network!!.runNetwork()
        future.get()

        // Move Stock
        future = company!!.startFlow(MoveStock(STOCK_SYMBOL, BUYING_STOCK, shareholder!!.info.legalIdentities[0]))
        network!!.runNetwork()
        future.get()

        //Retrieve states from receiver
        val receivedStockStatesPages = shareholder!!.services.vaultService.queryBy(StockState::class.java).states
        val receivedStockState = receivedStockStatesPages[0].state.data
        val (quantity) = shareholder!!.services.vaultService.tokenBalance(receivedStockState.toPointer(receivedStockState.javaClass))

        //Check
        Assert.assertEquals(quantity, java.lang.Long.valueOf(500).toLong())

        //Retrieve states from sender
        val remainingStockStatesPages = company!!.services.vaultService.queryBy(StockState::class.java).states
        val remainingStockState = remainingStockStatesPages[0].state.data
        val (quantity1) = company!!.services.vaultService.tokenBalance(remainingStockState.toPointer(remainingStockState.javaClass))

        //Check
        Assert.assertEquals(quantity1, java.lang.Long.valueOf(1500).toLong())
    }

}