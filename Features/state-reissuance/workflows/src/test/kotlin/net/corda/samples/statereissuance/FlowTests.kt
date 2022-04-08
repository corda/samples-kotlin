package net.corda.samples.statereissuance

import net.corda.testing.node.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import net.corda.core.identity.CordaX500Name

class FlowTests {
    private lateinit var network: MockNetwork
    private lateinit var a: StartedMockNode
    private lateinit var b: StartedMockNode

    @Before
    fun setup() {
        network = MockNetwork(
            MockNetworkParameters(
                cordappsForAllNodes = listOf(
                    TestCordapp.findCordapp("net.corda.samples.statereissuance.contracts"),
                    TestCordapp.findCordapp("net.corda.samples.statereissuance.flows")
                ),
                notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary", "London", "GB")))
            )
        )
        a = network.createPartyNode()
        b = network.createPartyNode()
        network.runNetwork()
    }

    @After
    fun tearDown() {
        if (::network.isInitialized) {
            network.stopNodes()
        }
    }

    @Test
    fun dummyTest() {
    }
}
