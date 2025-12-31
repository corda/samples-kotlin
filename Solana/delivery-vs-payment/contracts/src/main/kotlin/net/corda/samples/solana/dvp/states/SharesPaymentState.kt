package net.corda.samples.solana.dvp.states

import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.samples.solana.dvp.contracts.SharesPaymentContract
import net.corda.solana.sdk.instruction.Pubkey

/**
 * Payment details on Solana for amount of exchanged shares on Corda.
 */
@BelongsToContract(SharesPaymentContract::class)
data class SharesPaymentState(
    val cordaSharesAmount: Amount<TokenType>,
    val cordaSeller: Party,
    val cordaBuyer: Party,
    val solanaSellerTokenAccount: Pubkey,
    val solanaBuyerTokenAccount: Pubkey,
    val solanaMintAuthority: Pubkey,
    val solanaTokenMint: Pubkey,
    val solanaPaymentAmount: Long,
    val solanaPaymentDecimals: Byte,
) : ContractState {
    override val participants: List<AbstractParty> = listOf(cordaSeller, cordaBuyer)
}