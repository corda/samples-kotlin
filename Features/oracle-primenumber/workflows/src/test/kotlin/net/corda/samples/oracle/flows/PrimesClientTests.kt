package net.corda.samples.oracle.flows

import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.samples.oracle.states.PrimeState
import net.corda.testing.node.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PrimesClientTests {
    private val mockNet = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
            TestCordapp.findCordapp("net.corda.samples.oracle.flows"),
            TestCordapp.findCordapp("net.corda.samples.oracle.contracts")),
            notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary","London","GB")))
    ))
    private lateinit var a: StartedMockNode

    @Before
    fun setUp() {
        a = mockNet.createNode()

        val oracleName = CordaX500Name("Oracle", "New York", "US")
        val oracle = mockNet.createNode(MockNodeParameters(legalName = oracleName))
        listOf(QueryHandler::class.java, SignHandler::class.java).forEach { oracle.registerInitiatedFlow(it) }

        mockNet.runNetwork()
    }

    @After
    fun tearDown() {
        mockNet.stopNodes()
    }

    @Test
    fun `oracle returns correct Nth prime`() {
        val flow = a.startFlow(CreatePrime(100))
        mockNet.runNetwork()
        val result = flow.getOrThrow().tx.outputsOfType<PrimeState>().single()
        assertEquals(100, result.n)
        val prime100 = 541
        assertEquals(prime100, result.nthPrime)
    }

}
