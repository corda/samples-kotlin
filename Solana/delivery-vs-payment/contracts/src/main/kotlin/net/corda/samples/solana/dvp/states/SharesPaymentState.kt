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
 * Stablecoin payment details (Solana account addresses and amount),
 * and a delivery info of an asset (amount of shares, participants) on Corda.
 */
@BelongsToContract(SharesPaymentContract::class)
data class SharesPaymentState(
    val cordaSharesAmount: Amount<TokenType>,
    val cordaSeller: Party,
    val cordaBuyer: Party,
    val solanaSellerTokenAccount: Pubkey,
    val solanaBuyerTokenAccount: Pubkey,  // This is ATA so it could be derived from other fields,
                                          // left to avoid dependencies on throw party libraries
    val solanaBuyerWalletAccount: Pubkey,
    val solanaStablecoin: Pubkey,
    val stablecoinAmount: Long,
    val stablecoinDecimals: Byte,
) : ContractState {
    override val participants: List<AbstractParty> = listOf(cordaSeller, cordaBuyer)
}
