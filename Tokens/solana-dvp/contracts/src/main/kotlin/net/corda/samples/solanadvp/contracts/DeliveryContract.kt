package net.corda.samples.solanadvp.contracts

import com.r3.corda.lib.tokens.contracts.EvolvableTokenContract
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction
import net.corda.samples.solanadvp.states.DeliveryState

class DeliveryContract : EvolvableTokenContract(),Contract {
    companion object {
        const val CONTRACT_ID = "net.corda.samples.solanadvp.contracts.DeliveryContract"
    }
    override fun additionalCreateChecks(tx: LedgerTransaction) {
        val outputState = tx.getOutput(0) as DeliveryState
        require(outputState.price.quantity > 0 ) { "The price must be non zero" }
    }

    override fun additionalUpdateChecks(tx: LedgerTransaction) {
        // Write contract validation logic to be performed while an update of token
    }
}