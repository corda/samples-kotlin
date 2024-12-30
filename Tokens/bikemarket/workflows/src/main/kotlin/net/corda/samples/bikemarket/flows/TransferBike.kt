package net.corda.samples.bikemarket.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.samples.bikemarket.contracts.BikeContract
import net.corda.samples.bikemarket.states.BikeTokenState

@InitiatingFlow
@StartableByRPC
class TransferBike(
    private val bikeLinearId: String,
    private val newOwner: Party
) : FlowLogic<String>() {

    @Suspendable
    override fun call(): String {
        // Obtain a reference to the notary
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        // Query the vault for the bike state by its linear ID
        val bikeStateAndRef = serviceHub.vaultService.queryBy(BikeTokenState::class.java).states
            .find { it.state.data.linearId.toString() == bikeLinearId }
            ?: throw IllegalArgumentException("Bike with ID $bikeLinearId not found in vault.")

        val bikeState = bikeStateAndRef.state.data

        // Ensure the initiating party is the current owner
        if (bikeState.owner != ourIdentity) {
            throw IllegalArgumentException("Only the current owner can transfer the bike.")
        }

        // Create the updated bike state with the new owner
        val updatedBikeState = bikeState.copy(owner = newOwner)

        // Build the transaction
        val transactionBuilder = TransactionBuilder(notary)
            .addInputState(bikeStateAndRef)
            .addOutputState(updatedBikeState)
            .addCommand(BikeContract.Commands.Transfer(), listOf(ourIdentity.owningKey, newOwner.owningKey))

        // Verify the transaction
        transactionBuilder.verify(serviceHub)

        // Sign the transaction locally
        val locallySignedTx = serviceHub.signInitialTransaction(transactionBuilder)

        // Collect signatures from the new owner
        val newOwnerSession = initiateFlow(newOwner)
        val fullySignedTx = subFlow(CollectSignaturesFlow(locallySignedTx, listOf(newOwnerSession)))

        // Finalize the transaction
        val finalizedTx = subFlow(FinalityFlow(fullySignedTx, listOf(newOwnerSession)))

        return bikeLinearId
    }
}

@InitiatedBy(TransferBike::class)
class TransferBikeResponder(private val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        logger.info("TransferBikeResponder: Received request to transfer bike token.")

        // Create a SignTransactionFlow to validate and sign the transaction
        val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                // Add any custom validation logic here if necessary
            }
        }

        val txId = subFlow(signTransactionFlow).id
        logger.info("TransferBikeResponder: Transaction signed. ID: $txId")

        // Receive and finalize the transaction
        return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
    }
}
