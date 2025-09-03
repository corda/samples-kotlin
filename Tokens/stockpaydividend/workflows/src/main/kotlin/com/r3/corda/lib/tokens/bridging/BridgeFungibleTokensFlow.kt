package com.r3.corda.lib.tokens.bridging

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.bridging.contracts.BridgingContract
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.flows.move.AbstractMoveTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import net.corda.core.contracts.ContractState
import net.corda.core.flows.FlowSession
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder
import net.corda.solana.sdk.instruction.Pubkey

//Follows flow moves fungible tokens and adds bridging command and output state to the transaction
class BridgeFungibleTokensFlow
@JvmOverloads
constructor(
    val partyAndAmount: PartyAndAmount<TokenType>,
    override val participantSessions: List<FlowSession>,
    override val observerSessions: List<FlowSession> = emptyList(),
    val additionalOutput: ContractState,
    val additionalCommand: BridgingContract.BridgingCommand,
    val destination: Pubkey,
    val mint: Pubkey,
    val mintAuthority: Pubkey,
    val queryCriteria: QueryCriteria? = null
) : AbstractMoveTokensFlow() {

    @Suspendable
    override fun addMove(transactionBuilder: TransactionBuilder) {
        addMoveFungibleTokens(
            transactionBuilder = transactionBuilder,
            serviceHub = serviceHub,
            partiesAndAmounts = listOf(partyAndAmount), //TODO change to Confidential Identity and change will be distributed to current holder (BI)
            changeHolder = ourIdentity,
            queryCriteria = queryCriteria
        )
        val quantity = partyAndAmount.amount.toDecimal().toLong() //TODO
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