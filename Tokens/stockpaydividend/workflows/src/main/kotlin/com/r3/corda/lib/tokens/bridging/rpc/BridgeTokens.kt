package com.r3.corda.lib.tokens.bridging.rpc

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.workflows.flows.move.MoveTokensFlowHandler
import com.r3.corda.lib.tokens.workflows.utilities.sessionsForParties
import net.corda.core.contracts.ContractState
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import com.r3.corda.lib.tokens.bridging.BridgeFungibleTokensFlow
import com.r3.corda.lib.tokens.bridging.SolanaAccountsMappingService
import com.r3.corda.lib.tokens.bridging.contracts.BridgingContract
import com.r3.corda.lib.tokens.bridging.states.BridgedAssetLockState
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import net.corda.core.contracts.StateAndRef
import net.corda.core.utilities.ProgressTracker
import net.corda.samples.stockpaydividend.states.StockState
import com.r3.corda.lib.tokens.workflows.utilities.tokenAmountsByToken

@InitiatingFlow
@StartableByRPC
class BridgeStock(
    val symbol: String,
    val quantity: Long,
    val bridgeAuthority: Party
) : FlowLogic<String>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): String {

        //TODO this simulates getting updates from vault
        val page =
            serviceHub.vaultService.queryBy(StockState::class.java) //TODO + UNCONSUMED query  and belonging to our identity
        val states = page.states.filter { it.state.data.symbol == symbol }
        val pointer: TokenPointer<StockState> = states.map { it.state.data.toPointer(StockState::class.java) }.first()
        val token: StateAndRef<FungibleToken> = serviceHub.vaultService.tokenAmountsByToken(pointer).states.first()

        //Use built-in flow for move tokens to the recipient
        val stx = subFlow(
            BridgeFungibleTokens(
                ourIdentity, //TODO confidentialIdentity
                emptyList(),
                token,
                bridgeAuthority
            )
        )

        return ("\nBridged " + quantity + " " + symbol + " stocks to "
                + ourIdentity.name.organisation + ".\nTransaction ID: " + stx.id)
    }
}

/**
 * Initiating flow used to bridge token of the same party.
 *
 * @param observers optional observing parties to which the transaction will be broadcast
 */
@StartableByService
@InitiatingFlow
class BridgeFungibleTokens //TODO move away from RPC package
@JvmOverloads
constructor(
    val holder: AbstractParty,
    val observers: List<Party> = emptyList(),
    val token: StateAndRef<FungibleToken>, //TODO should be FungibleToken, TODO change to any TokenType would need amendments to UUID retrieval below
    val bridgeAuthority: Party
) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val participants = listOf(holder)  //TODO add confidentialIdentity
        val observerSessions = sessionsForParties(observers)
        val participantSessions = sessionsForParties(participants)

        val additionalOutput: ContractState = BridgedAssetLockState(listOf(ourIdentity))

        val cordaTokenId = (token.state.data.amount.token.tokenType as TokenPointer<*>).pointer.pointer.id

        val solanaAccountMapping = serviceHub.cordaService(SolanaAccountsMappingService::class.java)
        val destination = solanaAccountMapping.participants[ourIdentity.name]!! //TODO handle null, TODo eliminate this
        val mint = solanaAccountMapping.mints[cordaTokenId]!! //TODO handle null
        val mintAuthority = solanaAccountMapping.mintAuthorities[cordaTokenId]!! //TODO handle null
        val additionalCommand = BridgingContract.BridgingCommand.BridgeToSolana(
            destination,
            bridgeAuthority
        )

        return subFlow(
            BridgeFungibleTokensFlow(
                participantSessions = participantSessions,
                observerSessions = observerSessions,
                token = token,
                additionalOutput = additionalOutput,
                additionalCommand = additionalCommand,
                destination = destination,
                mint = mint,
                mintAuthority = mintAuthority
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
