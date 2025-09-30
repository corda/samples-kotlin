package com.r3.corda.lib.tokens.bridging.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.bridging.contracts.BridgingContract
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.workflows.flows.move.AbstractMoveTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveTokens
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowSession
import net.corda.core.transactions.TransactionBuilder
import net.corda.solana.sdk.instruction.Pubkey

class BridgeFungibleTokensFlow
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
) : AbstractMoveTokensFlow() {

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