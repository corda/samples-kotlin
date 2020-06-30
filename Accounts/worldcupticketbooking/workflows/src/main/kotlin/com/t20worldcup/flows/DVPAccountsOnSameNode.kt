package com.t20worldcup.flows

import co.paralleluniverse.fibers.Suspendable
import com.t20worldcup.states.T20CricketTicketState
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.money.FiatCurrency.Companion.getInstance
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveNonFungibleTokens
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlow
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import net.corda.core.contracts.Amount
import net.corda.core.flows.*
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import java.util.*
import net.corda.core.node.services.queryBy as queryBy


// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC
class DVPAccountsOnSameNode(private val tokenId: String,
                       private val buyerAccountName:String,
                       private val sellerAccountName:String,
                       private val costOfTicket: Long,
                       private val currency: String) : FlowLogic<String>() {
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
    override fun call():String {

        progressTracker.currentStep = GENERATING_KEYS
        //Buyer Account info
        val buyerInfo = accountService.accountInfo(buyerAccountName)[0].state.data
        val buyerAcct = subFlow(RequestKeyForAccount(buyerInfo))

        //Check if the seller has the ticket
        val sellerInfo = accountService.accountInfo(sellerAccountName).single().state.data
        val sellerAcct = subFlow(RequestKeyForAccount(sellerInfo))

        //All of the Tickets Seller has
        val criteria = QueryCriteria.VaultQueryCriteria(externalIds = listOf(sellerInfo.identifier.id))
        val ticketList = serviceHub.vaultService.queryBy<NonFungibleToken>(criteria = criteria).states

        //Retrieve the one that he wants to sell
        val ticketForSale = ticketList.filter { it.state.data.tokenType.tokenIdentifier ==  tokenId }[0]
                .state.data.tokenType.tokenIdentifier

        //construct the query criteria and get the base token type
        val uuid = UUID.fromString(ticketForSale)
        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(uuid = listOf(uuid),status = Vault.StateStatus.UNCONSUMED)

        //grab the created ticket type off the ledger
        val stateAndRef = serviceHub.vaultService.queryBy(T20CricketTicketState::class.java, queryCriteria).states[0]
        val ticketState  = stateAndRef.state.data
        val tokenPointer = ticketState.toPointer(ticketState.javaClass)

        progressTracker.currentStep = GENERATING_TRANSACTION

        // Obtain a reference from a notary we wish to use.
        /**
         *  METHOD 1: Take first notary on network, WARNING: use for test, non-prod environments, and single-notary networks only!*
         *  METHOD 2: Explicit selection of notary by CordaX500Name - argument can by coded in flow or parsed from config (Preferred)
         *
         *  * - For production you always want to use Method 2 as it guarantees the expected notary is returned.
         */
        val notary = serviceHub.networkMapCache.notaryIdentities.single() // METHOD 1
        // val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB")) // METHOD 2

        val txbuilder = TransactionBuilder(notary)

        //Add ticket to transaction
        addMoveNonFungibleTokens(txbuilder,serviceHub,tokenPointer,buyerAcct)

        //Part2 : Move fungible token - crash from buyer to seller
        val amount = Amount(costOfTicket, getInstance(currency))
        val partyAndAmount = PartyAndAmount(sellerAcct, amount)
        val payMoneyCriteria = QueryCriteria.VaultQueryCriteria(externalIds = listOf(buyerInfo.identifier.id),
                status = Vault.StateStatus.UNCONSUMED)

        //call utility function to move the fungible token from buyer to seller account
        addMoveFungibleTokens(txbuilder, serviceHub, listOf(partyAndAmount), buyerAcct, payMoneyCriteria)

        progressTracker.currentStep = SIGNING_TRANSACTION
        //self sign the transaction. note : the host party will first self sign the transaction.
        val selfSignedTx = serviceHub.signInitialTransaction(txbuilder, listOf(ourIdentity.owningKey))

        progressTracker.currentStep = GATHERING_SIGS
        //establish sessions with buyer and seller. to establish session get the host name from accountinfo object
        val customerSession = initiateFlow(buyerInfo.host)
        val dealerSession = initiateFlow(sellerInfo.host)

        //Note: though buyer and seller are on the same node still we will have to call CollectSignaturesFlow as the signer is not a Party but an account.
        val fulySignedTx = subFlow(CollectSignaturesFlow(selfSignedTx, listOf(customerSession, dealerSession)))

        progressTracker.currentStep = FINALISING_TRANSACTION
        //call ObserverAwareFinalityFlow for finality
        val stx = subFlow<SignedTransaction>(ObserverAwareFinalityFlow(fulySignedTx, listOf(customerSession, dealerSession)))
        return ("The ticket is sold to $buyerAccountName"+ "\ntxID: " + stx.id)
    }
}

@InitiatedBy(DVPAccountsOnSameNode::class)
class DVPAccountsOnSameNodeResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call():SignedTransaction {

        subFlow(object : SignTransactionFlow(counterpartySession) {
            @Throws(FlowException::class)
            override fun checkTransaction(stx: SignedTransaction) {
                // Custom Logic to validate transaction.
            }
        })
        return subFlow(ReceiveFinalityFlow(counterpartySession))
    }
}
