package net.corda.samples.autopayroll

import net.corda.core.identity.CordaX500Name
import net.corda.samples.autopayroll.contracts.MoneyStateContract
import net.corda.samples.autopayroll.states.MoneyState
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test

class ContractTests {
    private val ledgerServices = MockServices()
    private val partyA = TestIdentity(CordaX500Name(organisation = "Alice", locality = "TestLand", country = "US"))
    private val partyB = TestIdentity(CordaX500Name("Bob", "TestLand", "US"))

    @Test
    fun `No Negative PayCheck Value`() {
        val tokenPass = MoneyState(10, partyB.party)
        val tokenFail = MoneyState(-10, partyB.party)

        ledgerServices.ledger {
            transaction {
                output(MoneyStateContract.ID, tokenFail)
                command(partyA.publicKey, MoneyStateContract.Commands.Pay())
                this.fails()
            }
        }
        ledgerServices.ledger {
            transaction {
                output(MoneyStateContract.ID, tokenPass)
                command(partyA.publicKey, MoneyStateContract.Commands.Pay())
                this.verifies()
            }
        }
    }
}