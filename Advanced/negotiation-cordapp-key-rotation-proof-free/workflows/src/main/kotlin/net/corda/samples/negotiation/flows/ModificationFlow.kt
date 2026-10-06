package net.corda.samples.negotiation.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.keyrotation.crossprovider.PartyIdentityResolver
import net.corda.core.crypto.keyrotation.crossprovider.PartyIdentityResolver.Companion.generateProofChainMap
import net.corda.core.crypto.keyrotation.crossprovider.PartyIdentityResolver.Companion.resolveToCurrentParty
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.samples.negotiation.contracts.ProposalAndTradeContract
import net.corda.samples.negotiation.contracts.ProposalAndTradeContract.Commands.Modify
import net.corda.samples.negotiation.states.ProposalState


object ModificationFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val proposalId: UniqueIdentifier, val newAmount: Int) : FlowLogic<Unit>() {
        override val progressTracker = ProgressTracker()

        @Suspendable
        override fun call() {
            // Retrieving the input from the vault.
            val inputCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(proposalId))
            val inputStateAndRef = serviceHub.vaultService.queryBy<ProposalState>(inputCriteria).states.single()
            val input = inputStateAndRef.state.data

            // N.B.: Any party data retrieved from a state must be resolved using an instance of PartyIdentityResolver.
            //
            // `PartyIdentityResolver` checks whether the node has an associated proof for the given party.
            // If a proof is available, it resolves the party to the most recent valid identity permitted by that proof.
            //
            // Note that a key rotation proof is not immediately available after rotation. It typically becomes available
            // only after at least one transaction has been processed and the proof has been propagated to the Identity Service
            // of the node that performed the rotation.
            //
            // Therefore, immediately after a key rotation, the resolver may still return a valid but non-updated identity.
            // Once the proof becomes available, subsequent resolutions will return the updated (new-key) identity.
            //
            // The Identity Service will always contain the key rotation proofs for its own node.
            val resolver = PartyIdentityResolver(serviceHub.identityService)
            val buyerKeyResolution = resolver.resolve(input.buyer)
            val sellerKeyResolution = resolver.resolve(input.seller)
            val proposerKeyResolution = resolver.resolve(input.proposer)
            val proposeeKeyResolution = resolver.resolve(input.proposee)


            // A proof map must be included in the transaction command if any of the parties have rotated their keys,
            // and the input states contain the old identity while the output states contain the new identity.
            //
            // This is required because the contract must be able to verify the proof chains for all parties that have
            // undergone key rotation, and therefore must have access to the full proof history.
            //
            // All resolved parties are passed to the `generateProofChainMap` method, which determines whether there are
            // differences between the original and current identities. If differences are detected, it generates the
            // required proof chains and returns a map of new keys to their corresponding proof chains. If no differences
            // are found, an empty map is returned.
            //
            // In this example, only the buyer and seller are passed to `generateProofChainMap`, since the proposer and
            // proposee are always either the buyer or the seller.
            val proofMap = generateProofChainMap(buyerKeyResolution, sellerKeyResolution)
            if (proofMap == null) {
                logger.info("No proof.")
            } else {
                logger.info("One or more parties have rotated their keys, including the proof map in the transaction.")
            }


            // The `getOurIdentity` method always returns the most up-to-date identity for the node.
            //
            // In this context, it is safe to use the resolved party because if the node has performed a key rotation,
            // the resolver will return the latest valid identity. Therefore, it is safe to compare the identity returned
            // by `getOurIdentity` with the resolved identity.
            //
            // Never compare the identity returned by `getOurIdentity` with the original party stored in the state,
            // as that party may be outdated due to key rotation, and the resolver may return a different current identity.
            val ourIdentityFromInput = if (ourIdentity == proposerKeyResolution.originalOrCurrentParty) {
                    proposerKeyResolution.originalOrCurrentParty
                } else {
                    proposeeKeyResolution.originalOrCurrentParty
                }
            val counterpartyFromInput = if (ourIdentity == proposerKeyResolution.originalOrCurrentParty) {
                proposeeKeyResolution.originalOrCurrentParty
            } else {
                proposerKeyResolution.originalOrCurrentParty
            }


            // Creating the output using the newest identity provided by the resolver.
            //
            // This ensures that any outdated keys are replaced in the output state where possible.
            // If no updated identity is available, the existing (old) key is retained.
            //
            // Always use resolved parties when constructing the output state, as a key rotation proof
            // must be included in the transaction if any party identity has been updated.
            //
            // This guarantees that the transaction remains verifiable after key rotation.
            val output = ProposalState(
                amount = newAmount,
                buyer = buyerKeyResolution.originalOrCurrentParty,
                seller = sellerKeyResolution.originalOrCurrentParty,
                proposer = ourIdentityFromInput,
                proposee = counterpartyFromInput,
                linearId = input.linearId
            )

            // Creating a command that includes the required signers and the proof map as part of the command data.
            //
            // Use resolved parties to determine the required signers, as the contract verifies signatures against resolved identities.
            // Using original state parties may lead to signature verification failures if those parties have undergone key rotation.
            // Consistency is required: the same resolved identities must be used both for determining required signers and for
            // constructing the output state. Old and new keys for a given node must not be mixed.
            //
            // The included proofs will be accessible to the contract during transaction verification via the command.
            val requiredSigners = listOf(proposeeKeyResolution.getOriginalKey(), proposerKeyResolution.getOwningKey())
            val command = Command(Modify(), requiredSigners, proofMap)

            // Building the transaction.
            val notary = inputStateAndRef.state.notary
            val txBuilder = TransactionBuilder(notary)
            txBuilder.addInputState(inputStateAndRef)
            txBuilder.addOutputState(output, ProposalAndTradeContract.ID)
            txBuilder.addCommand(command)

            // Signing the transaction ourselves.
            val partStx = serviceHub.signInitialTransaction(txBuilder)

            // Gathering the counterparty's signatures
            // Note that the counterparty may have also rotated their keys. The initiateFlow will automatically resolve the counterparty's
            // identity and select the correct key to use for the session, so we can simply pass in the counterparty as retrieved from the resolver.
            val counterpartySession = initiateFlow(counterpartyFromInput)
            val fullyStx = subFlow(CollectSignaturesFlow(partStx, listOf(counterpartySession)))

            // Finalising the transaction.
            subFlow(FinalityFlow(fullyStx, listOf(counterpartySession)))
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val signTransactionFlow = object : SignTransactionFlow(counterpartySession) {
                override fun checkTransaction(stx: SignedTransaction) {
                    val ledgerTx = stx.toLedgerTransaction(serviceHub, false)
                    val input: ProposalState = ledgerTx.inputsOfType<ProposalState>()[0]!!

                    // The counterparty session always provides the most up-to-date identity for the counterparty.
                    //
                    // Therefore, any party retrieved from a state must be resolved using `resolveToCurrentParty`.
                    // While `resolveToCurrentParty` does not rely on a proof, it resolves the party to its latest valid identity.
                    //
                    // This ensures that equality checks behave as expected after key rotation.
                    val proposee = resolveToCurrentParty(input.proposee, serviceHub.identityService)
                    if (proposee != counterpartySession.counterparty) {
                        throw FlowException("Only the proposee can modify a proposal.")
                    }
                }
            }

            val txId = subFlow(signTransactionFlow).id

            subFlow(ReceiveFinalityFlow(counterpartySession, txId))
        }
    }
}