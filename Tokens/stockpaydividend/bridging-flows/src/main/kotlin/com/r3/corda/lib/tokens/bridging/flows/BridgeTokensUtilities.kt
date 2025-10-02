package com.r3.corda.lib.tokens.bridging.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.bridging.contracts.BridgingContract
import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.TransactionBuilder
import net.corda.solana.sdk.instruction.Pubkey
import net.corda.solana.sdk.internal.Token2022
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

/**
 * Adds a set of bridging commands to a transaction using specific outputs.
 */
@Suspendable
fun bridgeTokens(
    serviceHub: ServiceHub,
    transactionBuilder: TransactionBuilder,
    additionalOutput: List<ContractState>,
    additionalCommand: BridgingContract.BridgingCommand,
    destination: Pubkey,
    mint: Pubkey,
    mintAuthority: Pubkey,
    quantity: Long
): TransactionBuilder {
    transactionBuilder.inputStates()
    val outputGroups: Map<IssuedTokenType, List<AbstractToken>> =
        transactionBuilder.outputStates()
            .map { it.data }
            .filterIsInstance<AbstractToken>()
            .groupBy { it.issuedTokenType }
    val inputGroups: Map<IssuedTokenType, List<StateAndRef<AbstractToken>>> =
        transactionBuilder.inputStates()
            .map { serviceHub.toStateAndRef<AbstractToken>(it) }
            .groupBy { it.state.data.issuedTokenType }

    check(outputGroups.keys == inputGroups.keys) {
        "Input and output token types must correspond to each other when moving tokensToIssue"
    }

    transactionBuilder.apply {
        outputGroups.forEach { (issuedTokenType: IssuedTokenType, _: List<AbstractToken>) ->
            val inputGroup = inputGroups[issuedTokenType]
                ?: throw IllegalArgumentException("No corresponding inputs for the outputs issued token type: $issuedTokenType")
            val keys = inputGroup.map { it.state.data.holder.owningKey }
            additionalOutput.map {
                addOutputState(it)
            }
            addCommand(additionalCommand, keys)
        }
    }
    if (additionalCommand is BridgingContract.BridgingCommand.BridgeToSolana) {
        val instruction = Token2022.mintTo(mint, destination, mintAuthority, quantity)
        transactionBuilder.addNotaryInstruction(instruction)
    }

    return transactionBuilder
}

/**
 * Adds a single bridging command to a transaction.
 */
@Suspendable
fun bridgeToken(
    serviceHub: ServiceHub,
    transactionBuilder: TransactionBuilder,
    additionalOutput: ContractState,
    additionalCommand: BridgingContract.BridgingCommand,
    destination: Pubkey,
    mint: Pubkey,
    mintAuthority: Pubkey,
    quantity: Long
): TransactionBuilder {
    return bridgeTokens(
        serviceHub, transactionBuilder, listOf(additionalOutput),
        additionalCommand, destination, mint, mintAuthority, quantity
    )
}