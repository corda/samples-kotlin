package com.accounts_SupplyChain.states

import com.accounts_SupplyChain.contracts.InvoiceStateContract
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import java.util.*


@BelongsToContract(InvoiceStateContract::class)
class InvoiceState(

        val amount: Int,
        val sender: AnonymousParty,
        val recipient: AnonymousParty,
        val invoiceID: UUID) : ContractState{
    override val participants: List<AbstractParty> get() = listOfNotNull(recipient,sender).map { it }
}