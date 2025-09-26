package net.corda.samples.stockpaydividend

import com.lmax.solana4j.api.PublicKey
import com.r3.corda.lib.tokens.bridging.rpc.BridgeStock
import com.r3.corda.lib.tokens.bridging.states.BridgedAssetLockState
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.workflows.utilities.tokenBalance
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.samples.stockpaydividend.flows.*
import net.corda.samples.stockpaydividend.states.StockState
import net.corda.solana.aggregator.common.RpcParams
import net.corda.solana.aggregator.common.Signer
import net.corda.solana.aggregator.common.checkResponse
import net.corda.solana.sdk.internal.Token2022
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
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.internal.DUMMY_CONTRACTS_CORDAPP
import net.corda.testing.solana.randomKeypairFile
import org.junit.ClassRule
import org.junit.rules.TemporaryFolder
import java.nio.file.Path

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
    val LINEAR_ID = UniqueIdentifier()

    companion object {
        val notaryName = CordaX500Name("Solana Notary Service", "Zurich", "CH")

        @ClassRule
        @JvmField
        val generalDir = TemporaryFolder()

        @ClassRule
        @JvmField
        val custodiedKeysDir = TemporaryFolder()

        private lateinit var notaryKeyFile: Path
        private lateinit var notaryKey: Signer
        private lateinit var mintAuthority: Signer
        private val tokenAccountOwner = Signer.random()
        private lateinit var testValidator: SolanaTestValidator

        private lateinit var tokenMint: PublicKey
        private lateinit var tokenAccount: PublicKey

        @BeforeClass
        @JvmStatic
        fun startTestValidator() {
            testValidator = SolanaTestValidator()
            notaryKeyFile = generalDir.randomKeypairFile()
            notaryKey = Signer.fromFile(notaryKeyFile)
            mintAuthority = Signer.fromFile(custodiedKeysDir.randomKeypairFile())
            testValidator.start()
            testValidator.defaultNotaryProgramSetup(notaryKey.account)
            testValidator.fundAccount(10, mintAuthority)
            testValidator.fundAccount(10, tokenAccountOwner)

            val accountOwner = Signer.random()

            testValidator.fundAccount(10, accountOwner)

            tokenMint = testValidator.createToken(mintAuthority)
            tokenAccount = testValidator.createTokenAccount(accountOwner, tokenMint)
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
        val app = TestCordapp.findCordapp("net.corda.samples.stockpaydividend.flows")
        network = MockNetwork(
            MockNetworkParameters(
                cordappsForAllNodes = listOf(
                    TestCordapp.findCordapp("net.corda.samples.stockpaydividend.contracts"),
                    app,
                    TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                    TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                    DUMMY_CONTRACTS_CORDAPP
                ), notarySpecs = listOf(MockNetworkNotarySpec(notaryName, notaryConfig = createNotaryConfig())),
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
            )
        )

        val companyCfg = mapOf(
            "participants" to mapOf(COMPANY.name.toString() to tokenAccount.base58()),
            "mints" to mapOf(LINEAR_ID.toString() to tokenMint.base58()),
            "mintAuthorities" to mapOf(LINEAR_ID.toString() to mintAuthority.account.base58())
        )
        company = network!!.createNode(
            MockNodeParameters(
                legalName = COMPANY.name,
                additionalCordapps = listOf(app.withConfig(companyCfg))
            )
        )
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
                                notaryKeypairFile = "$notaryKeyFile"
                                custodiedKeysDir = "${custodiedKeysDir.root.toPath()}"
                                programWhitelist = ["${Token2022.PROGRAM_ID}"]          
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
        val cordaTokenId = receivedStockState.toPointer<StockState>().pointer.pointer.id
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

        // Issue Stock
        var future = company!!.startFlow<String?>(
            CreateAndIssueStock(
                STOCK_SYMBOL,
                STOCK_NAME,
                STOCK_CURRENCY,
                STOCK_PRICE,
                ISSUING_STOCK_QUANTITY,
                notaryParty!!,
                LINEAR_ID
            )
        )
        network!!.runNetwork()
        future.get()

        val stockStatesPages = company!!.services.vaultService.queryBy(StockState::class.java).states
        Assert.assertNotNull(stockStatesPages[0])
        val stockState = stockStatesPages[0].state.data
        val stockStatePointer = stockState.toPointer(stockState.javaClass)
        val (startCordaQuantity) = company!!.services.vaultService.tokenBalance(stockStatePointer)
        Assert.assertEquals(ISSUING_STOCK_QUANTITY.toLong(), startCordaQuantity)

        val startSolanaBalance =
            testValidator.client.getTokenAccountBalance(tokenAccount.base58(), RpcParams())
                .checkResponse("getTokenAccountBalance")
        Assert.assertEquals("0", startSolanaBalance!!.amount)

        // TODO Spend all to avoid having a change to yourself - then can't distinguish which amount si to mint which is a change
        future = company!!.startFlow(
            BridgeStock(
                STOCK_SYMBOL,
                startCordaQuantity ,
                company!!.info.legalIdentities[0]
            )
        )

        network!!.runNetwork()
        future.get()

        val remainingStockStatesPages = company!!.services.vaultService.queryBy(StockState::class.java).states
        Assert.assertNotNull(remainingStockStatesPages[0])
        val remainingStockState = remainingStockStatesPages[0].state.data
        val remainingStockStatePointer: TokenPointer<StockState> =
            remainingStockState.toPointer(remainingStockState.javaClass)
        val (finalCordaQuantity) = company!!.services.vaultService.tokenBalance(remainingStockStatePointer)
        Assert.assertEquals(
            ISSUING_STOCK_QUANTITY.toLong(),
            finalCordaQuantity
        ) // TODO this is Corda move token to self, so it still the same amount as at the beginning

        val token: StateAndRef<FungibleToken>? =
            company!!.services.vaultService.queryBy(FungibleToken::class.java).states.firstOrNull {
                it.state.data.amount.token.tokenType == remainingStockStatePointer
            }
        Assert.assertNotNull(token)
        val bridgingState: StateAndRef<BridgedAssetLockState>? =
            company!!.services.vaultService.queryBy(BridgedAssetLockState::class.java).states.firstOrNull()
        Assert.assertNotNull(bridgingState)

        Assert.assertTrue(
            "Bridge state and token are outputs of the same transaction",
            bridgingState!!.ref.txhash == token!!.ref.txhash
        )

        val finalSolanaBalance =
            testValidator.client.getTokenAccountBalance(tokenAccount.base58(), RpcParams())
                .checkResponse("getTokenAccountBalance")

        Assert.assertEquals(ISSUING_STOCK_QUANTITY.toString(), finalSolanaBalance!!.amount)
    }

}