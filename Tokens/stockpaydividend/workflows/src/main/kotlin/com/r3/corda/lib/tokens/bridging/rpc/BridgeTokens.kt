package com.r3.corda.lib.tokens.bridging.rpc

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.flows.move.MoveTokensFlowHandler
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import com.r3.corda.lib.tokens.workflows.utilities.sessionsForParties
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import com.r3.corda.lib.tokens.bridging.BridgeFungibleTokensFlow
import com.r3.corda.lib.tokens.bridging.contracts.BridgingContract
import com.r3.corda.lib.tokens.bridging.states.BridgedAssetLockState
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import net.corda.core.utilities.ProgressTracker
import net.corda.samples.stockpaydividend.flows.utilities.QueryUtilities
import net.corda.samples.stockpaydividend.states.StockState
import net.corda.solana.sdk.instruction.Pubkey

@InitiatingFlow
@StartableByRPC
class BridgeStock(
    val symbol: String,
    val quantity: Long,
    val bridgeAuthority: Party,
    val destination: Pubkey,
    val mint: Pubkey,
    val mintAuthority: Pubkey
) : FlowLogic<String>() {

    constructor(
        symbol: String,
        quantity: Long,
        bridgeAuthority: Party,
        destination: String,
        mint: String,
        mintAuthority: String
    ) : this(
        symbol,
        quantity,
        bridgeAuthority,
        Pubkey.fromBase58(destination),
        Pubkey.fromBase58(mint),
        Pubkey.fromBase58(mintAuthority)
    )

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): String {
        // To get the transferring stock, we can get the StockState from the vault and get it's pointer
        val stockPointer: TokenPointer<StockState> = QueryUtilities.queryStockPointer(symbol, serviceHub)

        // With the pointer, we can get the create an instance of transferring Amount
        val amount: Amount<TokenType> = Amount(quantity, stockPointer)

        val additionalOutput: ContractState = BridgedAssetLockState(listOf(ourIdentity))
        val additionalCommand = BridgingContract.BridgingCommand.BridgeToSolana(
            destination,
            bridgeAuthority
        ) //, ourIdentity.owningKey, bridgeAuthority.owningKey)
        //Use built-in flow for move tokens to the recipient
        val stx = subFlow(
            BridgeFungibleTokens(
                amount,
                ourIdentity,
                additionalOutput,
                additionalCommand,
                destination,
                mint,
                mintAuthority
            )
        )

        return ("\nBridged " + quantity + " " + symbol + " stocks to "
                + ourIdentity.name.organisation + ".\nTransaction ID: " + stx.id)
    }
}

/**
 * Initiating flow used to bridge amounts of tokens of the same party, [partyAndAmount] specifies what amount of tokens is bridged to a participant.
 *
 * Call this for one [TokenType] at a time.
 *
 * @param partyAndAmount pairing party - amount of token that is to be moved to that party
 * @param observers optional observing parties to which the transaction will be broadcast
 * @param queryCriteria additional criteria for token selection
 */
@StartableByService
@StartableByRPC
@InitiatingFlow
class BridgeFungibleTokens
@JvmOverloads
constructor(
    val partyAndAmount: PartyAndAmount<TokenType>,
    val observers: List<Party> = emptyList(),
    val additionalOutput: ContractState,
    val additionalCommand: BridgingContract.BridgingCommand,
    val destination: Pubkey,
    val mint: Pubkey,
    val mintAuthority: Pubkey,
    val queryCriteria: QueryCriteria? = null
) : FlowLogic<SignedTransaction>() {

    constructor(
        amount: Amount<TokenType>, holder: AbstractParty, additionalOutput: ContractState,
        additionalCommand: BridgingContract.BridgingCommand, destination: Pubkey, mint: Pubkey, mintAuthority: Pubkey
    )
            : this(
        PartyAndAmount(holder, amount),
        emptyList(),
        additionalOutput,
        additionalCommand,
        destination,
        mint,
        mintAuthority
    )

    @Suspendable
    override fun call(): SignedTransaction {
        val participants = listOf(partyAndAmount.party)
        val observerSessions = sessionsForParties(observers)
        val participantSessions = sessionsForParties(participants)
        return subFlow(
            BridgeFungibleTokensFlow(
                partyAndAmount = partyAndAmount,
                participantSessions = participantSessions,
                observerSessions = observerSessions,
                additionalOutput = additionalOutput,
                additionalCommand = additionalCommand,
                destination = destination,
                mint = mint,
                mintAuthority = mintAuthority,
                queryCriteria = queryCriteria
            )
        )
    }
}

/**
 * Responder flow for [BridgeFungibleTokens].
 */
@InitiatedBy(BridgeFungibleTokens::class)
class BridgeFungibleTokensHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(MoveTokensFlowHandler(otherSession))
}
