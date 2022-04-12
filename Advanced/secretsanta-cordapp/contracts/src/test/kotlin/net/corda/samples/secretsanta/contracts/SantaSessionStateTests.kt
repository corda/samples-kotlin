package net.corda.samples.secretsanta.contracts

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.identity.CordaX500Name
import net.corda.samples.secretsanta.states.SantaSessionState
import net.corda.testing.core.TestIdentity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.*

class SantaSessionStateTests {
    private val playerNames: MutableList<String?> = ArrayList()
    private val playerEmails: MutableList<String> = ArrayList()
    private val santa = TestIdentity(CordaX500Name("Santa", "", "GB")).party
    private val elf = TestIdentity(CordaX500Name("Elf", "", "GB")).party

    @Before
    fun setup() {
        playerNames.add("david")
        playerNames.add("alice")
        playerNames.add("bob")
        playerNames.add("charlie")
        playerNames.add("olivia")
        playerNames.add("peter")
        playerEmails.add("david@corda.net")
        playerEmails.add("alice@corda.net")
        playerEmails.add("bob@corda.net")
        playerEmails.add("charlie@corda.net")
        playerEmails.add("olivia@corda.net")
        playerEmails.add("peter@corda.net")
    }

    @After
    fun tearDown() { // pass
    }

    // unmatching names
    @Test(expected = IllegalArgumentException::class)
    fun unmatchingNameTest() {
        val badNames = listOf("alice", "bob", "charlie", "olivia", "peter")
        val goodEmails = listOf("david@corda.net", "alice@corda.net", "bob@corda.net", "charlie@corda.net", "olivia@corda.net", "peter@corda.net")
        val st = SantaSessionState(badNames, goodEmails, santa, elf)
    }

    // few name test
    @Test(expected = IllegalArgumentException::class)
    fun fewNameTest() { // here there are too few names
        val badNames = listOf("peter")
        val goodEmails = listOf("david@corda.net", "alice@corda.net", "bob@corda.net", "charlie@corda.net", "olivia@corda.net", "peter@corda.net")
        val st = SantaSessionState(badNames, goodEmails, santa, elf)
    }

    // unmatching emails
    @Test(expected = IllegalArgumentException::class)
    fun unmatchingEmailTest() { // note there's no matching email for david, david@corda.net
        val goodNames = listOf("david", "alice", "bob", "charlie", "olivia", "peter")
        val badEmails = listOf("alice@corda.net", "bob@corda.net", "charlie@corda.net", "olivia@corda.net", "peter@corda.net")
        val st = SantaSessionState(goodNames, badEmails, santa, elf)
    }

    // too few emails
    @Test(expected = IllegalArgumentException::class)
    fun fewEmailTest() {
        val goodNames = listOf("david", "alice", "bob", "charlie", "olivia", "peter")
        val badEmails = listOf("peter@corda.net")
        val st = SantaSessionState(goodNames, badEmails, santa, elf)
    }

    @Test(expected = IllegalArgumentException::class)
    fun tooFewPairingsTest() {
        val st = SantaSessionState(playerNames, playerEmails, santa, elf)
        val assignments = LinkedHashMap<String?, String?>()
        assignments["david"] = "alice"
        assignments["alice"] = "bob"
        assignments["bob"] = "charlie"
        assignments["charlie"] = "olivia"
        assignments["olivia"] = "david"
        // note peter would be the "odd man out" and not have a valid assignment
// assignments.put("peter", "peter");
        st.setAssignments(assignments)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidPairingsTest() {
        val st = SantaSessionState(playerNames, playerEmails, santa, elf)
        val assignments = LinkedHashMap<String?, String?>()
        assignments["david"] = "alice"
        assignments["alice"] = "bob"
        assignments["bob"] = "charlie"
        assignments["charlie"] = "olivia"
        assignments["olivia"] = "david"
        // note peter would be the "odd man out" and not have a valid assignment
        assignments["peter"] = "peter"
        st.setAssignments(assignments)
    }

    @Test
    fun stateGetters() {
        val st = SantaSessionState(playerNames, playerEmails, santa, elf)
        assertEquals(santa, st.issuer)
        assertEquals(playerNames, st.playerNames)
        assertEquals(playerEmails, st.playerEmails)
        assertTrue(st.playerNames.contains("olivia"))
        assertTrue(st.playerNames.contains("peter"))
        assertTrue(st.playerEmails.contains("olivia@corda.net"))
        assertTrue(st.playerEmails.contains("peter@corda.net"))
        assertNotEquals(st.getAssignments()!!["david"], st.getAssignments()!!["peter"])
    }

    @Test
    fun stateImplementsContractState() {
        val st = SantaSessionState(playerNames, playerEmails, santa, elf)
        assertTrue(st is ContractState)
        assertTrue(st is LinearState)
    }

    @Test
    fun stateHasOneParticipant() {
        val st = SantaSessionState(playerNames, playerEmails, santa, elf)
        assertEquals(2, st.participants.size)
        assertTrue(st.participants.contains(santa))
        assertTrue(st.participants.contains(elf))
    }

    @Test
    fun stateProducesValidAssignments() {
        val st = SantaSessionState(playerNames, playerEmails, santa, elf)
        val assignments: HashMap<String?, String?>? = st.getAssignments()
        // correct number of assignments
        assertEquals(playerNames.size, assignments!!.size)
        // iterate through assignments for validity
        for (santa_candidate in playerNames) { // ensure all these players actually exist
            assertTrue(playerNames.contains(santa_candidate))
            for (target_candidate in playerNames) { // skip duplicates in iteration
                if (santa_candidate == target_candidate) {
                    continue
                }
                // ensure no one is assigned themselves
                assertNotEquals(santa_candidate, assignments[santa_candidate])
            }
        }
    }
}
