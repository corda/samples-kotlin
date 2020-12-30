package net.corda.samples.referencestates.states

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import org.jgroups.util.Util
import org.junit.Test

class SanctionableIOUStateTests {
    var a = TestIdentity(CordaX500Name("Alice", "", "GB"))
    var b = TestIdentity(CordaX500Name("Bob", "", "GB"))

    @Test
    fun constructorTest() {
        val value = 50
        val lender = a.party
        val borrower = b.party
        val uid = UniqueIdentifier()
        val st = SanctionableIOUState(value, lender, borrower, uid)
        Util.assertTrue(st is ContractState)
        Util.assertTrue(st is LinearState)
        Util.assertEquals(value, st.value)
        Util.assertEquals(lender, st.lender)
        Util.assertEquals(borrower, st.borrower)
        Util.assertEquals(uid, st.linearId)
        Util.assertTrue(st.participants.contains(a.party))
        Util.assertTrue(st.participants.contains(b.party))
    }
}
