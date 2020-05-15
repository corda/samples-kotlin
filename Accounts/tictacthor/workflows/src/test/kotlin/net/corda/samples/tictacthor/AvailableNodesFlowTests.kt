package net.corda.samples.tictacthor

import net.corda.samples.tictacthor.contracts.BoardContract
import net.corda.samples.tictacthor.flows.StartGameFlow
import net.corda.samples.tictacthor.flows.StartGameFlowResponder
import net.corda.samples.tictacthor.states.BoardState
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.toX500Name
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.internal.chooseIdentityAndCert
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.fail

class AvailableNodesFlowTests {

    private val mockNetwork = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
        TestCordapp.findCordapp("net.corda.samples.tictacthor.contracts"),
        TestCordapp.findCordapp("net.corda.samples.tictacthor.flows")
    )))

    private lateinit var nodeA: StartedMockNode
    private lateinit var nodeB: StartedMockNode
    private lateinit var nodeC: StartedMockNode
    private lateinit var nodeD: StartedMockNode
    private lateinit var partyA: Party
    private lateinit var partyB: Party
    private lateinit var partyC: Party
    private lateinit var partyD: Party
    private lateinit var allParties: List<String>

    @Before
    fun setup() {
        nodeA = mockNetwork.createNode()
        nodeB = mockNetwork.createNode()
        nodeC = mockNetwork.createNode()
        nodeD = mockNetwork.createNode()
        partyA = nodeA.info.chooseIdentityAndCert().party
        partyB = nodeB.info.chooseIdentityAndCert().party
        partyC = nodeC.info.chooseIdentityAndCert().party
        partyD = nodeD.info.chooseIdentityAndCert().party
        allParties = listOf(partyA, partyB, partyC, partyD).map { it.name.toString() }
    }

    @After
    fun tearDown() = mockNetwork.stopNodes()

//    @Test
//    fun `AvailableNodesFlow returns a properly formatted list of strings`() {
//        val future = nodeA.startFlow(AvailableNodesFlow())
//        mockNetwork.runNetwork()
//        val availableParties = future.getOrThrow()
//        assert(availableParties is List<String>)
//        for (partyString in availableParties) {
//            try {
//                assert(allParties.contains(partyString))
//                val x500Name = CordaX500Name.parse(partyString)
//            }
//            catch (e: IllegalArgumentException) {
//                fail()
//            }
//        }
//    }
//
//    @Test
//    fun `test AvailableNodesFlow (no game)`() {
//        val future = nodeA.startFlow(AvailableNodesFlow())
//        mockNetwork.runNetwork()
//        val availableParties: List<String> = future.getOrThrow()
//        assertEquals(allParties - partyA.name.toString(), availableParties)
//    }
//
//    @Test
//    fun `test AvailableNodesFlow (game)`() {
//        val future = nodeA.startFlow(StartGameFlow(partyB))
//        mockNetwork.runNetwork()
//        future.getOrThrow()
//
//        val future2 = nodeC.startFlow(AvailableNodesFlow())
//        mockNetwork.runNetwork()
//        val availableParties: List<String> = future2.getOrThrow()
//        assertEquals(allParties - partyA.name.toString() - partyB.name.toString() - partyC.name.toString(), availableParties)
//    }

}