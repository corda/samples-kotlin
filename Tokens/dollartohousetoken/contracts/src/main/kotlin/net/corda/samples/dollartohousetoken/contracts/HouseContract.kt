package net.corda.samples.dollartohousetoken.contracts

import com.r3.corda.lib.tokens.contracts.EvolvableTokenContract
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

// ************
// * Contract *
// ************
class HouseContract : EvolvableTokenContract(),Contract {
    override fun additionalCreateChecks(tx: LedgerTransaction) {
    }

    override fun additionalUpdateChecks(tx: LedgerTransaction) {
    }

}