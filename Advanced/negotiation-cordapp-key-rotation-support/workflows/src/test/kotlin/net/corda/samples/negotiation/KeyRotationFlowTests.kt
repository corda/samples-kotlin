package net.corda.samples.negotiation

import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.samples.negotiation.states.ProposalState
import net.corda.samples.negotiation.states.TradeState
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The CorDapp keeps working after Node A rotates its key across providers (Corda Enterprise).
 *
 * This sample never replaces a key in a state: the proposal and the trade keep naming Node A by its original key, and the
 * flows do not put a proof map in the command. Every transaction that consumes such a state must be signed for the original
 * key, so Node A signs with its rotated key and carries the rotation proof in the signature, each time. Node B never has to
 * know about the rotation beforehand: the proof travels with the signature and the flows run exactly as before the rotation.
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
        assertProposal(outputOf(modifiedByA), partyB, partyAOriginal, partyAOriginal, partyB, 2)
        assertRotationProof(modifiedByA, partyAOriginal, partyARotated)

        // 2. Node B modifies.
        val modifiedByB = modify(b, proposalId, 3)
        assertProposal(outputOf(modifiedByB), partyB, partyAOriginal, partyB, partyAOriginal, 3)
        assertRotationProof(modifiedByB, partyAOriginal, partyARotated)

        // 3. Node A modifies again.
        assertRotationProof(modify(a, proposalId, 4), partyAOriginal, partyARotated)

        // 4. Node B accepts
        val accepted = accept(b, proposalId)
        assertRotationProof(accepted, partyAOriginal, partyARotated)

        val trade = accepted.coreTransaction.outputStates.single() as TradeState
        assertEquals(partyAOriginal, trade.seller)
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
        assertRotationProof(modifiedByB, partyAOriginal, partyARotated)

        // 2. Node A modifies.
        val modifiedByA = modify(a, proposalId, 3)
        assertProposal(outputOf(modifiedByA), partyAOriginal, partyB, partyAOriginal, partyB, 3)
        assertRotationProof(modifiedByA, partyAOriginal, partyARotated)

        // 3. Node B modifies again.
        assertRotationProof(modify(b, proposalId, 4), partyAOriginal, partyARotated)

        // 4. Node A accepts
        val accepted = accept(a, proposalId)
        assertRotationProof(accepted, partyAOriginal, partyARotated)

        val trade = accepted.coreTransaction.outputStates.single() as TradeState
        assertEquals(partyAOriginal, trade.buyer)
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

    /**
     * The rotation is proven in the transaction's signatures only: the command carries no proof map, the signature made with
     * `rotated`'s key carries a proof chain linking `original`'s key to it, and no other signature carries a proof.
     */
    private fun assertRotationProof(stx: SignedTransaction, original: Party, rotated: Party) {
        assertNull(stx.tx.commands.single().keyRotationProofChainMap, "The command should not carry a proof map")

        var signedByRotatedKey = false
        stx.sigs.forEach { signature ->
            val chain = signature.signatureMetadata.proofChain
            if (signature.by == rotated.owningKey) {
                signedByRotatedKey = true
                assertNotNull(chain, "The signature by the rotated key should carry a proof chain")
                assertTrue(chain.isValid(original.owningKey, rotated.owningKey), "The chain should link the original key to the rotated key")
            } else {
                assertTrue(chain == null || chain.isEmpty(), "Only the rotated key's signature should carry a proof chain")
            }
            assertFalse(signature.by == original.owningKey, "The original key must not sign any more")
        }
        assertTrue(signedByRotatedKey, "The transaction should be signed by ${rotated.name}'s rotated key")
    }
}
