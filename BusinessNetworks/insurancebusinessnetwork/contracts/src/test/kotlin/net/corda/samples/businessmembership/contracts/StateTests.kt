package net.corda.samples.businessmembership.contracts

import net.corda.core.identity.Party
import net.corda.samples.businessmembership.states.InsuranceState
import org.junit.Assert.assertEquals
import org.junit.Test

class StateTests {
    @Test
    fun hasFieldOfCorrectType() {
        // Does the message field exist?
        InsuranceState::class.java.getDeclaredField("insurer")
        // Is the message field of the correct type?
        assertEquals(InsuranceState::class.java.getDeclaredField("insurer").type, Party::class.java)
    }
}
