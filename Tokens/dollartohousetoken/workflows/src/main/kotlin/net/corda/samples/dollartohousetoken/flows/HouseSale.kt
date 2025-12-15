package net.corda.samples.dollartohousetoken.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.money.FiatCurrency.Companion.getInstance
import com.r3.corda.lib.tokens.selection.database.selector.DatabaseTokenSelection
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveNonFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveTokens
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
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.samples.dollartohousetoken.contracts.SaleContract
import net.corda.samples.dollartohousetoken.states.HouseState
import net.corda.samples.dollartohousetoken.states.SaleState
import net.corda.solana.sdk.instruction.Pubkey
import net.corda.solana.sdk.instruction.SolanaInstruction
import net.corda.solana.sdk.SplToken
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC
class HouseSale(val houseId: String,
                val buyer: Party) : FlowLogic<String>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call():String {
        // Obtain a reference from a notary we wish to use.
        val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB"))

        UUID.fromString(houseId)

        /* Fetch the house state from the vault using the vault query */
        val inputCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(UniqueIdentifier.fromString(houseId)))
        val houseStateAndRef = serviceHub.vaultService.queryBy<HouseState>(criteria = inputCriteria).states.single()
        val houseState = houseStateAndRef.state.data

        /* Build the transaction builder */
        val txBuilder = TransactionBuilder(notary)

        /* Create a move token proposal for the house token using the helper function provided by Token SDK. This would create the movement proposal and would
         * be committed in the ledgers of parties once the transaction in finalized.
        **/
        addMoveNonFungibleTokens(txBuilder, serviceHub, houseState.toPointer(houseState.javaClass), buyer)

        /* Initiate a flow session with the buyer to send the house valuation and transfer of the fiat currency */
        val buyerSession = initiateFlow(buyer)

        // Send the house valuation to the buyer.
        buyerSession.send(houseState.valuationOfHouse)

        // Receive inputStatesAndRef for the fiat currency exchange from the buyer, these would be inputs to the fiat currency exchange transaction.
        //val inputs = subFlow(ReceiveStateAndRefFlow<FungibleToken>(buyerSession))

        // Receive output for the fiat currency from the buyer, this would contain the transferred amount from buyer to yourself
        val payerDetails = buyerSession.receive<SolanaPayer>().unwrap { it }

        val output = SaleState( houseState.linearId, ourIdentity, buyer)
         txBuilder.addOutputState(output, SaleContract.ID)
            .addCommand(
                SaleContract.Commands.Agree(),
                listOf(ourIdentity.owningKey, buyer.owningKey)
            )

        val config = serviceHub.getAppContext().config
        val solanaTokenMint = Pubkey.fromBase58(config.getString("solanaTokenMint"))
        val solanaTokenMintDecimals = Integer.parseInt(config.getString("solanaTokenMintDecimals"))
        val solanaDestinationAccount = Pubkey.fromBase58(config.getString("solanaTokenAccount"))
        val solanaMintAuthority = payerDetails.walletAccount
        val solanaSourceAccount = payerDetails.tokenAccount
        require(payerDetails.tokenMint  == solanaTokenMint)

        val amount = houseState.valuationOfHouse.quantity
        txBuilder.addNotaryInstruction(SplToken.transfer(solanaSourceAccount,
            solanaTokenMint, solanaDestinationAccount, solanaMintAuthority,
            amount, solanaTokenMintDecimals.toByte()))

        /* Sign the transaction with your private */
        val initialSignedTrnx = serviceHub.signInitialTransaction(txBuilder)

        /* Call the CollectSignaturesFlow to receive signature of the buyer */
        val ftx = subFlow(CollectSignaturesFlow(initialSignedTrnx, listOf(buyerSession)))

        /* Call finality flow to notarise the transaction */
        val stx = subFlow(FinalityFlow(ftx, listOf(buyerSession)))

        /* Distribution list is a list of identities that should receive updates. For this mechanism to behave correctly we call the UpdateDistributionListFlow flow */
        subFlow(UpdateDistributionListFlow(stx))

        return ("\nThe house is sold to " + buyer.name.organisation + "\nTransaction ID: "
                + stx.id)
    }
}

