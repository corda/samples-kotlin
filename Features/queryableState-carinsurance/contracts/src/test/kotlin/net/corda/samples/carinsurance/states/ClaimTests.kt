package net.corda.samples.carinsurance.states

import org.junit.Assert.assertEquals
import org.junit.Test

class ClaimTests {
    private val desc = "claim description: my car was hit by a blockchain"
    private val claimNumber = "B-132022"
    private val claimAmount = 3000

    @Test
    fun constructorTest() {
        val (claimNumber1, claimDescription, claimAmount1) = Claim(claimNumber, desc, claimAmount)
        assertEquals(claimNumber, claimNumber1)
        assertEquals(desc, claimDescription)
        assertEquals(claimAmount.toLong(), claimAmount1.toLong())
    }
}
