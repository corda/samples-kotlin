package net.corda.samples.tictacthor.contracts

import com.template.states.BoardState
import com.template.states.Status
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Before
import org.junit.Test
import java.security.PublicKey

class BoardContractEndGameTests {
    private val ledgerServices = MockServices()

    class DummyCommand : TypeOnlyCommandData()

    lateinit var boardState: BoardState
    lateinit var publicKeys: List<PublicKey>
    lateinit var partyA: Party
    lateinit var partyB: Party

    @Before
    fun setup() {
        partyA = TestIdentity(CordaX500Name("PartyA","London","GB")).party
        partyB = TestIdentity(CordaX500Name("PartyB","New York","US")).party
        boardState = BoardState(partyA, partyB)
        val board: Array<CharArray> = arrayOf(charArrayOf('O', 'O', 'X'),
                                              charArrayOf('O', 'X', 'X'),
                                              charArrayOf('O', 'X', 'O'))
        boardState = boardState.copy(status = Status.GAME_OVER, board = board)
        publicKeys = boardState.participants.map {it.owningKey}
    }

    @Test
    fun mustIncludeEndGameCommand() {
        ledgerServices.ledger {
            transaction {
                input(BoardContract.ID, boardState)
                command(publicKeys, BoardContract.Commands.EndGame())
                this.verifies()
            }
            transaction {
                input(BoardContract.ID, boardState)
                command(publicKeys, DummyCommand())
                this.fails()
            }
        }
    }

    @Test
    fun endGameTransactionMustHaveOneInput() {
        ledgerServices.ledger {
            transaction {
                input(BoardContract.ID, boardState)
                command(publicKeys, BoardContract.Commands.EndGame())
                this.verifies()
            }
            transaction {
                input(BoardContract.ID, DummyState())
                command(publicKeys, BoardContract.Commands.EndGame())
                this `fails with` "The input state should be of type BoardState."
            }
            transaction {
                input(BoardContract.ID, boardState)
                input(BoardContract.ID, DummyState())
                command(publicKeys, BoardContract.Commands.EndGame())
                this `fails with` "There should be one input state."
            }
        }
    }

    @Test
    fun endGameTransactionMustHaveNoOutput() {
        ledgerServices.ledger {
            transaction {
                input(BoardContract.ID, boardState)
                command(publicKeys, BoardContract.Commands.EndGame())
                this.verifies()
            }
            transaction {
                input(BoardContract.ID, boardState)
                output(BoardContract.ID, DummyState())
                command(publicKeys, BoardContract.Commands.EndGame())
                this `fails with` "There should be no output state."
            }
        }
    }

    @Test
    fun bothPlayersMustSignEndGameTransaction() {
        val partyC = TestIdentity(CordaX500Name("PartyC","New York","US")).party
        ledgerServices.ledger {
            transaction {
                command(listOf(partyA.owningKey, partyB.owningKey), BoardContract.Commands.EndGame())
                input(BoardContract.ID, boardState)
                this.verifies()
            }
            transaction {
                command(partyA.owningKey, BoardContract.Commands.EndGame())
                input(BoardContract.ID, boardState)
                this `fails with` "Both participants must sign a EndGame transaction."
            }
            transaction {
                command(partyC.owningKey, BoardContract.Commands.EndGame())
                input(BoardContract.ID, boardState)
                this `fails with` "Both participants must sign a EndGame transaction."
            }
            transaction {
                command(listOf(partyC.owningKey, partyA.owningKey, partyB.owningKey), BoardContract.Commands.EndGame())
                input(BoardContract.ID, boardState)
                this `fails with` "Both participants must sign a EndGame transaction."
            }
        }
    }

}