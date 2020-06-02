package net.corda.samples.stockpaydividend

import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test

class FlowTests {
    private val network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
        TestCordapp.findCordapp("net.corda.samples.stockpaydividend.contracts"),
        TestCordapp.findCordapp("net.corda.samples.stockpaydividend.flows")
    )))
    private val a = network.createNode()
    private val b = network.createNode()

    init {
        listOf(a, b).forEach {
        }
    }

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun `dummy test`() {

    }
}