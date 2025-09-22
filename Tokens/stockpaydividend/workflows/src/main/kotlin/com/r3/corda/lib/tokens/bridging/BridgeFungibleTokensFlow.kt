package com.r3.corda.lib.tokens.bridging

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.bridging.contracts.BridgingContract
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.selection.TokenQueryBy
import com.r3.corda.lib.tokens.selection.database.selector.DatabaseTokenSelection
import com.r3.corda.lib.tokens.workflows.flows.move.AbstractMoveTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveTokens
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Amount.Companion.sumOrThrow
import net.corda.core.contracts.ContractState
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder
import net.corda.solana.sdk.instruction.Pubkey
import java.util.UUID

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
    val mintAuthority: Pubkey
) : AbstractMoveTokensFlow() {

    @Suspendable
    override fun addMove(transactionBuilder: TransactionBuilder) {

        //TODO move to other flow
        val criteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED)

        serviceHub.vaultService.queryBy(FungibleToken::class.java, criteria)

        val tokens = serviceHub.vaultService
            .queryBy(FungibleToken::class.java, criteria)
            .states
//            .filter { stateAndRef ->
//                val data = stateAndRef.state.data
//                val token = data.amount.token // IssuedTokenType
//                val tokenType = token.tokenType // TokenType
//                tokenType == FiatCurrency.getInstance("GBP") && data.holder == ourIdentity
//                // You can also check token.issuer == someIssuer if you want a specific issuer
//            }

        val token = tokens[0].state.data
        val holder = ourIdentity
        val output = FungibleToken(token.amount, holder)
        addMoveTokens(transactionBuilder = transactionBuilder, inputs = listOf(tokens[0]), outputs = listOf(output))

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