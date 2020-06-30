package com.t20worldcup.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.r3.corda.lib.ci.workflows.SyncKeyMappingFlow
import com.r3.corda.lib.ci.workflows.SyncKeyMappingFlowHandler
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.money.FiatCurrency.Companion.getInstance
import com.r3.corda.lib.tokens.selection.TokenQueryBy
import com.r3.corda.lib.tokens.selection.database.config.MAX_RETRIES_DEFAULT
import com.r3.corda.lib.tokens.selection.database.config.PAGE_SIZE_DEFAULT
import com.r3.corda.lib.tokens.selection.database.config.RETRY_CAP_DEFAULT
import com.r3.corda.lib.tokens.selection.database.config.RETRY_SLEEP_DEFAULT
import com.r3.corda.lib.tokens.selection.database.selector.DatabaseTokenSelection
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveNonFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveTokens
import com.r3.corda.lib.tokens.workflows.utilities.heldTokenAmountCriteria
import com.r3.corda.lib.tokens.workflows.utilities.sumTokenCriteria
import com.t20worldcup.states.T20CricketTicketState
import net.corda.core.contracts.Amount
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.QueryCriteria.LinearStateQueryCriteria
import net.corda.core.node.services.vault.QueryCriteria.VaultQueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import net.corda.core.utilities.unwrap
import java.security.PublicKey
import java.util.*
import kotlin.math.sign


// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC
class DVPAccountsHostedOnDifferentNodes(private val tokenId: String,
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

        //Part1 : Move non fungible token - ticket from seller to buyer
        //establish session with seller
        val sellerSession = initiateFlow(sellerInfo.host)

        //send uuid, buyer,seller account name to seller
        sellerSession.send(tokenId)
        sellerSession.send(buyerAccountName)
        sellerSession.send(sellerAccountName)


        //buyer will create generate a move tokens state and send this state with new holder(seller) to seller
        val amount = Amount(costOfTicket, getInstance(currency))

        //Buyer Query for token balance.
        val queryCriteria = heldTokenAmountCriteria(getInstance(currency), buyerAcct).and(sumTokenCriteria())
        val sum = serviceHub.vaultService.queryBy(FungibleToken::class.java, queryCriteria).component5()
        if (sum.size == 0) throw FlowException("$buyerAccountName has 0 token balance. Please ask the Bank to issue some cash.") else {
            val tokenBalance = sum[0] as Long
            if (tokenBalance < costOfTicket) throw FlowException("Available token balance of $buyerAccountName is less than the cost of the ticket. Please ask the Bank to issue some cash if you wish to buy the ticket ")
        }

        //the tokens to move to new account which is the seller account
        val partyAndAmount:Pair<AbstractParty, Amount<TokenType>> = Pair(sellerAcct, amount)


        //let's use the DatabaseTokenSelection to get the tokens from the db
        val tokenSelection = DatabaseTokenSelection(serviceHub, MAX_RETRIES_DEFAULT,
                RETRY_SLEEP_DEFAULT, RETRY_CAP_DEFAULT, PAGE_SIZE_DEFAULT)

        //call generateMove which gives us 2 stateandrefs with tokens having new owner as seller.

        //call generateMove which gives us 2 stateandrefs with tokens having new owner as seller.
        val inputsAndOutputs = tokenSelection
                .generateMove(Arrays.asList(partyAndAmount), buyerAcct, TokenQueryBy(), runId.uuid)

        //send the generated inputsAndOutputs to the seller
        subFlow(SendStateAndRefFlow(sellerSession, inputsAndOutputs.first))
        sellerSession.send(inputsAndOutputs.second)

        //sync following keys with seller - buyeraccounts, selleraccounts which we generated above using RequestKeyForAccount, and IMP: also share the anonymouse keys
        //created by the above token move method for the holder.
        val signers: MutableList<AbstractParty> = ArrayList()
        signers.add(buyerAcct)
        signers.add(sellerAcct)

        val inputs = inputsAndOutputs.first
        for ((state) in inputs) {
            signers.add(state.data.holder)
        }

        //Sync our associated keys with the conterparties.
        subFlow(SyncKeyMappingFlow(sellerSession, signers))

        //this is the handler for synckeymapping called by seller. seller must also have created some keys not known to us - buyer
        subFlow(SyncKeyMappingFlowHandler(sellerSession))

        //recieve the data from counter session in tx formatt.
        subFlow(object : SignTransactionFlow(sellerSession) {
            @Throws(FlowException::class)
            override fun checkTransaction(stx: SignedTransaction) {
                // Custom Logic to validate transaction.
            }
        })
        val stx = subFlow(ReceiveFinalityFlow(sellerSession))

        return ("The ticket is sold to $buyerAccountName"+ "\ntxID: "+stx.id)
    }
}

