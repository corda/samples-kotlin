package net.corda.samples.supplychain.contracts

import net.corda.samples.supplychain.states.InvoiceState
import org.junit.Assert.assertEquals
import org.junit.Test

class StateTests {
    @Test
    fun hasMessageFieldOfCorrectType() {
        // Does the message field exist?
        InvoiceState::class.java.getDeclaredField("amount")
        // Is the message field of the correct type?
        assertEquals(InvoiceState::class.java.getDeclaredField("amount").type, Int::class.java)
    }
}
