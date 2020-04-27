package net.corda.samples.tictacthor

import net.corda.samples.tictacthor.states.BoardState
import net.corda.core.identity.Party
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
import com.r3.corda.lib.accounts.workflows.accountService
import net.corda.samples.tictacthor.flows.*
import net.corda.core.flows.FlowLogic


class StartGameFlowTests {

    private val mockNetwork = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
            TestCordapp.findCordapp("net.corda.samples.tictacthor.contracts"),
            TestCordapp.findCordapp("net.corda.samples.tictacthor.flows"),
            TestCordapp.findCordapp("com.r3.corda.lib.ci"),
            TestCordapp.findCordapp("com.r3.corda.lib.accounts.contracts"),
            TestCordapp.findCordapp("com.r3.corda.lib.accounts.workflows")

    )))

    private lateinit var nodeA: StartedMockNode
    private lateinit var nodeB: StartedMockNode
    private lateinit var partyA: Party
    private lateinit var partyB: Party

    @Before
    fun setup() {
        nodeA = mockNetwork.createNode()
        nodeB = mockNetwork.createNode()
        partyA = nodeA.info.chooseIdentityAndCert().party
        partyB = nodeB.info.chooseIdentityAndCert().party
        listOf(nodeA, nodeB).forEach {
            it.registerInitiatedFlow(StartGameFlowResponder::class.java)
        }
    }

    @After
    fun tearDown() = mockNetwork.stopNodes()


    @Test
    fun createaccount(){
    }


    private fun <T> StartedMockNode.runFlowAndReturn(flow: FlowLogic<T>): T {
        val future = this.startFlow(flow)
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

//    @Test
//    fun flowReturnsCorrectlyFormedTransaction() {
//        val future = nodeA.startFlow(StartGameFlow(partyB))
//        mockNetwork.runNetwork()
//        val ptx: SignedTransaction = future.getOrThrow()
//
//        assert(ptx.tx.inputs.isEmpty())
//        assert(ptx.tx.outputs.size == 1)
//        assert(ptx.tx.outputs[0].data is BoardState)
//        assert(ptx.tx.commands.singleOrNull() != null)
//        assert(ptx.tx.commands.single().value is BoardContract.Commands.StartGame)
//        assert(ptx.tx.requiredSigningKeys.equals(setOf(partyA.owningKey, partyB.owningKey)))
//    }
//
//    @Test
//    fun flowReturnsTransactionSignedByBothParties() {
//        val future = nodeA.startFlow(StartGameFlow(partyB))
//        mockNetwork.runNetwork()
//        val stx = future.getOrThrow()
//        stx.verifyRequiredSignatures()
//    }
//
//    @Test
//    fun flowRecordsTheSameTransactionInBothPartyVaults() {
//        val future = nodeA.startFlow(StartGameFlow(partyB))
//        mockNetwork.runNetwork()
//        val stx = future.getOrThrow()
//
//        listOf(nodeA, nodeB).map {
//            it.services.validatedTransactions.getTransaction(stx.id)
//        }.forEach {
//            val txHash = (it as SignedTransaction).id
//            assertEquals(txHash, stx.id)
//        }
//    }

}