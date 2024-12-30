package net.corda.samples.bikemarket.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.samples.bikemarket.contracts.BikeContract
import net.corda.samples.bikemarket.states.BikeTokenState

@InitiatingFlow
@StartableByRPC
class RedeemBike(private val bikeLinearId: String) : FlowLogic<String>() {

    @Suspendable
    override fun call(): String {
        // Obtain a reference to the notary
        val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB"))
            ?: throw IllegalArgumentException("Notary not found.")

        // Query the vault for the bike state by its linear ID
        val bikeStateAndRef = serviceHub.vaultService.queryBy(BikeTokenState::class.java).states
            .find { it.state.data.linearId.toString() == bikeLinearId }
            ?: throw IllegalArgumentException("Bike with ID $bikeLinearId not found in vault.")

        val bikeState = bikeStateAndRef.state.data

        // Ensure the bike belongs to the party initiating the burn
        if (bikeState.maintainer != ourIdentity) {
            throw IllegalArgumentException("Only the bike owner can redeem it.")
        }

        // Build the transaction to burn the bike token
        val transactionBuilder = TransactionBuilder(notary)
            .addInputState(bikeStateAndRef)
            .addCommand(BikeContract.Commands.Burn(), ourIdentity.owningKey)

        // Verify the transaction
        transactionBuilder.verify(serviceHub)

        // Sign the transaction locally
        val locallySignedTx = serviceHub.signInitialTransaction(transactionBuilder)

        // Finalize the transaction
        val finalizedTx = subFlow(FinalityFlow(locallySignedTx, emptyList()))

        return "Successfully redeemed bike token with ID $bikeLinearId."
    }
}

@InitiatedBy(RedeemBike::class)
class RedeemBikeResponder(private val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        logger.info("RedeemBikeResponder: Received request to redeem bike token.")

        // Create a SignTransactionFlow to validate and sign the transaction
        val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                // Add any custom validation logic here if necessary
            }
        }

        val txId = subFlow(signTransactionFlow).id
        logger.info("RedeemBikeResponder: Transaction signed. ID: $txId")

        // Receive and finalize the transaction
        return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
    }
}
