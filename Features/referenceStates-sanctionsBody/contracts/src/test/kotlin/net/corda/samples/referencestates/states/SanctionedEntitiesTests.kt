package net.corda.samples.referencestates.states

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import org.jgroups.util.Util
import org.junit.Test
import java.util.*

class SanctionedEntitiesTests {

    var a = TestIdentity(CordaX500Name("Alice", "", "GB"))
    var b = TestIdentity(CordaX500Name("Bob", "", "GB"))
    var c = TestIdentity(CordaX500Name("Charlie", "", "GB"))

    @Test
    fun constructorTest() {
        val badPeople = Arrays.asList(a.party)
        val issuer = b.party
        val st = SanctionedEntities(badPeople, b.party)
        Util.assertTrue(st is ContractState)
        Util.assertTrue(st is LinearState)
        Util.assertEquals(badPeople, st.badPeople)
        Util.assertEquals(issuer, st.issuer)
        Util.assertTrue(st.participants.contains(b.party))
        Util.assertFalse(st.participants.contains(a.party))
        Util.assertFalse(st.participants.contains(c.party))
    }
}
