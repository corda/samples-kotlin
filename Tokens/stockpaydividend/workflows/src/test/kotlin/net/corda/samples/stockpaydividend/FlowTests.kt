package net.corda.samples.stockpaydividend

import com.r3.corda.lib.tokens.money.FiatCurrency.Companion.getInstance
import com.r3.corda.lib.tokens.workflows.utilities.tokenBalance
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.samples.stockpaydividend.flows.*
import net.corda.samples.stockpaydividend.states.DividendState
import net.corda.samples.stockpaydividend.states.StockState
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.util.Calendar
import java.util.Date
import java.util.concurrent.ExecutionException
import kotlin.math.pow

class FlowTests {
    private lateinit var network: MockNetwork
    private lateinit var company: StartedMockNode
    private lateinit var observer: StartedMockNode
    private lateinit var shareholder: StartedMockNode
    private lateinit var bank: StartedMockNode
    private lateinit var exDate: Date
    private lateinit var payDate: Date

    private lateinit var notary: StartedMockNode
    private lateinit var notaryParty: Party

    private val COMPANY = TestIdentity(CordaX500Name("Company", "TestVillage", "US"))
    private val SHAREHOLDER = TestIdentity(CordaX500Name("Shareholder", "TestVillage", "US"))
    private val BANK = TestIdentity(CordaX500Name("Bank", "Rulerland", "US"))
    private val OBSERVER = TestIdentity(CordaX500Name("Observer", "Rulerland", "US"))

    private val STOCK_SYMBOL = "TEST"
    private val STOCK_NAME = "Test Stock"
    private val STOCK_CURRENCY = "USD"
    private val STOCK_PRICE = BigDecimal.valueOf(7.4)
    private val ISSUING_STOCK_QUANTITY = 2000
    private val BUYING_STOCK = java.lang.Long.valueOf(500)
    private val ISSUING_MONEY = 5000000
    private val ANNOUNCING_DIVIDEND = BigDecimal("0.03")

