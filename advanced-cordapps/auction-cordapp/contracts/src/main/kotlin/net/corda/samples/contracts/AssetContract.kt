package net.corda.samples.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

// ************
// * Contract *
// ************
class AssetContract : Contract {
    companion object {
        // Used to identify our contract when building a transaction.
        const val ID = "net.corda.samples.contracts.AssetContract"
    }

    // A transaction is valid if the verify() function of the contract of all the transaction's input and output states
    // does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        // Verification logic goes here.
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {
        class TransferAsset : Commands
        class CreateAsset: Commands
    }
}