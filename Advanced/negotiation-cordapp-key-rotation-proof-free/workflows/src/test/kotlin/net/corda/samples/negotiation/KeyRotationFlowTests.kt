package net.corda.samples.negotiation

import net.corda.core.crypto.keyrotation.crossprovider.KeyRotationProofChain
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.samples.negotiation.states.ProposalState
import net.corda.samples.negotiation.states.TradeState
import org.junit.Test
import java.security.PublicKey
import java.util.SortedMap
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The CorDapp keeps working after Node A rotates its key across providers (Corda Enterprise).
 *
 * A rotation proof is only needed the first time a state that names the OLD key is consumed, and where it travels depends on
 * who starts that transaction:
 *
 * - If the node that rotated starts it, it replaces its old key by the new one in the output and puts the proof in the
 *   command (see `generateProofChainMap` in `ModificationFlow`).
 * - If the counterparty starts it, it does not know the rotation yet, so the output keeps the old key and the node that
 *   rotated proves the rotation in its signature instead. From then on the counterparty knows the proof too.
 *
 * Once every state names only current keys, no proof appears anywhere and the flows run exactly as before the rotation.
 *
 * In this sample only the proposee can modify or accept a proposal, and whoever modifies becomes the new proposer, so the two
 * tests below start from a proposal made by the node that must NOT start the first transaction after the rotation.
 */
class KeyRotationFlowTests : KeyRotationTestBase() {

    @Test
    fun nodeARotatesAndNodeAStartsTheTransaction() {
        val partyAOriginal = identityOf(a)
        val partyB = identityOf(b)

        // Node B proposes to Node A.
        val proposalId = propose(b, 1, partyAOriginal)
        assertProposal(proposalOf(a, proposalId), partyB, partyAOriginal, partyB, partyAOriginal, 1)
        assertProposal(proposalOf(b, proposalId), partyB, partyAOriginal, partyB, partyAOriginal, 1)

        a = rotate(a)
        val partyARotated = identityOf(a)

        // 1. Node A modifies.
        val modifiedByA = modify(a, proposalId, 2)
        assertProposal(outputOf(modifiedByA), partyB, partyARotated, partyARotated, partyB, 2)
        assertProofInCommand(modifiedByA, partyAOriginal, partyARotated)
        assertNoProofInSignatures(modifiedByA)

        // 2. Node B modifies.
        assertNoProof(modify(b, proposalId, 3))

        // 3. Node A modifies again: still no proof.
        assertNoProof(modify(a, proposalId, 4))

        // 4. Node B accepts
        val accepted = accept(b, proposalId)
        assertNoProof(accepted)

        val trade = accepted.coreTransaction.outputStates.single() as TradeState
        assertEquals(partyARotated, trade.seller)
        assertEquals(partyB, trade.buyer)
    }

    @Test
    fun nodeARotatesAndNodeBStartsTheTransaction() {
        val partyAOriginal = identityOf(a)
        val partyB = identityOf(b)

        // Node A proposes to Node B.
        val proposalId = propose(a, 1, partyB)
        assertProposal(proposalOf(a, proposalId), partyAOriginal, partyB, partyAOriginal, partyB, 1)
        assertProposal(proposalOf(b, proposalId), partyAOriginal, partyB, partyAOriginal, partyB, 1)

        a = rotate(a)
        val partyARotated = identityOf(a)

        // 1. Node B modifies.
        val modifiedByB = modify(b, proposalId, 2)
        assertProposal(outputOf(modifiedByB), partyAOriginal, partyB, partyB, partyAOriginal, 2)
        assertNull(commandProofMap(modifiedByB))
        assertProofInSignature(modifiedByB, partyAOriginal, partyARotated)

        // 2. Node A modifies.
        val modifiedByA = modify(a, proposalId, 3)
        assertProposal(outputOf(modifiedByA), partyARotated, partyB, partyARotated, partyB, 3)
        assertProofInCommand(modifiedByA, partyAOriginal, partyARotated)
        assertNoProofInSignatures(modifiedByA)

        // 3. Node B modifies again: still no proof.
        assertNoProof(modify(b, proposalId, 4))

        // 4. Node A accepts
        val accepted = accept(a, proposalId)
        assertNoProof(accepted)

        val trade = accepted.coreTransaction.outputStates.single() as TradeState
        assertEquals(partyARotated, trade.buyer)
        assertEquals(partyB, trade.seller)
    }

    // --- Assertions --------------------------------------------------------------------------------------------------------

    private fun outputOf(stx: SignedTransaction): ProposalState = stx.coreTransaction.outputStates.single() as ProposalState

    private fun assertProposal(proposal: ProposalState, buyer: Party, seller: Party, proposer: Party, proposee: Party, amount: Int) {
        assertEquals(buyer, proposal.buyer, "buyer")
        assertEquals(seller, proposal.seller, "seller")
        assertEquals(proposer, proposal.proposer, "proposer")
        assertEquals(proposee, proposal.proposee, "proposee")
        assertEquals(amount, proposal.amount, "amount")
    }

    private fun commandProofMap(stx: SignedTransaction): SortedMap<PublicKey, KeyRotationProofChain>? =
            stx.tx.commands.single().keyRotationProofChainMap

    /** The command's proof map links `before`'s key to `after`'s key. */
    private fun assertProofInCommand(stx: SignedTransaction, before: Party, after: Party) {
        val proofMap = assertNotNull(commandProofMap(stx), "The command should carry a proof map")
        val chain = assertNotNull(proofMap[before.owningKey], "The proof map should have a chain for the old key")
        assertTrue(chain.isValid(before.owningKey, after.owningKey), "The chain should link the old key to the new key")
    }

    /** The signature made with `after`'s key carries a proof chain linking `before`'s key to it. */
    private fun assertProofInSignature(stx: SignedTransaction, before: Party, after: Party) {
        val signature = stx.sigs.singleOrNull { it.by == after.owningKey }
                ?: fail("No signature by ${after.name}'s new key")
        val chain = assertNotNull(signature.signatureMetadata.proofChain, "The signature by the new key should carry a proof chain")
        assertTrue(chain.isValid(before.owningKey, after.owningKey), "The chain should link the old key to the new key")
    }

    private fun assertNoProofInSignatures(stx: SignedTransaction) {
        stx.sigs.forEach { signature ->
            val chain = signature.signatureMetadata.proofChain
            assertTrue(chain == null || chain.isEmpty(), "No signature should carry a proof chain")
        }
    }

    /** Neither the command nor any signature carries a proof: the transaction looks exactly like one without any rotation. */
    private fun assertNoProof(stx: SignedTransaction) {
        assertNull(commandProofMap(stx), "The command should not carry a proof map")
        assertNoProofInSignatures(stx)
    }
}
