package net.corda.samples.solanadvp.states

import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.samples.solanadvp.contracts.StockPaymentContract

/**
 * Receipt of payment for amount of FungibleTokens.
 */
@BelongsToContract(StockPaymentContract::class)
data class StockPaymentState(
    val assetsAmount: Amount<TokenType>,
    val seller: Party,
    val buyer: Party
) : ContractState {
    override val participants: List<AbstractParty> = listOf(seller, buyer)
}