package net.corda.samples.observable.contracts

import net.corda.core.identity.Party
import net.corda.samples.observable.states.HighlyRegulatedState
import org.junit.Assert.assertSame
import org.junit.Test

class StateTests {
    @Test
    @Throws(NoSuchFieldException::class)
    fun hasFieldOfCorrectType() {
        // Does the message field exist?
        HighlyRegulatedState::class.java.getDeclaredField("buyer")
        assertSame(Party::class.java, HighlyRegulatedState::class.java.getDeclaredField("buyer").type)
    }
}
