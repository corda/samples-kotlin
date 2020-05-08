package com.iplticket.flows

import co.paralleluniverse.fibers.Suspendable
import com.iplticket.states.IplTicketState
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.money.FiatCurrency.Companion.getInstance
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveNonFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveTokens
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlow
import com.r3.corda.lib.tokens.workflows.internal.selection.TokenSelection
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import net.corda.core.utilities.unwrap
import java.util.*


// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC
class BuyIplTicketFlow(private val tokenId: String,
                       private val buyerAccountName:String,
                       private val sellerAccountName:String,
                       private val costOfTicker: Long) : FlowLogic<String>() {
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
        val buyerAcctInfo = accountService.accountInfo(buyerAccountName)[0].state.data
        val buyerAcct = subFlow(RequestKeyForAccount(buyerAcctInfo))

        //Check if the seller has the ticket
        val sellerInfo = accountService.accountInfo(sellerAccountName).single().state.data
        val sellerAcct = subFlow(RequestKeyForAccount(buyerAcctInfo))
        val criteria = QueryCriteria.VaultQueryCriteria(externalIds = listOf(sellerInfo.identifier.id))

        //Ticket
        val ticketList = serviceHub.vaultService.queryBy<NonFungibleToken>(criteria = criteria).states
        val ticketForSale = ticketList.filter { it.state.data.tokenType.tokenIdentifier ==  tokenId }[0]
                .state.data.tokenType.tokenIdentifier

        //construct the query criteria and get the base token type
        val uuid = UUID.fromString(ticketForSale)
        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(uuid = listOf(uuid),status = Vault.StateStatus.UNCONSUMED)

        //grab the created ticket type off the ledger
        val stateAndRef = serviceHub.vaultService.queryBy(IplTicketState::class.java, queryCriteria).states[0]
        val ticketState  = stateAndRef.state.data

        progressTracker.currentStep = GENERATING_TRANSACTION
        /* Build the transaction builder */
        val notary = serviceHub.networkMapCache.notaryIdentities[0]
        val txBuilder = TransactionBuilder(notary)

        /* Create a move token proposal for the house token using the helper function provided by Token SDK.
         * This would create the movement proposal and would
         * be committed in the ledgers of parties once the transaction in finalized.
         */
        addMoveNonFungibleTokens(txBuilder, serviceHub, ticketState.toPointer(ticketState.javaClass), buyerAcct)

        /* Initiate a flow session with the buyer to send the ticket price and transfer of the fiat currency */
        val buyerSession = initiateFlow(buyerAcct)
        val priceOfTicket = Amount.parseCurrency("$costOfTicker USD")
        val msgPair: Pair<AnonymousParty,Amount<Currency>> = Pair(sellerAcct,priceOfTicket)
        buyerSession.send(msgPair)

        //Recieve the fiat currency exchange from the buyer
        val inputs = subFlow(ReceiveStateAndRefFlow<FungibleToken>(buyerSession))

        // Recieve output for the fiat currency from the buyer, this would contain the transfered amount from buyer to yourself
        val moneyReceived: List<FungibleToken> = buyerSession.receive<List<FungibleToken>>().unwrap { it -> it }

        /* Create a fiat currency proposal for the house token using the helper function provided by Token SDK. */
        addMoveTokens(txBuilder, inputs, moneyReceived)

        progressTracker.currentStep = SIGNING_TRANSACTION
        /* Sign the transaction with your private */
        val initialSignedTrnx = serviceHub.signInitialTransaction(txBuilder,listOf(ourIdentity.owningKey))

        progressTracker.currentStep = GATHERING_SIGS
        //establish sessions with buyer and seller. to establish session get the host name from accountinfo object
        val customerSession = initiateFlow(buyerAcctInfo.host)
        val dealerSession = initiateFlow(sellerInfo.host)
        val fullysignedTx = subFlow(CollectSignaturesFlow(initialSignedTrnx, listOf(customerSession, dealerSession)))

        /* Distribution list is a list of identities that should receive updates. For this mechanism to behave correctly we call the UpdateDistributionListFlow flow */
        subFlow(UpdateDistributionListFlow(fullysignedTx))

        progressTracker.currentStep = FINALISING_TRANSACTION
        /* Call finality flow to notarise the transaction */
        val stx = subFlow(ObserverAwareFinalityFlow(fullysignedTx, listOf(customerSession, dealerSession)))

        return ("The ticket is sold to $buyerAccountName"+ "\ntxID: " + stx.id)
    }
}

@InitiatedBy(BuyIplTicketFlow::class)
class BuyIplTicketFlowResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call():SignedTransaction {
        /* Recieve the valuation of the house */
        val msgPair = counterpartySession.receive<Pair<AnonymousParty,Amount<Currency>>>().unwrap { it }
        val price = msgPair.second
        /* Create instance of the fiat currecy token amount */
        val priceToken = Amount(price.quantity, getInstance(price.token.currencyCode))

        val tokenSelection = TokenSelection(serviceHub, 8, 100, 2000)
        val partyAndAmount = PartyAndAmount(msgPair.first,priceToken)
        val inputsAndOutputs : Pair<List<StateAndRef<FungibleToken>>, List<FungibleToken>> =
                tokenSelection.generateMove(runId.uuid, listOf(partyAndAmount),ourIdentity,null)
        /* Call SendStateAndRefFlow to send the inputs to the Initiator*/
        subFlow(SendStateAndRefFlow(counterpartySession, inputsAndOutputs.first))
        /* Send the output generated from the fiat currency move proposal to the initiator */
        counterpartySession.send(inputsAndOutputs.second)

        //signing
        subFlow(object : SignTransactionFlow(counterpartySession) {
            @Throws(FlowException::class)
            override fun checkTransaction(stx: SignedTransaction) { // Custom Logic to validate transaction.
            }
        })
        return subFlow(ReceiveFinalityFlow(counterpartySession))

    }
}
