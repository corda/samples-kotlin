package net.corda.samples.solana.dvp.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import net.corda.core.contracts.Amount
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
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.samples.solana.dvp.contracts.SharesPaymentContract
import net.corda.samples.solana.dvp.states.SharesPaymentState
import net.corda.samples.solana.dvp.states.StockState
import net.corda.solana.sdk.SplToken
import net.corda.solana.sdk.instruction.Pubkey
import java.math.BigDecimal

/**
 * Seller flow.
 */
@InitiatingFlow
@StartableByRPC
class SharesDvP(
    val symbol: String,
    val quantity: Long,
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

        val txBuilder = TransactionBuilder(notary)

        /* Create a move token proposal for the shares to deliver using the helper function provided by Token SDK */
        addMoveFungibleTokens(
            txBuilder,
            serviceHub,
            listOf(PartyAndAmount(buyer, stockAmount)),
            ourIdentity)

        /* Initiate a flow session with the buyer to send the valuation and transfer of the fiat currency */
        val buyerSession = initiateFlow(buyer)

        /* Send the valuation to the buyer. */
        val stockState = stockPointer.pointer.resolve(serviceHub).state.data
        buyerSession.send(Pair(quantity, stockState.price))

        /* Receive Solana accounts of the payer (buyer) */
        val payerDetails = buyerSession.receive<SolanaPayer>().unwrap { it }

        /* Collect own Solana accounts for Solana transaction and lookup for decimals (required for a checked transfer) */
        val config = serviceHub.getAppContext().config
        val solanaTokenMint = Pubkey.fromBase58(config.getString("solanaTokenMint"))
        val solanaService = serviceHub.cordaService(SolanaService::class.java)
        val solanaTokenMintDecimals = solanaService.getAccountMintDecimals(solanaTokenMint)
        val solanaDestinationAccount = Pubkey.fromBase58(config.getString("solanaTokenAccount"))
        val solanaMintAuthority = payerDetails.walletAccount
        val solanaSourceAccount = payerDetails.tokenAccount

        /* Set price in long format for Solana transaction */
        val solanaPrice = stockState.price.multiply(quantity.toBigDecimal())
        val solanaPriceAsLong = solanaPrice.toScaledLong(solanaTokenMintDecimals)

        /* Create Corda state with payment details */
        // TODO there is some duplication wth data in a notary instruction
        val output = SharesPaymentState(stockAmount, ourIdentity, buyer, solanaDestinationAccount,
            solanaSourceAccount, solanaMintAuthority, solanaTokenMint,
            solanaPriceAsLong, solanaTokenMintDecimals.toByte())

        txBuilder.addOutputState(output, SharesPaymentContract.ID)
            .addCommand(
                SharesPaymentContract.Commands.Agree(),
                listOf(ourIdentity.owningKey, buyer.owningKey)
            )

        require(payerDetails.tokenMint == solanaTokenMint)

        /* Create Solana transfer instruction that will be run by Corda Notary */
        txBuilder.addNotaryInstruction(
            SplToken.transfer(solanaSourceAccount, solanaTokenMint, solanaDestinationAccount, solanaMintAuthority,
                output.solanaPaymentAmount, output.solanaPaymentDecimals)
        )

        /* Sign the transaction with your private key */
        val initialSignedTx = serviceHub.signInitialTransaction(txBuilder)

        /* Call the CollectSignaturesFlow to receive signature of the buyer */
        val ftx = subFlow(CollectSignaturesFlow(initialSignedTx, listOf(buyerSession)))

        /* Call finality flow to notarise the transaction and transfer payment on Solana */
        val stx = subFlow(FinalityFlow(ftx, listOf(buyerSession)))

        /* Distribution list is a list of identities that should receive updates. In this sample an observer node. */
        subFlow(UpdateDistributionListFlow(stx))

        return ("\nDvP is done, shares have been transferred to " + buyer.name.organisation + "\nTransaction ID: "
                + stx.id)
    }
}

/**
 * Buyer flow.
 */
@InitiatedBy(SharesDvP::class)
class SharesDvpResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        /* Receive the "ask" quantity and price (quote). */
        val (quantity, price) = counterpartySession.receive<Pair<Long,BigDecimal>>().unwrap { it }

        // The flow could be extended to check if the amount of tokens is available on Solana
        val totalPrice = price.multiply(quantity.toBigDecimal())

        /* Collect own Solana accounts for Solana transaction */
        val config = serviceHub.getAppContext().config
        val solanaTokenMint = Pubkey.fromBase58(config.getString("solanaTokenMint"))
        val solanaMintAuthority = Pubkey.fromBase58(config.getString("solanaWalletAccount"))
        val solanaSourceAccount = Pubkey.fromBase58(config.getString("solanaTokenAccount"))

        /* Send to seller to add payment data to a transaction */
        val payerDetails = SolanaPayer(solanaTokenMint, solanaMintAuthority, solanaSourceAccount)
        counterpartySession.send(payerDetails)

        /* Sign Corda transaction */
        subFlow(object : SignTransactionFlow(counterpartySession) {
            @Throws(FlowException::class)
            override fun checkTransaction(stx: SignedTransaction) {
                //TODO verify if e.g. price in the contract is as agreed because Responder hadn't built any part of Corda transaction
            }
        })
        return subFlow(ReceiveFinalityFlow(counterpartySession))
    }
}

@CordaSerializable
data class SolanaPayer(val tokenMint: Pubkey, val walletAccount: Pubkey, val tokenAccount: Pubkey)
