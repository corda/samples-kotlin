package net.corda.samples.referencestates.states

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SanctionedEntitiesTests {

    private val  a = TestIdentity(CordaX500Name("Alice", "", "GB"))
    private val b = TestIdentity(CordaX500Name("Bob", "", "GB"))
    private val c = TestIdentity(CordaX500Name("Charlie", "", "GB"))

    @Test
    fun constructorTest() {
        val badPeople = listOf(a.party)
        val issuer = b.party
        val st = SanctionedEntities(badPeople, b.party)
        assertTrue(st is ContractState)
        assertTrue(st is LinearState)
        assertEquals(badPeople, st.badPeople)
        assertEquals(issuer, st.issuer)
        assertTrue(st.participants.contains(b.party))
        assertFalse(st.participants.contains(a.party))
        assertFalse(st.participants.contains(c.party))
    }
}
