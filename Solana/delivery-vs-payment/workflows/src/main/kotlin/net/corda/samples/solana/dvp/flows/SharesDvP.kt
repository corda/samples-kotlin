package net.corda.samples.solana.dvp.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
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
import net.corda.core.solana.Pubkey
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.notary.solana.toPubkey
import net.corda.samples.solana.dvp.contracts.SharesPaymentContract
import net.corda.samples.solana.dvp.states.SharesPaymentState
import net.corda.samples.solana.dvp.states.StockState
import net.corda.solana.sdk.SplToken
import java.math.BigDecimal

/**
 * Delivery versus Payment exchange is initialized as "ask" from Seller to be approved by Buyer.
 * The flow omits negotiation phase or "bid" style of buy as this is irrelevant for the sample point of view.
 * Seller flow initiates exchange.
 * Seller selects the shares (by symbol) and the quantity to sell on Corda network, and sends the buyer a quote: “X shares at Y price each.”
 * The amount paid on Solana is 1 to 1 derived from asset price and quantity (X*Y).
 * Buyer replies with the Solana payment details needed to pay (which stablecoin, and which Solana account will pay from).
 *
 * Seller builds a single Corda transaction deal that includes:
 * Delivery: shares move to the buyer on the Corda network.
 * Payment: an instruction for the notary to execute an SPL token transfer on Solana from buyer to seller for the agreed amount.
 *
 * Both parties approve, then the notary finalizes the transaction: payment is executed on Solana,
 * shares are delivered on Corda (by Corda Fungible Token move), and the transaction is recorded as complete.
 */
@InitiatingFlow
@StartableByRPC
class SharesDvP(
    val symbol: String,
    val quantity: Long,
    val buyer: Party,
    val notaryName: CordaX500Name
) : FlowLogic<String>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): String {
        /* Obtain a reference from a notary we wish to use */
        val notary = serviceHub.networkMapCache.getNotary(notaryName)

        /* Fetch the state to deliver from the vault using the vault query */
        val stockPointer: TokenPointer<StockState> = StockQueryUtilities.queryStockPointer(symbol, serviceHub)
        val stockAmount: Amount<TokenType> = Amount(quantity, stockPointer)

        val txBuilder = TransactionBuilder(notary)

        /* Create a move token proposal for the shares to deliver using the helper function provided by Token SDK */
        addMoveFungibleTokens(
            txBuilder,
            serviceHub,
            listOf(PartyAndAmount(buyer, stockAmount)),
            ourIdentity
        )

        /* Initiate a flow session with the buyer to send the valuation and transfer of the fiat currency */
        val buyerSession = initiateFlow(buyer)

        /* Send the valuation to the buyer. */
        val stockState = stockPointer.pointer.resolve(serviceHub).state.data
        buyerSession.send(Pair(quantity, stockState.price))

        /* Receive Solana accounts of the payer (buyer) */
        val payerDetails = buyerSession.receive<SolanaPayer>().unwrap { it }

        /* Collect own Solana accounts for Solana transaction and lookup for decimals (required for a checked transfer) */
        val config = serviceHub.getAppContext().config
        val stablecoinTokenMint = Pubkey.fromBase58(config.getString("stablecoinTokenMint"))
        require(payerDetails.tokenMint == stablecoinTokenMint) { "Payer provided an account for different tokenMint (stablecoin)." }
        val solanaService = serviceHub.cordaService(SolanaService::class.java)
        val solanaTokenMintDecimals = solanaService.getAccountMintDecimals(stablecoinTokenMint)
        val solanaDestinationAccount = solanaService.createAta(stablecoinTokenMint.toPublicKey()).toPubkey()

        val solanaMintAuthority = payerDetails.walletAccount
        val solanaSourceAccount = payerDetails.tokenAccount

        /* Set price in long format for Solana transaction */
        val stableCoinAmount = stockState.price.multiply(quantity.toBigDecimal())
        val stableCoinAmountAsLong = stableCoinAmount.toScaledLong(solanaTokenMintDecimals)
        val solanaTokenMintDecimalsAsByte = solanaTokenMintDecimals.toByte()

        /* Create Corda state with payment details */
        val sharesPaymentState = SharesPaymentState(
            stockAmount,
            ourIdentity,
            buyer,
            solanaDestinationAccount,
            solanaSourceAccount,
            solanaMintAuthority,
            stablecoinTokenMint,
            stableCoinAmountAsLong,
            solanaTokenMintDecimalsAsByte
        )

        txBuilder.addOutputState(sharesPaymentState, SharesPaymentContract.ID)
            .addCommand(
                SharesPaymentContract.Commands.Agree(),
                listOf(ourIdentity.owningKey, buyer.owningKey)
            )

        /* Create Solana transfer instruction that will be run by Corda Notary */
        txBuilder.addNotaryInstruction(
            SplToken.transferChecked(
                solanaSourceAccount, stablecoinTokenMint, solanaDestinationAccount, solanaMintAuthority,
                sharesPaymentState.stablecoinAmount, sharesPaymentState.stablecoinDecimals
            )
        )

        /* Sign the transaction with your private key */
        val initialSignedTx = serviceHub.signInitialTransaction(txBuilder)

        /* Call the CollectSignaturesFlow to receive signature of the buyer */
        val ftx = subFlow(CollectSignaturesFlow(initialSignedTx, listOf(buyerSession)))

        /* Call finality flow to notarise the transaction and transfer payment on Solana */
        val stx = subFlow(FinalityFlow(ftx, listOf(buyerSession)))

        /* Distribution list is a list of identities that should receive updates; in this sample this is the observer node */
        subFlow(UpdateDistributionListFlow(stx))

        return ("\nDvP is done, shares have been transferred to " + buyer.name.organisation + "\nTransaction ID: "
                + stx.id)
    }
}

