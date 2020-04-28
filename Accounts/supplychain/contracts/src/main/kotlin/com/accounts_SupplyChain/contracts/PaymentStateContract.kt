package com.accounts_SupplyChain.contracts

import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction

class PaymentStateContract : Contract{

    companion object{
        const val ID = "com.accounts_SupplyChain.contracts.PaymentStateContract"
    }


    override fun verify(tx: LedgerTransaction) {
        requireThat {
            /*
             *
             * For the simplicity of the sample, we unconditionally accept all of the transactions.
             *
             */
        }
    }

    interface Commands : CommandData {
        class Create : Commands
    }
}