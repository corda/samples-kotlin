package net.corda.samples.fungiblehousetoken.contracts

import com.r3.corda.lib.tokens.contracts.EvolvableTokenContract
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

// ************
// * Contract *
// ************
class HouseTokenStateContract : EvolvableTokenContract(), Contract {
    override fun additionalCreateChecks(tx: LedgerTransaction) {
    }

    override fun additionalUpdateChecks(tx: LedgerTransaction) {
    }

}