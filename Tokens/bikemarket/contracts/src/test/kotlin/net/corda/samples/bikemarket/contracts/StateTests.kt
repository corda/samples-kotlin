package net.corda.samples.bikemarket.contracts

import net.corda.samples.bikemarket.states.WheelsTokenState
import net.corda.testing.node.MockServices
import org.junit.Assert.assertEquals
import org.junit.Test

class StateTests {
    private val ledgerServices = MockServices()

    //sample State tests
    @Test
    fun hasSerialNumFieldOfCorrectType() {
        // Does the message field exist?
        WheelsTokenState::class.java.getDeclaredField("serialNum")
        // Is the message field of the correct type?
        assertEquals(String::class.java, WheelsTokenState::class.java.getDeclaredField("serialNum").type)
    }
}