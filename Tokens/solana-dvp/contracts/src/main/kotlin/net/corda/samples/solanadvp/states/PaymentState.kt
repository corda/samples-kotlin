package net.corda.samples.solanadvp.states

import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.samples.solanadvp.contracts.PaymentContract
import net.corda.solana.sdk.instruction.Pubkey

/**
 * A generic receipt asset state (either NonFungibleToken or a linear state).
 */
@BelongsToContract(PaymentContract::class)
data class PaymentState(
    val assetId: UniqueIdentifier,
    val seller: Party,
    val buyer: Party,
    val solanaSellerTokenAccount: Pubkey,
    val solanaBuyerTokenAccount: Pubkey,
    val solanaMintAuthority: Pubkey,
    val solanaTokenMint: Pubkey,
    val quantity: Long,
    val decimals: Byte,
) : ContractState {
    override val participants: List<AbstractParty> = listOf(seller, buyer)
}