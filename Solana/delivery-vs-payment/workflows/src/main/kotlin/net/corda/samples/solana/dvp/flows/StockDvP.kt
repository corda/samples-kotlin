package net.corda.samples.solana.dvp.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.queryBy
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.samples.solana.dvp.contracts.StockPaymentContract
import net.corda.samples.solana.dvp.states.StockPaymentState
import net.corda.samples.solana.dvp.states.StockState
import net.corda.solana.sdk.SplToken
import net.corda.solana.sdk.instruction.Pubkey
import java.util.Currency

/**
 * Seller flow.
 */
@InitiatingFlow
@StartableByRPC
class StockDvP(
    val symbol: String,
    val quantity: Long,
    val price: Amount<Currency>,
    val buyer: Party
) : FlowLogic<String>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): String {
        /* Obtain a reference from a notary we wish to use. */
        val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB"))

        /* Fetch the state to deliver from the vault using the vault query */
        val stockPointer: TokenPointer<StockState> = QueryUtilities.queryStockPointer(symbol, serviceHub)
        val stockAmount: Amount<TokenType> = Amount(quantity, stockPointer)

        /* Build the transaction builder */
        val txBuilder = TransactionBuilder(notary)

        /* Create a move token proposal for the token using the helper function provided by Token SDK.
         * This would create the movement proposal and would be committed in the ledgers of parties once the transaction in finalized.
        **/
        addMoveFungibleTokens(
            txBuilder,
            serviceHub,
            listOf(PartyAndAmount(buyer, stockAmount)),
            ourIdentity)

        /* Initiate a flow session with the buyer to send the valuation and transfer of the fiat currency */
        val buyerSession = initiateFlow(buyer)

        /* Send the valuation to the buyer. */
        buyerSession.send(price)

        // Receive output for the fiat currency from the buyer, this would contain the transferred amount from buyer to yourself
        val payerDetails = buyerSession.receive<SolanaPayer>().unwrap { it }

        val config = serviceHub.getAppContext().config
        val solanaTokenMint = Pubkey.fromBase58(config.getString("solanaTokenMint"))

        val solanaService = serviceHub.cordaService(SolanaService::class.java)
        val solanaTokenMintDecimals = solanaService.getAccountMintDecimals(solanaTokenMint).toByte()

        val solanaDestinationAccount = Pubkey.fromBase58(config.getString("solanaTokenAccount"))
        val solanaMintAuthority = payerDetails.walletAccount
        val solanaSourceAccount = payerDetails.tokenAccount

        val output = StockPaymentState(stockAmount, ourIdentity, buyer, solanaDestinationAccount,
            solanaSourceAccount, solanaMintAuthority, solanaTokenMint, price.quantity, solanaTokenMintDecimals)

        txBuilder.addOutputState(output, StockPaymentContract.ID)
            .addCommand(
                StockPaymentContract.Commands.Agree(),
                listOf(ourIdentity.owningKey, buyer.owningKey)
            )

        require(payerDetails.tokenMint == solanaTokenMint)

        txBuilder.addNotaryInstruction(
            SplToken.transfer(solanaSourceAccount, solanaTokenMint, solanaDestinationAccount, solanaMintAuthority,
                price.quantity, solanaTokenMintDecimals)
        )

        /* Sign the transaction with your private */
        val initialSignedTx = serviceHub.signInitialTransaction(txBuilder)

        /* Call the CollectSignaturesFlow to receive signature of the buyer */
        val ftx = subFlow(CollectSignaturesFlow(initialSignedTx, listOf(buyerSession)))

        /* Call finality flow to notarise the transaction */
        val stx = subFlow(FinalityFlow(ftx, listOf(buyerSession)))

        /* Distribution list is a list of identities that should receive updates. For this mechanism to behave correctly we call the UpdateDistributionListFlow flow */
        subFlow(UpdateDistributionListFlow(stx))

        return ("\nThe state is sold to " + buyer.name.organisation + "\nTransaction ID: "
                + stx.id)
    }
}

/**
 * Buyer flow.
 */
@InitiatedBy(StockDvP::class)
class SaleStockResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        /* Receive the valuation of the */
        val price = counterpartySession.receive<Amount<Currency>>().unwrap { it }

        // The flow could be extended to check if the amount of tokens is available on Solana

        val config = serviceHub.getAppContext().config
        val solanaTokenMint = Pubkey.fromBase58(config.getString("solanaTokenMint"))
        val solanaMintAuthority = Pubkey.fromBase58(config.getString("solanaWalletAccount"))
        val solanaSourceAccount = Pubkey.fromBase58(config.getString("solanaTokenAccount"))

        val payerDetails = SolanaPayer(solanaTokenMint, solanaMintAuthority, solanaSourceAccount)
        counterpartySession.send(payerDetails)

        /* Signing */
        subFlow(object : SignTransactionFlow(counterpartySession) {
            @Throws(FlowException::class)
            override fun checkTransaction(stx: SignedTransaction) {
            }
        })
        return subFlow(ReceiveFinalityFlow(counterpartySession))
    }
}

@CordaSerializable
data class SolanaPayer(val tokenMint: Pubkey, val walletAccount: Pubkey, val tokenAccount: Pubkey)

object QueryUtilities {
    /**
     * Retrieve any unconsumed StockState and filter by the given symbol
     */
    fun queryStock(symbol: String, serviceHub: ServiceHub): StateAndRef<StockState> {
        val stateAndRefs: List<StateAndRef<StockState>> = serviceHub.vaultService.queryBy<StockState>().states
        // Match the query result with the symbol. If no results match, throw exception
        return stateAndRefs.stream()
            .filter { (state) -> state.data.symbol == symbol }.findAny()
            .orElseThrow { IllegalArgumentException("StockState symbol=\"$symbol\" not found from vault") }
    }

    /**
     * Retrieve any unconsumed StockState and filter by the given symbol
     * Then return the pointer to this StockState
     */
    fun queryStockPointer(symbol: String, serviceHub: ServiceHub): TokenPointer<StockState> {
        val (state) = queryStock(symbol, serviceHub)
        return state.data.toPointer(StockState::class.java)
    }
}