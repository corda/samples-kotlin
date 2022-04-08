package net.corda.samples.referencestates.states

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SanctionableIOUStateTests {
    private val a = TestIdentity(CordaX500Name("Alice", "", "GB"))
    private val b = TestIdentity(CordaX500Name("Bob", "", "GB"))

    @Test
    fun constructorTest() {
        val value = 50
        val lender = a.party
        val borrower = b.party
        val uid = UniqueIdentifier()
        val st = SanctionableIOUState(value, lender, borrower, uid)
        assertTrue(st is ContractState)
        assertTrue(st is LinearState)
        assertEquals(value, st.value)
        assertEquals(lender, st.lender)
        assertEquals(borrower, st.borrower)
        assertEquals(uid, st.linearId)
        assertTrue(st.participants.contains(a.party))
        assertTrue(st.participants.contains(b.party))
    }
}
