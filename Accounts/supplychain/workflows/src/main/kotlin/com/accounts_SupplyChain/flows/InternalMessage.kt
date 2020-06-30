package com.accounts_SupplyChain.flows


import net.corda.core.flows.*
import co.paralleluniverse.fibers.Suspendable
import com.accounts_SupplyChain.accountUtilities.NewKeyForAccount
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.accounts_SupplyChain.contracts.InternalMessageStateContract
import com.accounts_SupplyChain.states.InternalMessageState
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AnonymousParty
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step



@StartableByRPC
@StartableByService
@InitiatingFlow
class InternalMessage(
        val fromWho: String,
        val whereTo:String,
        val message:String
) : FlowLogic<String>(){

    companion object {
        object GENERATING_KEYS : Step("Generating Keys for transactions.")
        object GENERATING_TRANSACTION : Step("Generating transaction for between accounts")
        object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
        object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
        object GATHERING_SIGS : Step("Gathering the counterparty's signature.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }

        object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
                GENERATING_KEYS,
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): String {

        progressTracker.currentStep = GENERATING_KEYS
        //Initiator account
        val FromAccount = accountService.accountInfo(fromWho).single().state.data
        val newKeyForInitiator = subFlow(NewKeyForAccount(FromAccount.identifier.id))
        //val newKeyForInitiator = subFlow(RequestKeyForAccount(FromAccount))

        //Recieving account
        val targetAccount = accountService.accountInfo(whereTo).single().state.data
        val targetAcctAnonymousParty = subFlow(RequestKeyForAccount(targetAccount))


        progressTracker.currentStep = GENERATING_TRANSACTION
        //generating State for transfer
        val output = InternalMessageState(message, AnonymousParty(newKeyForInitiator.owningKey),targetAcctAnonymousParty)

        // Obtain a reference from a notary we wish to use.
        /**
         *  METHOD 1: Take first notary on network, WARNING: use for test, non-prod environments, and single-notary networks only!*
         *  METHOD 2: Explicit selection of notary by CordaX500Name - argument can by coded in flow or parsed from config (Preferred)
         *
         *  * - For production you always want to use Method 2 as it guarantees the expected notary is returned.
         */
        val notary = serviceHub.networkMapCache.notaryIdentities.single() // METHOD 1
        // val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB")) // METHOD 2

        val transactionBuilder = TransactionBuilder(notary)
        transactionBuilder.addOutputState(output)
                .addCommand(InternalMessageStateContract.Commands.Create(), listOf(targetAcctAnonymousParty.owningKey,newKeyForInitiator.owningKey))


        progressTracker.currentStep = SIGNING_TRANSACTION
        val locallySignedTx = serviceHub.signInitialTransaction(transactionBuilder,
                listOfNotNull(newKeyForInitiator.owningKey,ourIdentity.owningKey))


        progressTracker.currentStep = GATHERING_SIGS
        val sessionForAccountToSendTo = initiateFlow(targetAccount.host)
        val accountToMoveToSignature = subFlow(CollectSignatureFlow(locallySignedTx,
                sessionForAccountToSendTo, targetAcctAnonymousParty.owningKey))
        val signedByCounterParty = locallySignedTx.withAdditionalSignatures(accountToMoveToSignature)

        progressTracker.currentStep = FINALISING_TRANSACTION
        subFlow(FinalityFlow(signedByCounterParty, listOf(sessionForAccountToSendTo).filter { it.counterparty != ourIdentity }))
        return "Inform " + targetAccount.name + " to " + message + "."
    }
}

@InitiatedBy(InternalMessage::class)
class InternalMessageResponder(val counterpartySession: FlowSession) : FlowLogic<Unit>(){
    @Suspendable
    override fun call() {
        subFlow(object : SignTransactionFlow(counterpartySession) {
            @Throws(FlowException::class)
            override fun checkTransaction(stx: SignedTransaction) {
                // Custom Logic to validate transaction.
            }
        })
        subFlow(ReceiveFinalityFlow(counterpartySession))
    }

}






