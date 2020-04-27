package net.corda.samples.tictacthor

import net.corda.samples.tictacthor.contracts.BoardContract
import net.corda.samples.tictacthor.flows.*
import net.corda.samples.tictacthor.states.BoardState
import net.corda.samples.tictacthor.states.Status
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.flows.FlowException
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.lang.IllegalStateException
import kotlin.test.assertEquals

class EndGameIntegrationTest {

    private val mockNetwork = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
            TestCordapp.findCordapp("net.corda.samples.tictacthor.contracts"),
            TestCordapp.findCordapp("net.corda.samples.tictacthor.flows")
    )))
    private lateinit var nodeA: StartedMockNode
    private lateinit var nodeB: StartedMockNode

//    @Before
//    fun setup() {
//        nodeA = mockNetwork.createNode(MockNodeParameters())
//        nodeB = mockNetwork.createNode(MockNodeParameters())
//        listOf(nodeA, nodeB).forEach {
//            it.registerInitiatedFlow(EndGameFlowResponder::class.java)
//        }
//    }
//
//    @After
//    fun tearDown() = mockNetwork.stopNodes()
//
//    @Test
//    fun `end game test (win)`()  {
//
//        val partyA = nodeA.info.chooseIdentity()
//        val partyB = nodeB.info.chooseIdentity()
//
//        // Setup Game
//        val futureWithGameState = nodeA.startFlow(StartGameFlow(partyB))
//        mockNetwork.runNetwork()
//        var boardState = getBoardState(futureWithGameState.getOrThrow())
//        assertEquals(boardState.playerO, partyA)
//        assertEquals(boardState.playerX, partyB)
//        assertEquals(partyA, boardState.getCurrentPlayerParty())
//        assert(!BoardContract.BoardUtils.isGameOver(boardState))
//
//        // Move #1
//        boardState = makeMoveAndGetNewBoardState(nodeA, 0,0)
//        assertEquals(partyB, boardState.getCurrentPlayerParty())
//        assert(!BoardContract.BoardUtils.isGameOver(boardState))
//
//        // Move #2
//        boardState = makeMoveAndGetNewBoardState(nodeB, 1,0)
//        assertEquals(partyA, boardState.getCurrentPlayerParty())
//        assert(!BoardContract.BoardUtils.isGameOver(boardState))
//
//        // Move #3
//        boardState = makeMoveAndGetNewBoardState(nodeA, 0,1)
//        assertEquals(partyB, boardState.getCurrentPlayerParty())
//        assert(!BoardContract.BoardUtils.isGameOver(boardState))
//
//        // Move #4
//        boardState = makeMoveAndGetNewBoardState(nodeB, 2,1)
//        assertEquals(partyA, boardState.getCurrentPlayerParty())
//        assert(!BoardContract.BoardUtils.isGameOver(boardState))
//
//        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(boardState.linearId))
//        val boardStateNodeA = nodeA.services.vaultService.queryBy<BoardState>(queryCriteria).states.single()
//        val boardStateNodeB = nodeB.services.vaultService.queryBy<BoardState>(queryCriteria).states.single()
//        assertEquals(boardStateNodeA.state.data.linearId, boardStateNodeB.state.data.linearId)
//
//        // Move #5
//        boardState = makeMoveAndGetNewBoardState(nodeA, 0,2)
//        assertEquals(partyB, boardState.getCurrentPlayerParty())
//        assert(BoardContract.BoardUtils.isGameOver(boardState))
//
//        assertEquals(nodeA.services.vaultService.queryBy<BoardState>(queryCriteria).states.single().state.data.status, Status.GAME_OVER)
//        assertEquals(nodeB.services.vaultService.queryBy<BoardState>(queryCriteria).states.single().state.data.status, Status.GAME_OVER)
//
//        // End Game
//        val futureEndGame = nodeA.startFlow(EndGameFlow())
//        mockNetwork.runNetwork()
//
//        assert(nodeA.services.vaultService.queryBy<BoardState>(queryCriteria).states.isEmpty())
//        assert(nodeB.services.vaultService.queryBy<BoardState>(queryCriteria).states.isEmpty())
//    }
//
//
//    @Test
//    fun `end game test (no win)`()  {
//
//        val partyA = nodeA.info.chooseIdentity()
//        val partyB = nodeB.info.chooseIdentity()
//
//        // Setup Game
//        val futureWithGameState = nodeA.startFlow(StartGameFlow(partyB))
//        mockNetwork.runNetwork()
//        var boardState = getBoardState(futureWithGameState.getOrThrow())
//        assertEquals(boardState.playerO, partyA)
//        assertEquals(boardState.playerX, partyB)
//        assertEquals(partyA, boardState.getCurrentPlayerParty())
//        assert(!BoardContract.BoardUtils.isGameOver(boardState))
//
//        // Move #1
//        boardState = makeMoveAndGetNewBoardState(nodeA, 0,0)
//        assertEquals(partyB, boardState.getCurrentPlayerParty())
//        assert(!BoardContract.BoardUtils.isGameOver(boardState))
//
//        // Move #2
//        boardState = makeMoveAndGetNewBoardState(nodeB, 1,0)
//        assertEquals(partyA, boardState.getCurrentPlayerParty())
//        assert(!BoardContract.BoardUtils.isGameOver(boardState))
//
//        // Move #3
//        boardState = makeMoveAndGetNewBoardState(nodeA, 2,0)
//        assertEquals(partyB, boardState.getCurrentPlayerParty())
//        assert(!BoardContract.BoardUtils.isGameOver(boardState))
//
//        // Move #4
//        boardState = makeMoveAndGetNewBoardState(nodeB, 0,2)
//        assertEquals(partyA, boardState.getCurrentPlayerParty())
//        assert(!BoardContract.BoardUtils.isGameOver(boardState))
//
//        // Move #5
//        boardState = makeMoveAndGetNewBoardState(nodeA, 0,1)
//        assertEquals(partyB, boardState.getCurrentPlayerParty())
//        assert(!BoardContract.BoardUtils.isGameOver(boardState))
//
//        // Move #6
//        boardState = makeMoveAndGetNewBoardState(nodeB, 1,1)
//        assertEquals(partyA, boardState.getCurrentPlayerParty())
//        assert(!BoardContract.BoardUtils.isGameOver(boardState))
//
//        // Move #7
//        boardState = makeMoveAndGetNewBoardState(nodeA, 1,2)
//        assertEquals(partyB, boardState.getCurrentPlayerParty())
//        assert(!BoardContract.BoardUtils.isGameOver(boardState))
//
//        // Move #8
//        boardState = makeMoveAndGetNewBoardState(nodeB, 2,2)
//        assertEquals(partyA, boardState.getCurrentPlayerParty())
//        assert(!BoardContract.BoardUtils.isGameOver(boardState))
//
//        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(boardState.linearId))
//        val boardStateNodeA = nodeA.services.vaultService.queryBy<BoardState>(queryCriteria).states.single()
//        val boardStateNodeB = nodeB.services.vaultService.queryBy<BoardState>(queryCriteria).states.single()
//        assertEquals(boardStateNodeA.state.data.linearId, boardStateNodeB.state.data.linearId)
//
//        assertEquals(nodeA.services.vaultService.queryBy<BoardState>(queryCriteria).states.single().state.data.status, Status.GAME_IN_PROGRESS)
//        assertEquals(nodeB.services.vaultService.queryBy<BoardState>(queryCriteria).states.single().state.data.status, Status.GAME_IN_PROGRESS)
//
//        // Move #9
//        boardState = makeMoveAndGetNewBoardState(nodeA, 2,1)
//        assertEquals(partyB, boardState.getCurrentPlayerParty())
//        assert(BoardContract.BoardUtils.isGameOver(boardState))
//
//        assertEquals(nodeA.services.vaultService.queryBy<BoardState>(queryCriteria).states.single().state.data.status, Status.GAME_OVER)
//        assertEquals(nodeB.services.vaultService.queryBy<BoardState>(queryCriteria).states.single().state.data.status, Status.GAME_OVER)
//
//        // End Game
//        nodeA.startFlow(EndGameFlow())
//        mockNetwork.runNetwork()
//
//        assert(nodeA.services.vaultService.queryBy<BoardState>(queryCriteria).states.isEmpty())
//        assert(nodeB.services.vaultService.queryBy<BoardState>(queryCriteria).states.isEmpty())
//    }
//
//
//    @Test
//    fun `invalid move test`()  {
//        val partyA = nodeA.info.chooseIdentity()
//        val partyB = nodeB.info.chooseIdentity()
//
//        // Setup Game
//        val futureWithGameState = nodeA.startFlow(StartGameFlow(partyB))
//        mockNetwork.runNetwork()
//        var boardState = getBoardState(futureWithGameState.getOrThrow())
//        assertEquals(boardState.playerO, partyA)
//        assertEquals(boardState.playerX, partyB)
//        assertEquals(partyA, boardState.getCurrentPlayerParty())
//        assert(!BoardContract.BoardUtils.isGameOver(boardState))
//
//        // Move #1
//        boardState = makeMoveAndGetNewBoardState(nodeA, 0,0)
//        assertEquals(partyB, boardState.getCurrentPlayerParty())
//        assert(!BoardContract.BoardUtils.isGameOver(boardState))
//
//        // Move #2
//        val future = nodeB.startFlow(SubmitTurnFlow(0, 0))
//        mockNetwork.runNetwork()
//
//        var exception = Exception()
//        try {
//            future.getOrThrow()
//        }
//        catch (e: Exception) {
//            exception = e
//        }
//        assert(exception is TransactionVerificationException)
//        assertEquals("java.lang.IllegalArgumentException: Failed requirement: Not valid board update.", exception.cause.toString())
//    }
//
//
//    @Test
//    fun `end game when not end game`()  {
//        val partyA = nodeA.info.chooseIdentity()
//        val partyB = nodeB.info.chooseIdentity()
//
//        // Setup Game
//        val futureWithGameState = nodeA.startFlow(StartGameFlow(partyB))
//        mockNetwork.runNetwork()
//        var boardState = getBoardState(futureWithGameState.getOrThrow())
//        assertEquals(boardState.playerO, partyA)
//        assertEquals(boardState.playerX, partyB)
//        assertEquals(partyA, boardState.getCurrentPlayerParty())
//        assert(!BoardContract.BoardUtils.isGameOver(boardState))
//
//        // Move #1
//        boardState = makeMoveAndGetNewBoardState(nodeA, 0,0)
//        assertEquals(partyB, boardState.getCurrentPlayerParty())
//        assert(!BoardContract.BoardUtils.isGameOver(boardState))
//
//        // Move #2
//        val future = nodeB.startFlow(EndGameFlow())
//        mockNetwork.runNetwork()
//
//        var exception = Exception()
//        try {
//            future.getOrThrow()
//        }
//        catch (e: Exception) {
//            exception = e
//        }
//        assert(exception is TransactionVerificationException)
//        assertEquals("java.lang.IllegalArgumentException: Failed requirement: Input board must have status GAME_OVER.", exception.cause.toString())
//    }
//
//    @Test
//    fun `moves out of order`()  {
//        val partyA = nodeA.info.chooseIdentity()
//        val partyB = nodeB.info.chooseIdentity()
//
//        // Setup Game
//        val futureWithGameState = nodeA.startFlow(StartGameFlow(partyB))
//        mockNetwork.runNetwork()
//        var boardState = getBoardState(futureWithGameState.getOrThrow())
//        assertEquals(boardState.playerO, partyA)
//        assertEquals(boardState.playerX, partyB)
//        assertEquals(partyA, boardState.getCurrentPlayerParty())
//        assert(!BoardContract.BoardUtils.isGameOver(boardState))
//
//        // Move #1
//        boardState = makeMoveAndGetNewBoardState(nodeA, 0,0)
//        assertEquals(partyB, boardState.getCurrentPlayerParty())
//        assert(!BoardContract.BoardUtils.isGameOver(boardState))
//
//        // Move #2
//        val future = nodeA.startFlow(SubmitTurnFlow(0, 1))
//        mockNetwork.runNetwork()
//
//        var exception = Exception()
//        try {
//            future.getOrThrow()
//        }
//        catch (e: Exception) {
//            exception = e
//        }
//        assert(exception is FlowException)
//        assertEquals("It's not your turn!", exception.message.toString())
//    }
//
//    @Test
//    fun `invalid index`()  {
//        val partyA = nodeA.info.chooseIdentity()
//        val partyB = nodeB.info.chooseIdentity()
//
//        // Setup Game
//        val futureWithGameState = nodeA.startFlow(StartGameFlow(partyB))
//        mockNetwork.runNetwork()
//        var boardState = getBoardState(futureWithGameState.getOrThrow())
//        assertEquals(boardState.playerO, partyA)
//        assertEquals(boardState.playerX, partyB)
//        assertEquals(partyA, boardState.getCurrentPlayerParty())
//        assert(!BoardContract.BoardUtils.isGameOver(boardState))
//
//        // Move #1
//        boardState = makeMoveAndGetNewBoardState(nodeA, 0,0)
//        assertEquals(partyB, boardState.getCurrentPlayerParty())
//        assert(!BoardContract.BoardUtils.isGameOver(boardState))
//
//        // Move #2
//        val future = nodeB.startFlow(SubmitTurnFlow(0, 3))
//        mockNetwork.runNetwork()
//
//        var exception = Exception()
//        try {
//            future.getOrThrow()
//        }
//        catch (e: Exception) {
//            exception = e
//        }
//        assert(exception is IllegalStateException)
//        assertEquals("Invalid board index.", exception.message.toString())
//
//    }
//
//
//    private fun makeMoveAndGetNewBoardState(node: StartedMockNode, x: Int, y: Int): BoardState {
//        val futureWithGameState = node.startFlow(SubmitTurnFlow(x, y))
//        mockNetwork.runNetwork()
//        return getBoardState(futureWithGameState.getOrThrow())
//    }
//
//    private fun getBoardState(tx: SignedTransaction): BoardState = tx.coreTransaction.outputsOfType<BoardState>().single()

}