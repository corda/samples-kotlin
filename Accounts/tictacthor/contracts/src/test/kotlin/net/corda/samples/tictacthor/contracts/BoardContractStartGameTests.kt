package net.corda.samples.tictacthor.contracts

import com.template.states.BoardState
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

class BoardContractStartGameTests {
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
        publicKeys = boardState.participants.map {it.owningKey}
    }

    @Test
    fun mustIncludeStartGameCommand() {
        ledgerServices.ledger {
            transaction {
                output(BoardContract.ID, boardState)
                command(publicKeys, BoardContract.Commands.StartGame())
                this.verifies()
            }
            transaction {
                output(BoardContract.ID, boardState)
                command(publicKeys, DummyCommand())
                this.fails()
            }
        }
    }

    @Test
    fun startGameTransactionMustHaveNoInputs() {
        ledgerServices.ledger {
            transaction {
                output(BoardContract.ID, boardState)
                command(publicKeys, BoardContract.Commands.StartGame())
                this.verifies()
            }
            transaction {
                input(BoardContract.ID, DummyState())
                output(BoardContract.ID, boardState)
                command(publicKeys, BoardContract.Commands.StartGame())
                this `fails with` "There should be no input state."
            }
        }
    }

    @Test
    fun startGameTransactionMustHaveOneOutput() {
        ledgerServices.ledger {
            transaction {
                output(BoardContract.ID, boardState)
                command(publicKeys, BoardContract.Commands.StartGame())
                this.verifies()
            }
            transaction {
                output(BoardContract.ID, boardState)
                output(BoardContract.ID, DummyState())
                command(publicKeys, BoardContract.Commands.StartGame())
                this `fails with` "There should be one output state."
            }
        }
    }

    @Test
    fun cannotStartGameWithYourself() {
        val boardStateSameParty = BoardState(partyA, partyA)
        ledgerServices.ledger {
            transaction {
                output(BoardContract.ID, boardState)
                command(listOf(partyA.owningKey, partyB.owningKey), BoardContract.Commands.StartGame())
                this.verifies()
            }
            transaction {
                output(BoardContract.ID, boardStateSameParty)
                command(listOf(partyA.owningKey, partyA.owningKey), BoardContract.Commands.StartGame())
                this `fails with` "You cannot play a game with yourself."
            }
        }
    }

    @Test
    fun bothPlayersMustSignStartGameTransaction() {
        val partyC = TestIdentity(CordaX500Name("PartyC","New York","US")).party
        ledgerServices.ledger {
            transaction {
                command(listOf(partyA.owningKey, partyB.owningKey), BoardContract.Commands.StartGame())
                output(BoardContract.ID, boardState)
                this.verifies()
            }
            transaction {
                command(partyA.owningKey, BoardContract.Commands.StartGame())
                output(BoardContract.ID, boardState)
                this `fails with` "Both participants must sign a StartGame transaction."
            }
            transaction {
                command(partyC.owningKey, BoardContract.Commands.StartGame())
                output(BoardContract.ID, boardState)
                this `fails with` "Both participants must sign a StartGame transaction."
            }
            transaction {
                command(listOf(partyC.owningKey, partyA.owningKey, partyB.owningKey), BoardContract.Commands.StartGame())
                output(BoardContract.ID, boardState)
                this `fails with` "Both participants must sign a StartGame transaction."
            }
        }
    }
}