package com.accounts_SupplyChain.flows


import net.corda.core.flows.*
import co.paralleluniverse.fibers.Suspendable
import com.accounts_SupplyChain.accountUtilities.NewKeyForAccount
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount


import com.accounts_SupplyChain.contracts.PaymentStateContract
import com.accounts_SupplyChain.states.PaymentState
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AnonymousParty
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.util.concurrent.atomic.AtomicReference
import net.corda.core.node.StatesToRecord
import net.corda.core.utilities.ProgressTracker

@StartableByRPC
@StartableByService
@InitiatingFlow
class SendPayment(
        val whoAmI: String,
        val whereTo:String,
        val amount:Int
) : FlowLogic<String>(){

    companion object {
        object GENERATING_KEYS : ProgressTracker.Step("Generating Keys for transactions.")
        object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction for between accounts")
        object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
        object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
        object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }

        object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
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

        //Create a key for Loan transaction
        progressTracker.currentStep = GENERATING_KEYS
        val myAccount = accountService.accountInfo(whoAmI).single().state.data
        val myKey = subFlow(NewKeyForAccount(myAccount.identifier.id)).owningKey
        val targetAccount = accountService.accountInfo(whereTo).single().state.data
        val targetAcctAnonymousParty = subFlow(RequestKeyForAccount(targetAccount))



        //generating State for transfer
        progressTracker.currentStep = GENERATING_TRANSACTION
        val output = PaymentState(amount,targetAcctAnonymousParty,AnonymousParty(myKey))

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
                .addCommand(PaymentStateContract.Commands.Create(), listOf(targetAcctAnonymousParty.owningKey,myKey))

        //Pass along Transaction
        progressTracker.currentStep = SIGNING_TRANSACTION
        val locallySignedTx = serviceHub.signInitialTransaction(transactionBuilder, listOfNotNull(ourIdentity.owningKey,myKey))


        //Collect sigs
        progressTracker.currentStep =GATHERING_SIGS
        val sessionForAccountToSendTo = initiateFlow(targetAccount.host)
        val accountToMoveToSignature = subFlow(CollectSignatureFlow(locallySignedTx, sessionForAccountToSendTo, targetAcctAnonymousParty.owningKey))
        val signedByCounterParty = locallySignedTx.withAdditionalSignatures(accountToMoveToSignature)

        progressTracker.currentStep =FINALISING_TRANSACTION
        subFlow(FinalityFlow(signedByCounterParty, listOf(sessionForAccountToSendTo).filter { it.counterparty != ourIdentity }))
        return "Payment send to " + targetAccount.host.name.organisation + "'s "+ targetAccount.name+ " team."
    }
}

@InitiatedBy(SendPayment::class)
class SendPaymentResponder(val counterpartySession: FlowSession) : FlowLogic<Unit>(){
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






