package net.corda.samples.bikemarket.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.utilities.withNotary
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.samples.bikemarket.contracts.FrameContract
import net.corda.samples.bikemarket.states.FrameTokenState

// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC
class CreateFrameToken(private val frameSerial: String) : FlowLogic<String>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call():String {

        // Obtain a reference from a notary we wish to use.
        val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB"))
        //Create non-fungible frame token
        val uuid = UniqueIdentifier()
        val frame = FrameTokenState(ourIdentity, uuid,0,frameSerial)

        //warp it with transaction state specifying the notary
        val transactionState = frame withNotary notary!!

        val transactionBuilder = TransactionBuilder(notary)


        transactionBuilder.addOutputState(transactionState).addCommand(FrameContract.Commands.Create(), ourIdentity.owningKey)
        transactionBuilder.verify(serviceHub)

        val locallySignedTx = serviceHub.signInitialTransaction(transactionBuilder)
        println("Signed transaction")
        println(locallySignedTx)


        val sessions = serviceHub.networkMapCache.allNodes.map { it.legalIdentities.first() }.filter { it != ourIdentity && it != notary }.map { initiateFlow(it) }
        val subflowoutput = subFlow(FinalityFlow(locallySignedTx, sessions))
//        subFlow(FinalityFlow( listOf(sessionForAccountToSendTo).filter { it.counterparty != ourIdentity }))
        serviceHub.vaultService.queryBy(FrameTokenState::class.java).states.forEach {
            println(it.state)
        }

        return transactionState.data.linearId.toString()
    }
}


@InitiatedBy(CreateFrameToken::class)
class Acceptor(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {

        println("received session")
        val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
            }
        }

        println("passed signing transaction flow")
        val txId = subFlow(signTransactionFlow).id
        println("txId: $txId")
        return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
    }
}