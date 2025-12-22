package net.corda.samples.solanadvp.flows

import net.corda.core.utilities.OpaqueBytes
import net.corda.solana.sdk.instruction.Pubkey
import net.corda.solana.sdk.instruction.SolanaInstruction
import java.nio.ByteBuffer
import java.nio.ByteOrder

// TODO will be replaced by recreating instruction and comparing in contract verification
fun SolanaInstruction.requireMatchExceptDestinationAccount(
    sourceTokenAccount: Pubkey,
    walletAccount: Pubkey,
    mintAccount: Pubkey,
    amount: Long,
    expectedMintDecimals: Byte
) {
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