package net.corda.samples.oracle.states

import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrimeStateTests {
    private val a = TestIdentity(CordaX500Name("Alice", "", "GB"))

    @Test
    fun constructorTest() {
        val st = PrimeState(1, 5, a.party)
        assertEquals(a.party, st.requester)
        assertTrue(st.participants.contains(a.party))
    }
}