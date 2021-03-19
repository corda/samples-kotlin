package net.corda.samples.duediligence

import net.corda.samples.duediligence.flows.RequestToValidateCorporateRecordsInitiator
import net.corda.testing.node.*
import org.junit.After
import org.junit.Before
import org.junit.Test


class FlowTests {

    lateinit var network: MockNetwork
    lateinit var a: StartedMockNode
    lateinit var b: StartedMockNode

    @Before
    fun setup() {
        network = MockNetwork(MockNetworkParameters(
                cordappsForAllNodes = listOf(
                        TestCordapp.findCordapp("net.corda.samples.duediligence.contracts"),
                        TestCordapp.findCordapp("net.corda.samples.duediligence.flows"))))
        a = network.createPartyNode()
        b = network.createPartyNode()
        network.runNetwork()
    }

    @After
    fun tearDown() {

    }

    @Test
    fun `Auditing Request Creation test`() {
        val createRequestFlow = RequestToValidateCorporateRecordsInitiator(
                validater = b.info.legalIdentities[0],numberOfFiles = 10)
        val future = a.startFlow(createRequestFlow)
        network.runNetwork()
        val RequestCreationResult = future.get()
        print(RequestCreationResult)
    }
}