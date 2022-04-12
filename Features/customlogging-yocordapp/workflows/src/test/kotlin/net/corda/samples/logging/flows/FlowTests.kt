package net.corda.samples.logging.flows

import net.corda.core.identity.CordaX500Name
import net.corda.testing.node.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ExecutionException
import kotlin.test.assertTrue
import kotlin.test.fail

class FlowTests {
    private lateinit var mockNetwork: MockNetwork
    private lateinit var a: StartedMockNode
    private lateinit var b: StartedMockNode

    @Before
    fun setup() {
        mockNetwork = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("net.corda.samples.logging.contracts"),
                TestCordapp.findCordapp("net.corda.samples.logging.flows")
        ),
                notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary","London","GB")))
        ))

        a = mockNetwork.createNode(MockNodeParameters())
        b = mockNetwork.createNode(MockNodeParameters())
        val startedNodes = arrayListOf(a, b)
        // For real nodes this happens automatically, but we have to manually register the flow for tests
        startedNodes.forEach { it.registerInitiatedFlow(YoFlowResponder::class.java) }
        mockNetwork.runNetwork()
    }

    @After
    fun tearDown() {
        if (::mockNetwork.isInitialized) {
            mockNetwork.stopNodes()
        }
    }

    @Throws(ExecutionException::class, InterruptedException::class)
    @Test
    fun dummyTest() {
        val future = a.startFlow(YoFlow(b.info.legalIdentities.first()))
        mockNetwork.runNetwork()
        val ptx = future.get() ?: fail("No flow response")
        assertTrue(ptx.tx.inputs.isEmpty())
    }
}
