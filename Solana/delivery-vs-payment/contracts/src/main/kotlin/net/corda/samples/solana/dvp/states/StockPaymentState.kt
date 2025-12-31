package net.corda.samples.solana.dvp.states

import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.samples.solana.dvp.contracts.StockPaymentContract
import net.corda.solana.sdk.instruction.Pubkey

/**
 * Receipt of payment for amount of FungibleTokens.
 */
@BelongsToContract(StockPaymentContract::class)
data class StockPaymentState(
    val assetsAmount: Amount<TokenType>,
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