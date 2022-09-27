package net.corda.samples.dollartohousetoken.flows
import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.money.FiatCurrency.Companion.getInstance
import com.r3.corda.lib.tokens.selection.database.selector.DatabaseTokenSelection
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveNonFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveTokens
import com.r3.corda.lib.tokens.workflows.flows.redeem.RedeemTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.redeem.addTokensToRedeem
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.samples.dollartohousetoken.states.CarState
import java.util.*

// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC
class CarSale(val carId: String,
              val buyer: Party) : FlowLogic<String>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call():String {
        // Obtain a reference from a notary we wish to use.
        val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB"))

        /* Fetch the car state from the vault using the vault query */
        val inputCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(UniqueIdentifier.fromString(carId)))
        val carStateAndRef = serviceHub.vaultService.queryBy<CarState>(criteria = inputCriteria).states.single()
        val carState = carStateAndRef.state.data

        /* Build the transaction builder */
        val txBuilder = TransactionBuilder(notary)

        /* Create a move token proposal */
        addMoveNonFungibleTokens(txBuilder, serviceHub, carState.toPointer(carState.javaClass), buyer)

        /* Initiate a flow session with the buyer */
        val buyerSession = initiateFlow(buyer)
        buyerSession.send(carState.carValue)

        // Recieve inputStatesAndRef for the fiat currency exchange from the buyer, these would be inputs to the fiat currency exchange transaction.
        val inputs = subFlow(ReceiveStateAndRefFlow<FungibleToken>(buyerSession))

        // Recieve output for the fiat currency from the buyer, this would contain the transfered amount from buyer to yourself
        val moneyReceived: List<FungibleToken> = buyerSession.receive<List<FungibleToken>>().unwrap { it -> it}

        /* Create a fiat currency proposal for the house token using the helper function provided by Token SDK. */
        addMoveTokens(txBuilder, inputs, moneyReceived)

        /* Sign the transaction with your private */
        val initialSignedTrnx = serviceHub.signInitialTransaction(txBuilder)

        /* Call the CollectSignaturesFlow to recieve signature of the buyer */
        val ftx= subFlow(CollectSignaturesFlow(initialSignedTrnx, listOf(buyerSession)))

        /* Call finality flow to notarise the transaction */
        val stx = subFlow(FinalityFlow(ftx, listOf(buyerSession)))
        subFlow(UpdateDistributionListFlow(stx))
        return ("\nThe car is sold to " + buyer.name.organisation + "\nTransaction ID: "
                + stx.id)
    }
}

@InitiatedBy(CarSale::class)
class CarSaleResponder(val counterpartySession: FlowSession) : FlowLogic<String>() {
    @Suspendable
    override fun call():String {
        /* Recieve the valuation of the car */
        val price = counterpartySession.receive<Amount<Currency>>().unwrap { it }

        /* Create instance of the fiat currecy token amount */
        val priceToken = Amount(price.quantity, getInstance(price.token.currencyCode))

        /*
        *  Generate the move proposal, it returns the input-output pair for the fiat currency transfer, which we need to send to the Initiator.
        * */
        val partyAndAmount = PartyAndAmount(counterpartySession.counterparty,priceToken)
        val inputsAndOutputs : Pair<List<StateAndRef<FungibleToken>>, List<FungibleToken>> =
                DatabaseTokenSelection(serviceHub).generateMove(listOf(Pair(counterpartySession.counterparty,priceToken)),ourIdentity)
        //.generateMove(runId.uuid, listOf(partyAndAmount),ourIdentity,null)

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

        subFlow(ReceiveFinalityFlow(counterpartySession))

        return "$ourIdentity"
    }
}
