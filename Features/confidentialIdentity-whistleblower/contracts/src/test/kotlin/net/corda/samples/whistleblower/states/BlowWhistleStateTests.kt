package net.corda.samples.whistleblower.states

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import org.jgroups.util.Util
import org.junit.Test


class BlowWhistleStateTests {
    private val a = TestIdentity(CordaX500Name("alice", "", "GB"))
    private val b = TestIdentity(CordaX500Name("bob", "", "GB"))
    private val c = TestIdentity(CordaX500Name("bad corp", "", "GB"))

    @Test
    fun constructorTest() {

        // here, c is the bad corporation, a is the Whistleblower, and b is the investigator
        val st = BlowWhistleState(c.party, a.party.anonymise(), b.party.anonymise())
        Util.assertEquals(a.party, st.whistleBlower)
        Util.assertEquals(c.party, st.badCompany)
        Util.assertEquals(b.party, st.investigator)
    }

    @Test
    fun stateImplementTests() {
        val st = BlowWhistleState(c.party, a.party.anonymise(), b.party.anonymise())
        Util.assertTrue(st is ContractState)
        Util.assertTrue(st is LinearState)
        Util.assertTrue(st.linearId is UniqueIdentifier)
    }
}
