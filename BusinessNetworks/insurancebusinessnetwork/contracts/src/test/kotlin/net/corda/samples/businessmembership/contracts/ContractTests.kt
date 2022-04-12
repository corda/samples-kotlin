package net.corda.samples.businessmembership.contracts

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.samples.businessmembership.states.InsuranceState
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test

class ContractTests {
    private val ledgerServices = MockServices(listOf("net.corda.samples.businessmembership.contracts"))
    private val alice = TestIdentity(CordaX500Name("Alice", "TestLand", "US"))
    private val bob = TestIdentity(CordaX500Name("Bob", "TestLand", "US"))

    @Test
    fun failsDueToParticipantsAreNotNetworkMembers() {
        val insurancestate = InsuranceState(alice.party, "TEST", bob.party, UniqueIdentifier().toString(), "Initiating Policy")
        ledgerServices.ledger {
            transaction {
                output(InsuranceStateContract.CONTRACT_NAME,insurancestate)
                command(alice.publicKey, InsuranceStateContract.Commands.Issue())
                this.fails()
            }
        }
    }
}
