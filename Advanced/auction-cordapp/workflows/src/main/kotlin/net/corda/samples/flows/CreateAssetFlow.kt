package net.corda.samples.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.samples.contracts.AssetContract
import net.corda.samples.states.Asset
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.util.*

// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC
class CreateAssetFlow(
        private val title: String,
        private val description: String,
        private val imageURL: String
) : FlowLogic<SignedTransaction>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call():SignedTransaction {
        // Obtain a reference from a notary we wish to use.
        /**
         *  METHOD 1: Take first notary on network, WARNING: use for test, non-prod environments, and single-notary networks only!*
         *  METHOD 2: Explicit selection of notary by CordaX500Name - argument can by coded in flow or parsed from config (Preferred)
         *
         *  * - For production you always want to use Method 2 as it guarantees the expected notary is returned.
         */
        val notary = serviceHub.networkMapCache.notaryIdentities.single() // METHOD 1
        // val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB")) // METHOD 2

        // Create the output state
        val output = Asset(UniqueIdentifier(), title, description, imageURL, ourIdentity)
        // Build the transaction, add the output state and the command to the transaction.
        val txBuilder = TransactionBuilder(notary)
                .addOutputState(output)
                .addCommand(AssetContract.Commands.CreateAsset(), listOf(ourIdentity.owningKey))
        // Verify the transaction
        txBuilder.verify(serviceHub)
        // Sign the transaction
        val stx = serviceHub.signInitialTransaction(txBuilder)
        // Notarise the transaction and record the state in the ledger.
        return subFlow(FinalityFlow(stx, listOf()))
    }
}