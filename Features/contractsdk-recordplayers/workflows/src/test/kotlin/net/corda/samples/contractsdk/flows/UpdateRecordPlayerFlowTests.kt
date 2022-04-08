package net.corda.samples.contractsdk.flows

import com.google.common.collect.ImmutableList
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.NetworkParameters
import net.corda.samples.contractsdk.states.Needle
import net.corda.samples.contractsdk.states.RecordPlayerState
import net.corda.testing.node.*
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

class UpdateRecordPlayerFlowTests {
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
        val f1 = IssueRecordPlayerFlow(dealerB, "SPHERICAL")
        val future = manufacturerNode.startFlow(f1)
        network.runNetwork()
        val f1Output = future.get()!!.tx.outputsOfType(RecordPlayerState::class.java)[0]
        val f2 = UpdateRecordPlayerFlow(f1Output.linearId, "damaged", f1Output.magneticStrength, f1Output.coilTurns, f1Output.amplifierSNR, f1Output.songsPlayed)
        val future2 = dealerBNode.startFlow(f2)
        network.runNetwork()
        val f2Output = future2.get()!!.tx.outputsOfType(RecordPlayerState::class.java)[0]
        val signedTransaction = future.get()

        // assert our contract SDK conditions
        assertEquals(1, signedTransaction!!.tx.outputStates.size)
        assertEquals(network.notaryNodes[0].info.legalIdentities[0], signedTransaction.notary)
    }

    // ensure that our linear state updates work correctly
    @Test
    @Throws(Exception::class)
    fun flowUpdateTest() {
        val f1 = IssueRecordPlayerFlow(dealerB, "SPHERICAL")
        val future = manufacturerNode.startFlow(f1)
        network.runNetwork()
        val f1Output = future.get()!!.tx.outputsOfType(RecordPlayerState::class.java)[0]

        val f2 = UpdateRecordPlayerFlow(
                f1Output.linearId,
                "damaged",
                f1Output.magneticStrength,
                f1Output.coilTurns,
                f1Output.amplifierSNR,
                f1Output.songsPlayed + 5)
        val future2 = dealerBNode.startFlow(f2)
        network.runNetwork()
        val f2Output = future2.get()!!.tx.outputsOfType(RecordPlayerState::class.java)[0]
        assertEquals(Needle.SPHERICAL, f1Output.needle)
        assertEquals(Needle.DAMAGED, f2Output.needle)
        assertEquals(f1Output.magneticStrength, f2Output.magneticStrength)
        assertEquals(f1Output.songsPlayed + 5, f2Output.songsPlayed)
        assertNotEquals(f1Output.songsPlayed.toLong(), f2Output.songsPlayed.toLong())
    }
}