    @Before
    fun setup() {
        network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("net.corda.samples.stockpaydividend.contracts"),
                TestCordapp.findCordapp("net.corda.samples.stockpaydividend.flows"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows")
        ), networkParameters = testNetworkParameters(minimumPlatformVersion = 4)))

        company = network.createPartyNode(COMPANY.name)
        observer = network.createPartyNode(OBSERVER.name)
        shareholder = network.createPartyNode(SHAREHOLDER.name)
        bank = network.createPartyNode(BANK.name)
        notary = network.notaryNodes[0]
        notaryParty = notary.info.legalIdentities[0]

        // Set execution date as tomorrow
        val c = Calendar.getInstance()
        c.add(Calendar.DATE, 1)
        exDate = c.time

        // Set pay date as the day after tomorrow
        c.add(Calendar.DATE, 1)
        payDate = c.time
        network.startNodes()
    }

    @After
    fun tearDown() {
        if (::network.isInitialized) {
            network.stopNodes()
        }
    }

    @Test
    @Throws(ExecutionException::class, InterruptedException::class)
    fun issueTest() {
        // Issue Stock
        val future = company.startFlow(CreateAndIssueStock(STOCK_SYMBOL, STOCK_NAME, STOCK_CURRENCY, STOCK_PRICE, ISSUING_STOCK_QUANTITY, notaryParty))
        network.runNetwork()
        val stx = future.get()
        val stxID = stx.substring(stx.lastIndexOf(" ") + 1)
        val stxIDHash: SecureHash = SecureHash.parse(stxID)

        //Check if company and observer of the stock have recorded the transactions
        val issuerTx = company.services.validatedTransactions.getTransaction(stxIDHash)
        val observerTx = observer.services.validatedTransactions.getTransaction(stxIDHash)
        assertNotNull(issuerTx)
        assertNotNull(observerTx)
        assertEquals(issuerTx, observerTx)
    }


    @Test
    @Throws(ExecutionException::class, InterruptedException::class)
    fun moveTest() {
        // Issue Stock
        var future = company.startFlow<String?>(CreateAndIssueStock(STOCK_SYMBOL, STOCK_NAME, STOCK_CURRENCY, STOCK_PRICE, ISSUING_STOCK_QUANTITY, notaryParty))
        network.runNetwork()
        future.get()

        // Move Stock
        future = company.startFlow(MoveStock(STOCK_SYMBOL, BUYING_STOCK, shareholder.info.legalIdentities[0]))
        network.runNetwork()
        val moveTx = future.get()

        //Retrieve states from receiver
        val receivedStockStatesPages = shareholder.services.vaultService.queryBy(StockState::class.java).states
        val receivedStockState = receivedStockStatesPages[0].state.data
        val (quantity) = shareholder.services.vaultService.tokenBalance(receivedStockState.toPointer(receivedStockState.javaClass))

        //Check
        assertEquals(quantity, java.lang.Long.valueOf(500).toLong())

        //Retrieve states from sender
        val remainingStockStatesPages = company.services.vaultService.queryBy(StockState::class.java).states
        val remainingStockState = remainingStockStatesPages[0].state.data
        val (quantity1) = company.services.vaultService.tokenBalance(remainingStockState.toPointer(remainingStockState.javaClass))

        //Check
        assertEquals(quantity1, java.lang.Long.valueOf(1500).toLong())
    }

    @Test
    @Throws(ExecutionException::class, InterruptedException::class)
    fun announceDividendTest() {
        // Issue Stock
        var future = company.startFlow(
                CreateAndIssueStock(STOCK_SYMBOL, STOCK_NAME, STOCK_CURRENCY, STOCK_PRICE, ISSUING_STOCK_QUANTITY, notaryParty))
        network.runNetwork()
        future.get()

        // Move Stock
        future = company.startFlow(MoveStock(STOCK_SYMBOL, BUYING_STOCK, shareholder.info.legalIdentities[0]))
        network.runNetwork()
        future.get()

        // Announce Dividend
        future = company.startFlow(AnnounceDividend(STOCK_SYMBOL, ANNOUNCING_DIVIDEND, exDate, payDate))
        network.runNetwork()
        val announceTxSting = future.get()
        val stxID = announceTxSting.substring(announceTxSting.lastIndexOf(" ") + 1)
        val announceTx: SecureHash = SecureHash.parse(stxID)

        // Retrieve states from sender
        val remainingStockStatesPages = company.services.vaultService.queryBy(StockState::class.java).states
        val (_, _, _, _, _, dividend, exDate, payDate) = remainingStockStatesPages[0].state.data
        assertEquals(dividend, ANNOUNCING_DIVIDEND)
        assertEquals(exDate, exDate)
        assertEquals(payDate, payDate)

        // Check observer has recorded the same transaction
        val issuerTx = company.services.validatedTransactions.getTransaction(announceTx)
        val observerTx = observer.services.validatedTransactions.getTransaction(announceTx)
        assertNotNull(issuerTx)
        assertNotNull(observerTx)
        assertEquals(issuerTx, observerTx)
    }

    @Test
    @Throws(ExecutionException::class, InterruptedException::class)
    fun getStockUpdateTest() {
        // Issue Stock
        var future = company.startFlow<String?>(CreateAndIssueStock(STOCK_SYMBOL, STOCK_NAME, STOCK_CURRENCY, STOCK_PRICE, ISSUING_STOCK_QUANTITY, notaryParty))
        network.runNetwork()
        future.get()

        // Move Stock
        future = company.startFlow(MoveStock(STOCK_SYMBOL, BUYING_STOCK, shareholder.info.legalIdentities[0]))
        network.runNetwork()
        future.get()

        // Announce Dividend
        future = company.startFlow(AnnounceDividend(STOCK_SYMBOL, ANNOUNCING_DIVIDEND, exDate, payDate))
        network.runNetwork()
        future.get()

        // Get Stock Update
        future = shareholder.startFlow(ClaimDividendReceivable(STOCK_SYMBOL))
        network.runNetwork()
        future.get()

        // Checks if the shareholder actually receives the same transaction and updated the stock state (with new dividend)
        val issuerStockStateRefs = company.services.vaultService.queryBy(StockState::class.java).states
        val (txhash) = issuerStockStateRefs[0].ref
        val holderStockStateRefs = shareholder.services.vaultService.queryBy(StockState::class.java).states
        val (txhash1) = holderStockStateRefs[0].ref
        assertEquals(txhash, txhash1)
    }


    @Test
    @Throws(ExecutionException::class, InterruptedException::class)
    fun claimDividendTest() {
        // Issue Stock
        var future = company.startFlow(CreateAndIssueStock(STOCK_SYMBOL, STOCK_NAME, STOCK_CURRENCY, STOCK_PRICE, ISSUING_STOCK_QUANTITY, notaryParty))
        network.runNetwork()
        future.get()

        // Move Stock
        future = company.startFlow(MoveStock(STOCK_SYMBOL, BUYING_STOCK, shareholder.info.legalIdentities[0]))
        network.runNetwork()
        val moveTx = future.get()

        // Announce Dividend
        future = company.startFlow(AnnounceDividend(STOCK_SYMBOL, ANNOUNCING_DIVIDEND, exDate, payDate))
        network.runNetwork()
        future.get()

        // Shareholder claims Dividend
        future = shareholder.startFlow(ClaimDividendReceivable(STOCK_SYMBOL))
        network.runNetwork()
        val claimTxString = future.get()
        val stxID = claimTxString.substring(claimTxString.lastIndexOf(" ") + 1)
        val claimTxID: SecureHash = SecureHash.parse(stxID)

        // Checks if the dividend amount is correct
        val holderDividendPages = shareholder.services.vaultService.queryBy(DividendState::class.java).states
        val (_, _, _, dividendAmount) = holderDividendPages[0].state.data
        val fractionalDigit = dividendAmount.token.fractionDigits
        val yieldAmount = BigDecimal.valueOf(BUYING_STOCK).multiply(ANNOUNCING_DIVIDEND)
        val receivingDividend = yieldAmount.multiply(STOCK_PRICE).multiply(BigDecimal.valueOf(10.0.pow(fractionalDigit.toDouble())))
        assertEquals(dividendAmount.quantity, receivingDividend.toLong())

        // Check company and shareholder owns the same transaction
        val issuerTx = company.services.validatedTransactions.getTransaction(claimTxID)
        val holderTx = shareholder.services.validatedTransactions.getTransaction(claimTxID)
        assertNotNull(issuerTx)
        assertNotNull(holderTx)
        assertEquals(issuerTx, holderTx)
    }

    @Test
    @Throws(ExecutionException::class, InterruptedException::class)
    fun payDividendTest() {
        // Issue Money
        var future = bank.startFlow<String?>(IssueMoney(STOCK_CURRENCY, ISSUING_MONEY.toLong(), company.info.legalIdentities[0]))
        network.runNetwork()
        future.get()

        // Issue Stock
        future = company.startFlow(CreateAndIssueStock(STOCK_SYMBOL, STOCK_NAME, STOCK_CURRENCY, STOCK_PRICE, ISSUING_STOCK_QUANTITY, notaryParty))
        network.runNetwork()
        future.get()

        // Move Stock
        future = company.startFlow(MoveStock(STOCK_SYMBOL, BUYING_STOCK, shareholder.info.legalIdentities[0]))
        network.runNetwork()
        future.get()

        // Announce Dividend
        future = company.startFlow(AnnounceDividend(STOCK_SYMBOL, ANNOUNCING_DIVIDEND, exDate, payDate))
        network.runNetwork()
        future.get()

        // Shareholder claims Dividend
        future = shareholder.startFlow(ClaimDividendReceivable(STOCK_SYMBOL))
        network.runNetwork()
        future.get()

        //Pay Dividend
        val futurePayDiv = company.startFlow(PayDividend())
        network.runNetwork()
        val txList = futurePayDiv.get()

        // The above test should only have 1 transaction created
        assertEquals(txList.size.toLong(), 1)
        val payDivTxString = txList[0]
        val stxID = payDivTxString.substring(payDivTxString.lastIndexOf(" ") + 1)
        val payDivTxID: SecureHash = SecureHash.parse(stxID)

        // Checks if no Dividend state left unspent in shareholder's and company's vault
        val holderDivStateRefs = shareholder.services.vaultService.queryBy(DividendState::class.java).states
        assertTrue(holderDivStateRefs.isEmpty())
        val issuerDivStateRefs = company.services.vaultService.queryBy(DividendState::class.java).states
        assertTrue(issuerDivStateRefs.isEmpty())

        // Validates shareholder has received equivalent fiat currencies of the dividend
        val fiatTokenType = getInstance(STOCK_CURRENCY)
        val (quantity) = shareholder.services.vaultService.tokenBalance(fiatTokenType)
        val receivingDividend = BigDecimal.valueOf(BUYING_STOCK).multiply(STOCK_PRICE).multiply(ANNOUNCING_DIVIDEND)
        assertEquals(quantity, receivingDividend.movePointRight(2).toLong())

        // Check company and shareholder owns the same transaction
        val issuerTx = company.services.validatedTransactions.getTransaction(payDivTxID)
        val holderTx = shareholder.services.validatedTransactions.getTransaction(payDivTxID)
        assertNotNull(issuerTx)
        assertNotNull(holderTx)
        assertEquals(issuerTx, holderTx)
    }

}