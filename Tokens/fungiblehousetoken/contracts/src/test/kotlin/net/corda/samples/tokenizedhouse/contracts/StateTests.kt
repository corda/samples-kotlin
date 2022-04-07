package net.corda.samples.tokenizedhouse.contracts

import net.corda.samples.tokenizedhouse.states.FungibleHouseTokenState
import org.junit.Assert.assertEquals
import org.junit.Test


class StateTests {
    //sample State tests
    @Test
    @Throws(NoSuchFieldException::class)
    fun hasConstructionAreaFieldOfCorrectType() {
        // Does the message field exist?
        FungibleHouseTokenState::class.java.getDeclaredField("symbol")
        assertEquals(String::class.java, FungibleHouseTokenState::class.java.getDeclaredField("symbol").type)
    }
}