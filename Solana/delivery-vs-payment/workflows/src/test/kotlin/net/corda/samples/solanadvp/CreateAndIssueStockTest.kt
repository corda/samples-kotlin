package net.corda.samples.solanadvp

import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.AppServiceHub
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.samples.solanadvp.flows.CreateAndIssueStock
import net.corda.samples.solanadvp.flows.SolanaService
import net.corda.solana.sdk.instruction.Pubkey
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import java.math.BigDecimal
import java.util.concurrent.Future

// This is sample of a test with Corda states only without Solana
class CreateAndIssueStockTest {
    private var network: MockNetwork? = null
    private var issuer: StartedMockNode? = null
    private var observer: StartedMockNode? = null

    @BeforeEach
    fun setup() {
        network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("net.corda.samples.solanadvp.contracts"),
                TestCordapp.findCordapp("net.corda.samples.solanadvp.flows"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows")
        ), networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary","London","GB")))
        ))
        val unstarted = network!!.createUnstartedNode()
        unstarted.installCordaService(SolanaService::class.java)
        issuer = unstarted.start()

        val unstarted2 = network!!.createUnstartedNode(CordaX500Name("Observer", "New York", "US"))
        unstarted2.installCordaService(SolanaService::class.java)
        observer = unstarted.start()
        network!!.runNetwork()
    }

    @AfterEach
    fun tearDown() {
        network!!.stopNodes()
    }

    @Test
    fun `state creation test`() {
        val createAndIssueFlow = CreateAndIssueStock( "AAPL", "Apple",  "USD", BigDecimal(273.12),  1)
        val future: Future<String> = issuer!!.startFlow(createAndIssueFlow)
        network!!.runNetwork()
        val stx = future.get()
        val stxID = stx.substring(stx.lastIndexOf(" ") + 1)
        val stxIDHash: SecureHash = SecureHash.parse(stxID)

        //Check if company and observer of the stock have recorded the transactions
        val issuerTx = issuer!!.services.validatedTransactions.getTransaction(stxIDHash)
        val observerTx = observer!!.services.validatedTransactions.getTransaction(stxIDHash)
        assertNotNull(issuerTx)
        assertNotNull(observerTx)
        assertEquals(issuerTx, observerTx)
    }
}

class SolanaService(appServiceHub: AppServiceHub) : SingletonSerializeAsToken() {
    fun getAccountMintDecimals(account: Pubkey): Int {
        return 3
    }
}