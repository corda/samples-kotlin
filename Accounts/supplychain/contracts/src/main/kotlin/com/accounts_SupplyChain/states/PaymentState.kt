package com.accounts_SupplyChain.states

import com.accounts_SupplyChain.contracts.PaymentStateContract
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty

import net.corda.core.identity.AnonymousParty


@BelongsToContract(PaymentStateContract::class)
class PaymentState(

        val amount: Int,
        val recipient: AnonymousParty,
        val sender: AnonymousParty) : ContractState{
    override val participants: List<AbstractParty> get() = listOfNotNull(recipient,sender).map { it }
}