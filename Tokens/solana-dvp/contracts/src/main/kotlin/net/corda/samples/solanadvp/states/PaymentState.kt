package net.corda.samples.solanadvp.states

import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.samples.solanadvp.contracts.PaymentContract

@BelongsToContract(PaymentContract::class)
data class PaymentState(
    val assetId: UniqueIdentifier,
    val seller: Party,
    val buyer: Party
) : ContractState {
    override val participants: List<AbstractParty> = listOf(seller, buyer)
}