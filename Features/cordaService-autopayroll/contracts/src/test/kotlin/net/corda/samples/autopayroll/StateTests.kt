package net.corda.samples.autopayroll

import groovy.util.GroovyTestCase.assertEquals
import net.corda.core.contracts.UniqueIdentifier
import net.corda.samples.autopayroll.states.MoneyState
import net.corda.testing.node.MockServices
import org.junit.Test

class StateTests {
    private val ledgerServices = MockServices()

    @Test
    fun hasFieldOfCorrectType() {
        // Does the message field exist?
        MoneyState::class.java.getDeclaredField("amount")
        // Is the message field of the correct type?
        assertEquals(MoneyState::class.java.getDeclaredField("amount").type, Int::class.java)
    }
}