package net.corda.samples.autopayroll

import net.corda.samples.autopayroll.states.MoneyState
import org.junit.Assert.assertEquals
import org.junit.Test

class StateTests {
    @Test
    fun hasFieldOfCorrectType() {
        // Does the message field exist?
        MoneyState::class.java.getDeclaredField("amount")
        // Is the message field of the correct type?
        assertEquals(MoneyState::class.java.getDeclaredField("amount").type, Int::class.java)
    }
}