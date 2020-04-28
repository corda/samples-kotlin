package com.accounts_SupplyChain.contracts

import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction

class ShippingRequestStateContract : Contract {

    companion object {
        const val ID = "com.accounts_SupplyChain.contracts.ShippingRequestStateContract"
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