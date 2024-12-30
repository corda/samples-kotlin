package net.corda.samples.bikemarket.contracts

import com.r3.corda.lib.tokens.contracts.EvolvableTokenContract
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.Requirements
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import net.corda.samples.bikemarket.states.FrameTokenState


class BikeContract :  Contract {

    companion object {
        const val CONTRACT_ID = "net.corda.samples.bikemarket.contracts.BikeContract"
    }
    interface Commands: CommandData {
        class Create : Commands
        class Burn : Commands
        class Transfer : Commands
    }

    override fun verify(tx: LedgerTransaction) {
    }
}
