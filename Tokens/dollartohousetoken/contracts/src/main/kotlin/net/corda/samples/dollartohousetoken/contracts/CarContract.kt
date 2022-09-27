package net.corda.samples.dollartohousetoken.contracts

import com.r3.corda.lib.tokens.contracts.EvolvableTokenContract
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction
import net.corda.samples.dollartohousetoken.states.CarState

// ************
// * Contract *
// ************
class CarContract : EvolvableTokenContract(), Contract {
    companion object {
    }

    override fun additionalCreateChecks(tx: LedgerTransaction) {
        // Write contract validation logic to be performed while creation of token
        val outputState = tx.getOutput(0) as CarState
        outputState.apply {
            require(outputState.carValue.quantity > 1000) { "Valuation cannot be zero" }
        }
    }

    override fun additionalUpdateChecks(tx: LedgerTransaction) {
        // Write contract validation logic to be performed while updation of token
    }

}