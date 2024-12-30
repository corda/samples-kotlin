package net.corda.samples.bikemarket.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.utilities.withNotary
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.samples.bikemarket.contracts.BikeContract
import net.corda.samples.bikemarket.states.BikeTokenState
import net.corda.samples.bikemarket.states.FrameTokenState

@InitiatingFlow
@StartableByRPC
class CreateNewBike(
    private val brand: String,
    private val name: String,
    private val price: Int,
    private val wheels: String,
    private val groupset: String
) : FlowLogic<String>() {

    @Suspendable
    override fun call(): String {
        // Obtain a reference to the notary
        val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB"))
            ?: throw IllegalArgumentException("Notary not found.")

        // Create a non-fungible bike token state
        val bike = BikeTokenState(
            maintainer = ourIdentity,
            linearId = UniqueIdentifier(),
            fractionDigits = 0,
            owner = ourIdentity,
            brand = brand,
            name = name,
            price = price,
            wheels = wheels,
            groupset = groupset
        )

        // Wrap the bike state with a transaction state specifying the notary
        val transactionState = bike withNotary notary

        // Build the transaction
        val transactionBuilder = TransactionBuilder(notary)
            .addOutputState(transactionState)
            .addCommand(BikeContract.Commands.Create(), ourIdentity.owningKey)

        // Verify the transaction
        transactionBuilder.verify(serviceHub)

        // Sign the transaction locally
        val locallySignedTx = serviceHub.signInitialTransaction(transactionBuilder)

        // Gather sessions with all relevant parties (excluding the notary and self)
        val sessions = serviceHub.networkMapCache.allNodes.map { it.legalIdentities.first() }
            .filter { it != ourIdentity && it != notary }
            .map { initiateFlow(it) }
//
//        val sessions = listOf(initiateFlow(serviceHub.), initiateFlow(notary))

        // Finalize the transaction
        val finalizedTx = subFlow(FinalityFlow(locallySignedTx, sessions))

        // Optional: Query the vault for existing frame tokens (for debug purposes)
        serviceHub.vaultService.queryBy(FrameTokenState::class.java).states.forEach {
            logger.info("Vault contains: ${it.state}")
        }

        return transactionState.data.linearId.toString()
    }
}

@InitiatedBy(CreateNewBike::class)
class BikeAcceptor(private val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        logger.info("Session received from: ${otherPartySession.counterparty}")

        // Create a SignTransactionFlow to validate and sign the transaction
        val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                // Add any custom validation logic here if necessary
                println("Checking transaction")
            }
        }

        println("Passed signing transaction flow")
        val txId = subFlow(signTransactionFlow).id
        logger.info("Transaction signed. ID: $txId")

        // Receive and finalize the transaction
        return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
    }
}
