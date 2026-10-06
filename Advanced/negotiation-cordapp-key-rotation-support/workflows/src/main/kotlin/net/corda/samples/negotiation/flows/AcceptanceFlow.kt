package net.corda.samples.negotiation.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.keyrotation.crossprovider.PartyIdentityResolver
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
    class Initiator(val proposalId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {
        override val progressTracker = ProgressTracker()

        @Suspendable
        override fun call(): SignedTransaction {
            // Retrieving the input from the vault.
            val inputCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(proposalId))
            val inputStateAndRef = serviceHub.vaultService.queryBy<ProposalState>(inputCriteria).states.single()
            val input = inputStateAndRef.state.data

            // Creating the output.
            val output = TradeState(input.amount, input.buyer, input.seller, input.linearId)

            // Creating the command.
            val requiredSigners = listOf(input.proposer.owningKey, input.proposee.owningKey)
            val command = Command(ProposalAndTradeContract.Commands.Accept(), requiredSigners)

            // Building the transaction.
            val notary = inputStateAndRef.state.notary
            val txBuilder = TransactionBuilder(notary)
            txBuilder.addInputState(inputStateAndRef)
            txBuilder.addOutputState(output, ProposalAndTradeContract.ID)
            txBuilder.addCommand(command)

            // Signing the transaction ourselves.
            val partStx = serviceHub.signInitialTransaction(txBuilder)


            // Gathering the counterparty's signature
            //
            // The proposer must be resolved to its latest identity before it can be compared with the party returned by `getOurIdentity`.
            //
            // The counterparty might be an old key, but the session will be initiated with the most up-to-date identity.
            // No need to use the resolved party in this case.
            val proposer = PartyIdentityResolver.resolveToCurrentParty(input.proposer, serviceHub.identityService)
            val counterparty = if (ourIdentity == proposer) input.proposee else input.proposer
            val counterpartySession = initiateFlow(counterparty)
            val fullyStx = subFlow(CollectSignaturesFlow(partStx, listOf(counterpartySession)))

            // Finalising the transaction.
            return subFlow(FinalityFlow(fullyStx, listOf(counterpartySession)))
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(counterpartySession) {
                override fun checkTransaction(stx: SignedTransaction) {
                    val ledgerTx = stx.toLedgerTransaction(serviceHub, false)

                    // The proposee must be resolved to its latest identity before it can be compared with the party returned by `counterpartySession`.
                    // `counterpartySession` always returns the most up-to-date identity of the counterparty.
                    val proposee = PartyIdentityResolver.resolveToCurrentParty(ledgerTx.inputsOfType<ProposalState>()[0].proposee, serviceHub.identityService)
                    if (proposee != counterpartySession.counterparty) {
                        throw FlowException("Only the proposee can accept a proposal.")
                    }
                }
            }

            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(counterpartySession, txId))
        }
    }
}
