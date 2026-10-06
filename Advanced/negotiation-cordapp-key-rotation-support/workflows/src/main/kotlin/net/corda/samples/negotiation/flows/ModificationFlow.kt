package net.corda.samples.negotiation.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.keyrotation.crossprovider.PartyIdentityResolver.Companion.resolveToCurrentParty
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.samples.negotiation.contracts.ProposalAndTradeContract
import net.corda.samples.negotiation.states.ProposalState


object ModificationFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val proposalId: UniqueIdentifier, val newAmount: Int) : FlowLogic<SignedTransaction>() {
        override val progressTracker = ProgressTracker()

        @Suspendable
        override fun call(): SignedTransaction {
            // Retrieving the input from the vault.
            val inputCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(proposalId))
            val inputStateAndRef = serviceHub.vaultService.queryBy<ProposalState>(inputCriteria).states.single()
            val input = inputStateAndRef.state.data

            //Creating the output
            //
            // The proposerParty is retrieved from the state and must be resolved to its latest identity
            // before it can be compared with the party returned by `getOurIdentity`. Since the intent is not to
            // replace the old key with the new one in the output state, calling `resolveToCurrentParty` is sufficient.
            //
            // Comparing parties that both originate from states is safe without additional resolution.
            val proposerParty = resolveToCurrentParty(input.proposer, serviceHub.identityService)
            val myPartyFromInput = if (ourIdentity.equals(proposerParty)) input.proposer else input.proposee
            val counterpartyFromInput = if (myPartyFromInput.equals(input.proposer)) input.proposee else input.proposer
            val output = ProposalState(
                amount = newAmount,
                buyer = input.buyer,
                seller= input.seller,
                proposer = myPartyFromInput,
                proposee = counterpartyFromInput,
                linearId = input.linearId
            )

            // Creating the command.
            val requiredSigners = listOf(input.proposer.owningKey, input.proposee.owningKey)
            val command = Command(ProposalAndTradeContract.Commands.Modify(), requiredSigners)

            // Building the transaction.
            val notary = inputStateAndRef.state.notary
            val txBuilder = TransactionBuilder(notary)
            txBuilder.addInputState(inputStateAndRef)
            txBuilder.addOutputState(output, ProposalAndTradeContract.ID)
            txBuilder.addCommand(command)

            // Signing the transaction ourselves.
            val partStx = serviceHub.signInitialTransaction(txBuilder)

            //Gathering the counterparty's signatures
            //
            // The counterparty might be an old key, but the session will be initiated with the most up-to-date identity.
            // No need to use the resolved party in this case.
            val counterpartySession = initiateFlow(counterpartyFromInput)
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
                    val input: ProposalState = ledgerTx.inputsOfType<ProposalState>()[0]

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

            return subFlow(ReceiveFinalityFlow(counterpartySession, txId))
        }
    }
}