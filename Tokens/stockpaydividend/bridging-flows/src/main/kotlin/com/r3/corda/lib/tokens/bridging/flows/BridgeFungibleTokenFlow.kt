package com.r3.corda.lib.tokens.bridging.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.bridging.contracts.BridgingContract
import com.r3.corda.lib.tokens.bridging.states.BridgedAssetLockState
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.workflows.flows.move.AbstractMoveTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.move.MoveTokensFlowHandler
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveTokens
import com.r3.corda.lib.tokens.workflows.utilities.sessionsForParties
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByService
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.solana.sdk.instruction.Pubkey

/**
 * Initiating flow used to bridge token of the same party.
 *
 * @param observers optional observing parties to which the transaction will be broadcast
 */
@StartableByService
@InitiatingFlow
class BridgeFungibleTokenFlow(
    val holder: AbstractParty,
    val observers: List<Party> = emptyList(),
    val token: StateAndRef<FungibleToken>, //TODO should be FungibleToken, TODO change to any TokenType would need amendments to UUID retrieval below
    val bridgeAuthority: Party
) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val participants = listOf(holder)
        val observerSessions = sessionsForParties(observers)
        val participantSessions = sessionsForParties(participants)

        val additionalOutput: ContractState = BridgedAssetLockState(listOf(ourIdentity))

        val cordaTokenId = (token.state.data.amount.token.tokenType as TokenPointer<*>).pointer.pointer.id

        val owners = previousOwnersOf(token).map { serviceHub.identityService.wellKnownPartyFromAnonymous(it) ?: it }
        val singlePreviousOwner = owners.singleOrNull { it is Party } as Party?
        require(singlePreviousOwner != null) {
            "Cannot find previous owner of the token to bridge, or multiple found: $owners"
        }
        val solanaAccountMapping = serviceHub.cordaService(SolanaAccountsMappingService::class.java)
        val destination =
            solanaAccountMapping.participants[singlePreviousOwner.name]!! //TODO handle null
        val mint = solanaAccountMapping.mints[cordaTokenId]!! //TODO handle null
        val mintAuthority = solanaAccountMapping.mintAuthorities[cordaTokenId]!! //TODO handle null
        val additionalCommand = BridgingContract.BridgingCommand.BridgeToSolana(
            destination,
            bridgeAuthority
        )
        logger.info("Generating Solana bridging transaction with the following parameters: mint=$mint, mintAuthority=$mintAuthority, destination=$destination.")
        return subFlow(
            InternalBridgeFungibleTokenFlow(
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

    fun previousOwnersOf(output: StateAndRef<FungibleToken>): Set<AbstractParty> {
        val txHash = output.ref.txhash
        val stx = serviceHub.validatedTransactions.getTransaction(txHash)
            ?: error("Producing transaction $txHash not found")

        val inputTokens: List<FungibleToken> =
            stx.toLedgerTransaction(serviceHub).inputsOfType<FungibleToken>()

        return inputTokens.map { it.holder }.toSet()
    }
}

/**
 * Responder flow for [BridgeFungibleTokenFlow].
 */
@InitiatedBy(BridgeFungibleTokenFlow::class)
class BridgeFungibleTokensHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(MoveTokensFlowHandler(otherSession))
}

class InternalBridgeFungibleTokenFlow
@JvmOverloads
constructor(
    override val participantSessions: List<FlowSession>,
    override val observerSessions: List<FlowSession> = emptyList(),
    val token: StateAndRef<FungibleToken>,
    val additionalOutput: ContractState,
    val additionalCommand: BridgingContract.BridgingCommand,
    val destination: Pubkey,
    val mint: Pubkey,
    val mintAuthority: Pubkey
) : AbstractMoveTokensFlow() { //TODO move away from this abstract class, it's progress tracker mention only token move

    @Suspendable
    override fun addMove(transactionBuilder: TransactionBuilder) {

        val amount = token.state.data.amount
        val holder = ourIdentity //TODO confidential identity
        val output = FungibleToken(amount, holder)
        addMoveTokens(transactionBuilder = transactionBuilder, inputs = listOf(token), outputs = listOf(output))

        val quantity = amount.toDecimal()
            .toLong() //TODO this is quantity for Solana, should it be 1 to 1 what is bridged on Corda?
        bridgeToken(
            serviceHub,
            transactionBuilder,
            additionalOutput,
            additionalCommand,
            destination,
            mint,
            mintAuthority,
            quantity
        )
    }
}