@InitiatedBy(HouseSale::class)
class HouseSaleResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call():SignedTransaction {
        /* Receive the valuation of the house */
        val price = counterpartySession.receive<Amount<Currency>>().unwrap { it }

        /* Create instance of the fiat currency token amount */
        //val priceToken = Amount(price.quantity, getInstance(price.token.currencyCode))

        /* Generate the move proposal, it returns the input-output pair for the fiat currency transfer, which we need to send to the Initiator. */
        //PartyAndAmount(counterpartySession.counterparty,priceToken)
        //val inputsAndOutputs : Pair<List<StateAndRef<FungibleToken>>, List<FungibleToken>> =
        //        DatabaseTokenSelection(serviceHub).generateMove(listOf(Pair(counterpartySession.counterparty,priceToken)),ourIdentity)

        /* Call SendStateAndRefFlow to send the inputs to the Initiator*/
        //subFlow(SendStateAndRefFlow(counterpartySession, inputsAndOutputs.first))
        /* Send the output generated from the fiat currency move proposal to the initiator */
        //counterpartySession.send(inputsAndOutputs.second)

        val config = serviceHub.getAppContext().config
        val solanaTokenMint = Pubkey.fromBase58(config.getString("solanaTokenMint"))
        val solanaMintAuthority = Pubkey.fromBase58(config.getString("solanaMintAuthority"))
        val solanaSourceAccount = Pubkey.fromBase58(config.getString("solanaTokenAccount"))

        val payerDetails = SolanaPayer(solanaTokenMint, solanaMintAuthority, solanaSourceAccount)
        counterpartySession.send(payerDetails)

        //signing
        subFlow(object : SignTransactionFlow(counterpartySession) {
            @Throws(FlowException::class)
            override fun checkTransaction(stx: SignedTransaction) {
                val notaryInstructions = stx.tx.notaryInstructions
                require(notaryInstructions.isNotEmpty()) { "Expected a notary instruction" }
                require(notaryInstructions.size == 1) { "Expected single notary instruction" }
                val instruction = notaryInstructions.first()
                require( instruction is SolanaInstruction) { "Expected Solana notary instruction" }
                val solanaTokenMintDecimals = Integer.parseInt(config.getString("solanaTokenMintDecimals"))
                instruction.isEqualTo(solanaSourceAccount,
                    solanaMintAuthority,
                    solanaTokenMint,
                    price.quantity,
                    solanaTokenMintDecimals.toByte())
            }
        })
        return subFlow(ReceiveFinalityFlow(counterpartySession))
    }
}

@CordaSerializable
data class SolanaPayer(val tokenMint: Pubkey, val walletAccount: Pubkey, val tokenAccount: Pubkey)


fun SolanaInstruction.isEqualTo(sourceTokenAccount: Pubkey,
                                       walletAccount: Pubkey,
                                       mintAccount: Pubkey,
                                       amount: Long,
                                       expectedMintDecimals: Byte) {
    require(this.accounts.size == 4) { "Missing accounts" }
    require(this.accounts[0].pubkey == sourceTokenAccount) { "Wrong source account" }
    require(this.accounts[1].pubkey == mintAccount) { "Wrong mint account" }
    require(this.accounts[3].pubkey == walletAccount) { "Wrong source wallet account" }
    val expectedData = ByteBuffer.allocate(10)
        .order(ByteOrder.LITTLE_ENDIAN)
        .put(12)
        .putLong(amount)
        .put(expectedMintDecimals)
        .array()
    require(this.data == OpaqueBytes(expectedData)) { "Instruction data does not match expected data" }
}