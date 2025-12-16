package net.corda.samples.solanadvp.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import net.corda.samples.solanadvp.states.PaymentState

class PaymentContract : Contract {
    companion object {
        const val ID = "net.corda.samples.solanadvp.contracts.PaymentContract"
    }

    interface Commands : CommandData {
        class Agree : Commands
    }

    override fun verify(tx: LedgerTransaction) {
        val cmd: CommandWithParties<Commands> =
            tx.commands.requireSingleCommand<Commands>()

        when (cmd.value) {
            is Commands.Agree -> requireThat {
                "No inputs should be consumed." using (tx.inputsOfType<PaymentState>().isEmpty())
                val outputs = tx.outputsOfType<PaymentState>()
                "One output should be created." using (outputs.size == 1)
                val out = tx.outputsOfType<PaymentState>().single()
                // This makes buyer signature required in addition to seller
                val required = setOf(out.seller.owningKey, out.buyer.owningKey)
                "Seller and buyer must both sign." using (cmd.signers.containsAll(required))
            }
        }
    }
}
