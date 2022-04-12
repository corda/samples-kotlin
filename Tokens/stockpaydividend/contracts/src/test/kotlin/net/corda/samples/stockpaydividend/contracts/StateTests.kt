package net.corda.samples.stockpaydividend.contracts

import net.corda.samples.stockpaydividend.states.StockState
import org.junit.Assert.assertEquals
import org.junit.Test

class StateTests {
    //sample State tests
    @Test
    @Throws(NoSuchFieldException::class)
    fun hasConstructionAreaFieldOfCorrectType() {
        // Does the message field exist?
        StockState::class.java.getDeclaredField("symbol")
        assertEquals(String::class.java, StockState::class.java.getDeclaredField("symbol").type)
    }
}