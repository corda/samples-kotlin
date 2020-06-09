package com.accounts_SupplyChain

import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.internal.findCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test

class FlowTests {
    private val network = MockNetwork(MockNetworkParameters().withCordappsForAllNodes(
            listOf(
                    findCordapp("com.accounts_SupplyChain.contracts")
            )
    ))
    private val a = network.createNode()
    private val b = network.createNode()

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun `dummy test`() {
    }
}