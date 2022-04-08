package net.corda.samples.heartbeat.states

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.SchedulableState
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartStateTests {
    private val a = TestIdentity(CordaX500Name("Alice", "", "GB"))
    private val b = TestIdentity(CordaX500Name("Bob", "", "GB"))

    @Test
    fun constructorTest() {
        val st = HeartState(a.party)
        assertTrue(st.participants.contains(a.party))
        assertFalse(st.participants.contains(b.party))
    }

    @Test
    fun stateImplementsContractStateTest() {
        val st = HeartState(a.party)
        assertTrue(st is ContractState)
        assertTrue(st is SchedulableState)
    }
}