/**
 * Buyer flow. Buyer receives the quote (quantity + price) and calculates the total amount to pay.
 * Buyer sends the seller the required Solana payment coordinates (stablecoin + paying account).
 * Before approving transaction received from seller, buyer checks the final deal matches what was agreed:
 * Payment amount and accounts are exactly as expected on Solana,
 * Shares quantity delivered to the buyer matches the agreed quantity.
 * If everything matches, buyer approves and records the completed exchange.
 */
@InitiatedBy(SharesDvP::class)
class SharesDvpResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        /* Receive the "ask" quantity and price (quote). */
        val (quantity, price) = counterpartySession.receive<Pair<Long, BigDecimal>>().unwrap { it }

        val solanaPaymentAmount = price.multiply(quantity.toBigDecimal())

        /* Collect own Solana accounts for Solana transaction */
        val config = serviceHub.getAppContext().config
        val stablecoinTokenMint = Pubkey.fromBase58(config.getString("stablecoinTokenMint"))

        val solanaService = serviceHub.cordaService(SolanaService::class.java)
        // The ATA should be already created and funded, otherwise the buyer has no stablecoins to spent
        val solanaSourceAccount = solanaService.deriveAtaAddress(stablecoinTokenMint).toPubkey()
        // The flow could be extended to check if the amount of tokens is available on Solana

        /* Send to seller to add payment data to a transaction */
        val payerDetails = SolanaPayer(stablecoinTokenMint, solanaService.getMyWalletAddress(), solanaSourceAccount)
        counterpartySession.send(payerDetails)

        /* Verify and sign Corda transaction */
        subFlow(object : SignTransactionFlow(counterpartySession) {
            @Throws(FlowException::class)
            override fun checkTransaction(stx: SignedTransaction) {
                /* Verify if transaction details provided by a seller match the agreed details in the flow earlier,
                 * because a buyer (responder) hadn't built any part of Corda transaction. Buyer doesn't verify
                 * a destination account to pay stablecoins to as it's a seller interest to provide it correctly.
                 * */
                val paymentStates = stx.coreTransaction.outputsOfType(SharesPaymentState::class.java)
                require(paymentStates.size == 1) { "Received transaction to sign without payment details" }
                val paymentState = paymentStates.first()
                val solanaTokenMintDecimals = solanaService.getAccountMintDecimals(stablecoinTokenMint)
                val expectedSolanaPaymentAmount = solanaPaymentAmount.toScaledLong(solanaTokenMintDecimals)
                require(paymentState.stablecoinAmount == expectedSolanaPaymentAmount) {
                    "Payment amount ${paymentState.stablecoinAmount} " +
                            "doesn't match the agreed amount of $expectedSolanaPaymentAmount."
                }
                require(paymentState.solanaBuyerTokenAccount == solanaSourceAccount) {
                    "Payment is not transferred from my token account, expected $solanaSourceAccount," +
                            " received ${paymentState.solanaBuyerTokenAccount}"
                }
                require(paymentState.solanaBuyerWalletAccount == solanaService.getMyWalletAddress()) {
                    "Payment is not signed by my account, expected $solanaSourceAccount," +
                            " received ${paymentState.solanaBuyerTokenAccount}"
                }
                require(paymentState.solanaStablecoin == stablecoinTokenMint) {
                    "Payment was agreed on different token mint, expected $stablecoinTokenMint, received ${paymentState.solanaStablecoin}"
                }

                /* Validity of Notary Instruction (to perform Solana transfer) is verified in SharesPaymentContract  */

                val receivedAssets = stx.coreTransaction.outputsOfType(FungibleToken::class.java).filter {
                    it.holder == ourIdentity
                }
                require(receivedAssets.isNotEmpty()) { "No assets delivered" }
                // TODO The code below is simplified, it assumes all states are of the same Corda type
                val receivedAssetsQuantity = receivedAssets.sumOf { it.amount.quantity }
                require(receivedAssetsQuantity == quantity) {
                    "Quantity of delivered assets differs, expected $quantity, received $receivedAssetsQuantity"
                }
            }
        })
        return subFlow(ReceiveFinalityFlow(counterpartySession))
    }
}

@CordaSerializable
data class SolanaPayer(val tokenMint: Pubkey, val walletAccount: Pubkey, val tokenAccount: Pubkey)
