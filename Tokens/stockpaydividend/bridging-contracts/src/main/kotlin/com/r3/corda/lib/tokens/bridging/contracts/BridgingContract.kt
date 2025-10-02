package com.r3.corda.lib.tokens.bridging.contracts

import com.lmax.solana4j.programs.TokenProgramBase
import com.r3.corda.lib.tokens.bridging.states.BridgedAssetLockState
import com.r3.corda.lib.tokens.contracts.commands.MoveTokenCommand
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.solana.sdk.instruction.Pubkey
import net.corda.solana.sdk.instruction.SolanaInstruction
import net.corda.solana.sdk.internal.Token2022
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BridgingContract : Contract {
    override fun verify(tx: LedgerTransaction) {
        val bridgingCommands = tx.commandsOfType<BridgingCommand>()

        require(bridgingCommands.size == 1) { "Bridging transactions must have single bridging command" }

        when (val bridgingCommand = bridgingCommands.single().value) {
            is BridgingCommand.BridgeToSolana -> verifyBridging(tx, bridgingCommand)
        }
    }

    private fun verifyBridging(tx: LedgerTransaction, bridgingCommand: BridgingCommand.BridgeToSolana) {
        val lockState = tx.outputsOfType<BridgedAssetLockState>().singleOrNull()

        require(lockState != null) { "Bridging transaction must have exactly one BridgedAssetLockstate as output" }

        require(lockState.participants.single() == bridgingCommand.bridgeAuthority) { "BridgedAssetLockstate must be owned by bridging authority" }

        val moveCommands = tx.commandsOfType<MoveTokenCommand>()

        require(moveCommands.size == 1) { "Bridging must have one move command to lock token" }

        val lockedSum = tx.outputsOfType<FungibleToken>()
            .filter { it.holder == bridgingCommand.bridgeAuthority } // TODO this is mute point for now, change to != bridgeAuthority, to filter only states owned by CI ...
            // ... currently can't distinguish between locked and a change, both are for same holder
            .sumOf {
                it.amount.toDecimal().toLong()
            }

        val instruction = tx.notaryInstructions.singleOrNull() as? SolanaInstruction

        require(instruction != null) { "Exactly one Solana mint instruction required" }

        require(instruction.programId == Token2022.PROGRAM_ID) { "Solana program id must be Token2022 program" }

        require(instruction.accounts[1].pubkey == bridgingCommand.targetAddress) { "Target in instructions does not match command" }

        @Suppress("MagicNumber")
        require(instruction.data.size == 9) { "Expecting 9 bytes of instruction data" }

        val instructionBytes = ByteBuffer.wrap(instruction.data.bytes).order(ByteOrder.LITTLE_ENDIAN)

        val tokenInstruction = instructionBytes.get().toInt()

        val amount = instructionBytes.getLong()
        require(tokenInstruction == TokenProgramBase.MINT_TO_INSTRUCTION) { "Token instruction must be MINT_TO_INSTRUCTION" }

        require(amount == lockedSum) { "Locked amount of $lockedSum must match requested mint amount $amount." }
    }

    sealed interface BridgingCommand : CommandData {

        data class BridgeToSolana(val targetAddress: Pubkey, val bridgeAuthority: Party) : BridgingCommand
    }
}