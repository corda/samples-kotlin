package net.corda.samples.heartbeat.contracts

import net.corda.core.contracts.Contract
import net.corda.core.identity.CordaX500Name
import net.corda.samples.heartbeat.states.HeartState
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartContractTests {
    private val a = TestIdentity(CordaX500Name("Alice", "", "GB"))

    private val ledgerServices = MockServices(
            listOf("net.corda.samples.heartbeat.contracts")
    )
    private val st: HeartState = HeartState(a.party)

    @Test
    fun contractImplementsContract() {
        assertTrue(HeartContract() is Contract)
    }

    @Test
    fun contractRequiresSpecificCommand() {
        ledgerServices.ledger {
            transaction {
                // Has correct command type, will verify.
                output(HeartContract.contractID, st)
                command(listOf(a.publicKey), HeartContract.Commands.Beat())
                verifies()
            }
        }
    }
}
