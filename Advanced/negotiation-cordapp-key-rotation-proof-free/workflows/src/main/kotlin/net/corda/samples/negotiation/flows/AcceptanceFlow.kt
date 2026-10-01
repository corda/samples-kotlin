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
import net.corda.samples.negotiation.states.ProposalState
import net.corda.samples.negotiation.states.TradeState


object AcceptanceFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val proposalId: UniqueIdentifier) : FlowLogic<Unit>() {
        override val progressTracker = ProgressTracker()

        @Suspendable
        override fun call() {
            // Retrieving the input from the vault.
            val inputCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(proposalId))
            val inputStateAndRef = serviceHub.vaultService.queryBy<ProposalState>(inputCriteria).states.single()
            val input = inputStateAndRef.state.data

            // The parties are being resolved so that we can move way from using possible outdated keys from the input state
            // and instead use the most up-to-date keys when building the transaction.
            val resolver = PartyIdentityResolver(serviceHub.identityService)
            val buyerKeyResolution = resolver.resolve(input.buyer)
            val sellerKeyResolution = resolver.resolve(input.seller)
            val proposerKeyResolution = resolver.resolve(input.proposer)
            val proposeeKeyResolution = resolver.resolve(input.proposee)

            val proofMap = generateProofChainMap(buyerKeyResolution, sellerKeyResolution)
            if (proofMap == null) {
                logger.info("No proof.")
            } else {
                logger.info("One or more parties have rotated their keys, including the proof map in the transaction.")
            }

            // Creating the output.
            val output = TradeState(input.amount, buyerKeyResolution.originalOrCurrentParty, sellerKeyResolution.originalOrCurrentParty, input.linearId)

            // Creating the command.
            val requiredSigners = listOf(proposeeKeyResolution.getOwningKey(), proposerKeyResolution.getOwningKey())
            val command = Command(ProposalAndTradeContract.Commands.Accept(), requiredSigners, proofMap)

            // Building the transaction.
            val notary = inputStateAndRef.state.notary
            val txBuilder = TransactionBuilder(notary)
            txBuilder.addInputState(inputStateAndRef)
            txBuilder.addOutputState(output, ProposalAndTradeContract.ID)
            txBuilder.addCommand(command)

            // Signing the transaction ourselves.
            val partStx = serviceHub.signInitialTransaction(txBuilder)

            // Gathering the counterparty's signature.
            //
            // The identity returned by `getOurIdentity` cannot be compared directly with the proposer from the input state,
            // as the node may have rotated its keys since the proposal was created. The resolved party must be used instead.
            // The resolver will always be able to resolve the node's own identity because the proof will always be available for the node's own key.
            val counterparty = if (ourIdentity.equals(proposerKeyResolution.originalOrCurrentParty)) input.proposee else input.proposer


            // The counterparty might be an old key, but the session will be initiated with the most up-to-date identity.
            // No need to use the resolved party in this case.
            val counterpartySession = initiateFlow(counterparty)
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
                    val input = ledgerTx.inputsOfType<ProposalState>().single()

                    // The counterparty session always provides the most up-to-date identity for the counterparty.
                    //
                    // Therefore, any party retrieved from a state must be resolved using `resolveToCurrentParty`.
                    // While `resolveToCurrentParty` does not rely on a proof, it resolves the party to its latest valid identity.
                    //
                    // This ensures that equality checks behave as expected after key rotation.
                    val proposee = resolveToCurrentParty(input.proposee, serviceHub.identityService)
                    if (proposee != counterpartySession.counterparty) {
                        throw FlowException("Only the proposee can accept a proposal.")
                    }
                }
            }

            val txId = subFlow(signTransactionFlow).id

            subFlow(ReceiveFinalityFlow(counterpartySession, txId))
        }
    }
}