@InitiatedBy(DVPAccountsHostedOnDifferentNodes::class)
class DVPAccountsHostedOnDifferentNodesResponder(val otherSide: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call():SignedTransaction {

        //get all the details from the seller
        val tokenId: String = otherSide.receive(String::class.java).unwrap { it }
        val buyerAccountName: String = otherSide.receive(String::class.java).unwrap { it }
        val sellerAccountName: String = otherSide.receive(String::class.java).unwrap{ it }

        val inputs = subFlow(ReceiveStateAndRefFlow<FungibleToken>(otherSide))
        val moneyReceived: List<FungibleToken> = otherSide.receive(List::class.java).unwrap{ it } as List<FungibleToken>


        //call SyncKeyMappingHandler for SyncKey Mapping called at buyers side
        subFlow(SyncKeyMappingFlowHandler(otherSide))


        //Get buyers and sellers account infos
        val buyerAccountInfo = accountService.accountInfo(buyerAccountName)[0].state.data
        val sellerAccountInfo = accountService.accountInfo(sellerAccountName)[0].state.data

        //Generate new keys for buyers and sellers
        val buyerAccount = subFlow(RequestKeyForAccount(buyerAccountInfo))
        val sellerAccount = subFlow(RequestKeyForAccount(sellerAccountInfo))

        //query for all tickets
        val queryCriteriaForSellerTicketType: QueryCriteria = VaultQueryCriteria(
                externalIds = listOf(sellerAccountInfo.identifier.id),
                status = Vault.StateStatus.UNCONSUMED)


        val allNonfungibleTokens = serviceHub.vaultService
                .queryBy<NonFungibleToken>(criteria = queryCriteriaForSellerTicketType).states

        //Retrieve the one that he wants to sell
        val matchedNonFungibleToken: StateAndRef<NonFungibleToken> = allNonfungibleTokens.filter{ it.state.data.tokenType.tokenIdentifier.equals(tokenId)}[0]

        val ticketId = matchedNonFungibleToken.state.data.tokenType.tokenIdentifier

        //Query for the ticket Buyer wants to sell.
        val queryCriteria: QueryCriteria = LinearStateQueryCriteria( uuid = listOf(UUID.fromString(ticketId)),status = Vault.StateStatus.UNCONSUMED)

        //grab the t20worldcup off the ledger
        val stateAndRef = serviceHub.vaultService.queryBy<T20CricketTicketState>(criteria = queryCriteria).states[0]
        val evolvableTokenType: T20CricketTicketState = stateAndRef.state.data

        //get the pointer pointer to the T20CricketTicket
        val tokenPointer: TokenPointer<*> = evolvableTokenType.toPointer(evolvableTokenType.javaClass)

        //building transaction

        // Obtain a reference from a notary we wish to use.
        /**
         *  METHOD 1: Take first notary on network, WARNING: use for test, non-prod environments, and single-notary networks only!*
         *  METHOD 2: Explicit selection of notary by CordaX500Name - argument can by coded in flow or parsed from config (Preferred)
         *
         *  * - For production you always want to use Method 2 as it guarantees the expected notary is returned.
         */
        val notary = serviceHub.networkMapCache.notaryIdentities.single() // METHOD 1
        // val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB")) // METHOD 2

        val txBuilder = TransactionBuilder(notary)

        //part1 of DVP is to transfer the non fungible token from seller to buyer
        addMoveNonFungibleTokens(txBuilder,serviceHub,tokenPointer,buyerAccount)

        //part2 of DVP is to transfer cash - fungible token from buyer to seller and return the change to buyer
        addMoveTokens(txBuilder, inputs, moneyReceived)

        //add signers
        val signers: MutableList<AbstractParty> = ArrayList()
        signers.add(buyerAccount)
        signers.add(sellerAccount)

        for ((state) in inputs) {
            signers.add(state.data.holder)
        }

        //sync keys with buyer, again sync for similar members
        subFlow(SyncKeyMappingFlow(otherSide, signers))

        //call filterMyKeys to get the my signers for seller node and pass in as a 4th parameter to CollectSignaturesFlow.
        //by doing this we tell CollectSignaturesFlow that these are the signers which have already signed the transaction
        val commandWithPartiesList: List<CommandWithParties<CommandData>> = txBuilder.toLedgerTransaction(serviceHub).commands
        val mySigners: MutableList<PublicKey> = ArrayList()
        commandWithPartiesList.map {
            val signer = (serviceHub.keyManagementService.filterMyKeys(it.signers) as ArrayList<PublicKey>)
            if(signer.size >0){
                mySigners.add(signer[0]) }
            }

        val selfSignedTransaction = serviceHub.signInitialTransaction(txBuilder, mySigners)
        val fullySignedTx = subFlow(CollectSignaturesFlow(selfSignedTransaction, listOf(otherSide), mySigners))

        //call FinalityFlow for finality
        return subFlow(FinalityFlow(fullySignedTx, Arrays.asList(otherSide)))
    }
}
