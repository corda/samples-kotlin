package net.corda.samples.solanadvp.contracts

import com.r3.corda.lib.tokens.contracts.commands.Create
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.samples.solanadvp.states.DeliveryState
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test

class ContractTests {
    private val ledgerServices = MockServices()
    val operator = TestIdentity(CordaX500Name(organisation = "Alice", locality = "TestLand", country = "US"))

    //sample tests
    @Test
    fun `Price must be greater than zero`() {
        val tokenPass = DeliveryState(
            UniqueIdentifier(),
            listOf(operator.party),
            Amount.parseCurrency("1000 USD")
        )
        val tokenFail = DeliveryState(
            UniqueIdentifier(),
            listOf(operator.party),
            Amount.parseCurrency("0 USD")
        )
        ledgerServices.ledger {
            transaction {
                output(DeliveryContract.CONTRACT_ID, tokenFail)
                command(operator.publicKey, Create())
                this.fails()
            }
        }
        ledgerServices.ledger {
            transaction {
                output(DeliveryContract.CONTRACT_ID, tokenPass)
                command(operator.publicKey, Create())
                this.verifies()
            }
        }
    }
}