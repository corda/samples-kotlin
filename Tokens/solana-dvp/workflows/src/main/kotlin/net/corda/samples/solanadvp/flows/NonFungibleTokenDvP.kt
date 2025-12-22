package net.corda.samples.solanadvp.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveNonFungibleTokens
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
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
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.samples.solanadvp.contracts.PaymentContract
import net.corda.samples.solanadvp.states.DeliveryState
import net.corda.samples.solanadvp.states.PaymentState
import net.corda.solana.sdk.SplToken
import net.corda.solana.sdk.instruction.Pubkey
import java.util.Currency
import java.util.UUID

@InitiatingFlow
@StartableByRPC
class NonFungibleTokenDvP(
    val id: String,
    val buyer: Party
) : FlowLogic<String>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): String {
        // Obtain a reference from a notary we wish to use.
        val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB"))

        UUID.fromString(id)

        /* Fetch the state to deliver from the vault using the vault query */
        val inputCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(UniqueIdentifier.fromString(id)))
        val deliveryStateAndRef =
            serviceHub.vaultService.queryBy<DeliveryState>(criteria = inputCriteria).states.single()
        val deliveryState = deliveryStateAndRef.state.data

        /* Build the transaction builder */
        val txBuilder = TransactionBuilder(notary)

        /* Create a move token proposal for the token using the helper function provided by Token SDK. This would create the movement proposal and would
         * be committed in the ledgers of parties once the transaction in finalized.
        **/
        addMoveNonFungibleTokens(txBuilder, serviceHub, deliveryState.toPointer(deliveryState.javaClass), buyer)

        /* Initiate a flow session with the buyer to send the valuation and transfer of the fiat currency */
        val buyerSession = initiateFlow(buyer)

        // Send the valuation to the buyer.
        buyerSession.send(deliveryState.price)

        // Receive output for the fiat currency from the buyer, this would contain the transferred amount from buyer to yourself
        val payerDetails = buyerSession.receive<SolanaPayer>().unwrap { it }

        val amount = deliveryState.price.quantity

        val config = serviceHub.getAppContext().config
        val solanaTokenMint = Pubkey.fromBase58(config.getString("solanaTokenMint"))
        val solanaTokenMintDecimals = Integer.parseInt(config.getString("solanaTokenMintDecimals")).toByte()
        val solanaDestinationAccount = Pubkey.fromBase58(config.getString("solanaTokenAccount"))
        val solanaMintAuthority = payerDetails.walletAccount
        val solanaSourceAccount = payerDetails.tokenAccount
        require(payerDetails.tokenMint == solanaTokenMint)

        val output = PaymentState(
            deliveryState.linearId, ourIdentity, buyer,
            solanaDestinationAccount, solanaSourceAccount,
            solanaMintAuthority, solanaTokenMint,
            amount, solanaTokenMintDecimals
        )
        txBuilder.addOutputState(output, PaymentContract.ID)
            .addCommand(
                PaymentContract.Commands.Agree(),
                listOf(ourIdentity.owningKey, buyer.owningKey)
            )

        txBuilder.addNotaryInstruction(
            SplToken.transfer(
                solanaSourceAccount,
                solanaTokenMint, solanaDestinationAccount, solanaMintAuthority,
                amount, solanaTokenMintDecimals
            )
        )

        /* Sign the transaction with your private */
        val initialSignedTrnx = serviceHub.signInitialTransaction(txBuilder)

        /* Call the CollectSignaturesFlow to receive signature of the buyer */
        val ftx = subFlow(CollectSignaturesFlow(initialSignedTrnx, listOf(buyerSession)))

        /* Call finality flow to notarise the transaction */
        val stx = subFlow(FinalityFlow(ftx, listOf(buyerSession)))

        /* Distribution list is a list of identities that should receive updates. For this mechanism to behave correctly we call the UpdateDistributionListFlow flow */
        subFlow(UpdateDistributionListFlow(stx))

        return ("\nThe state is sold to " + buyer.name.organisation + "\nTransaction ID: "
                + stx.id)
    }
}

@InitiatedBy(NonFungibleTokenDvP::class)
class SaleResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
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