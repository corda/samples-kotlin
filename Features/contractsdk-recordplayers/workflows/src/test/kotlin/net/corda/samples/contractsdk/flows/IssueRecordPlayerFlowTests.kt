package net.corda.samples.contractsdk.flows

import com.google.common.collect.ImmutableList
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.NetworkParameters
import net.corda.core.utilities.getOrThrow
import net.corda.samples.contractsdk.states.Needle
import net.corda.samples.contractsdk.states.RecordPlayerState
import net.corda.testing.node.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Practical exercise instructions Flows part 1.
 * Uncomment the unit tests and use the hints + unit test body to complete the FLows such that the unit tests pass.
 */
class IssueRecordPlayerFlowTests {

    private lateinit var network: MockNetwork
    private lateinit var manufacturerNode: StartedMockNode
    private lateinit var dealerBNode: StartedMockNode
    private lateinit var dealerCNode: StartedMockNode
    private lateinit var manufacturer: Party
    private lateinit var dealerB: Party
    private lateinit var dealerC: Party

    private val testNetworkParameters = NetworkParameters(4, emptyList(), 10485760, 10485760 * 5, Instant.now(), 1, LinkedHashMap())

    @Before
    fun setup() {
        network = MockNetwork(MockNetworkParameters(
                notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary","London","GB")))
        ).withCordappsForAllNodes(ImmutableList.of(
                TestCordapp.findCordapp("net.corda.samples.contractsdk.contracts"),
                TestCordapp.findCordapp("net.corda.samples.contractsdk.flows"))).withNetworkParameters(testNetworkParameters)
        )

        manufacturerNode = network.createPartyNode(null)
        dealerBNode = network.createPartyNode(null)
        dealerCNode = network.createPartyNode(null)
        manufacturer = manufacturerNode.info.legalIdentities[0]
        dealerB = dealerBNode.info.legalIdentities[0]
        dealerC = dealerCNode.info.legalIdentities[0]
        network.runNetwork()
    }

    @After
    fun tearDown() {
        if (::network.isInitialized) {
            network.stopNodes()
        }
    }

    @Test
    @Throws(Exception::class)
    fun flowUsesCorrectNotary() {
        // RecordPlayerState st = new RecordPlayerState(manufacturer, dealerB, Needle.SPHERICAL, 100, 700, 10000, 0, new UniqueIdentifier());
        val f1 = IssueRecordPlayerFlow(dealerB, "SPHERICAL")
        val future = manufacturerNode.startFlow(f1)
        network.runNetwork()
        val signedTransaction = future.get()

        if (signedTransaction != null) {
            assertEquals(1, signedTransaction.tx.outputStates.size)
        }
        assertEquals(network.notaryNodes[0].info.legalIdentities[0], signedTransaction?.notary)
    }

    @Test
    @Throws(Exception::class)
    fun contractCorrectness() {
        val issueFlow = IssueRecordPlayerFlow(dealerB, "SPHERICAL")
        val future = manufacturerNode.startFlow(issueFlow)
        network.runNetwork()

        val ptx = future.getOrThrow()

        val (_, contract) = ptx!!.tx.outputs.single()
        assertEquals("net.corda.samples.contractsdk.contracts.RecordPlayerContract", contract)
    }

    @Test
    @Throws(Exception::class)
    fun canCreateState() {
        val st = RecordPlayerState(manufacturer, dealerB, Needle.SPHERICAL, 100, 700, 10000, 0, UniqueIdentifier())
        val issueFlow = IssueRecordPlayerFlow(dealerB, "SPHERICAL")
        val future = manufacturerNode.startFlow(issueFlow)
        network.runNetwork()
        val signedTransaction = future.get()
        val output = signedTransaction!!.tx.outputsOfType(RecordPlayerState::class.java)[0]

        // get some random data from the output to verify
        assertEquals(st.manufacturer, output.manufacturer)
        assertEquals(st.dealer, output.dealer)
        assertNotEquals(st.dealer, output.manufacturer)
        assertEquals(st.needle, output.needle)
    }
}
