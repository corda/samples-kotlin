package net.corda.samples.notarychange.contracts

import net.corda.samples.notarychange.states.IOUState
import org.junit.Assert.assertSame
import org.junit.Test

class StateTests {
    @Test
    @Throws(NoSuchFieldException::class)
    fun hasAmountFieldOfCorrectType() {
        // Does the message field exist?
        IOUState::class.java.getDeclaredField("value")
        assertSame(IOUState::class.java.getDeclaredField("value").type, Int::class.javaPrimitiveType)
    }
}