package net.corda.samples.solana.dvp.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import net.corda.samples.solana.dvp.states.SharesPaymentState
import net.corda.solana.sdk.SplToken
import net.corda.solana.sdk.instruction.SolanaInstruction
import kotlin.collections.singleOrNull

class SharesPaymentContract : Contract {
    companion object {
        const val ID = "net.corda.samples.solana.dvp.contracts.SharesPaymentContract"
    }

    interface Commands : CommandData {
        class Agree : Commands
    }

    override fun verify(tx: LedgerTransaction) {
        val cmd: CommandWithParties<Commands> =
            tx.commands.requireSingleCommand<Commands>()

        when (cmd.value) {
            is Commands.Agree -> requireThat {
                "No inputs should be consumed." using (tx.inputsOfType<SharesPaymentState>().isEmpty())
                val outputs = tx.outputsOfType<SharesPaymentState>()
                "One output should be created." using (outputs.size == 1)
                val out = tx.outputsOfType<SharesPaymentState>().single()
                // This makes buyer signature required in addition to seller
                val required = setOf(out.cordaSeller.owningKey, out.cordaBuyer.owningKey)
                "Seller and buyer must both sign." using (cmd.signers.containsAll(required))

                val solanaInstruction =
                    requireNotNull(tx.notaryInstructionsOfType<SolanaInstruction>().singleOrNull()) {
                        "Exactly one Solana instruction required"
                    }

                val expectedInstruction = SplToken.transfer(
                    out.solanaBuyerTokenAccount,
                    out.solanaTokenMint,
                    out.solanaSellerTokenAccount,
                    out.solanaMintAuthority,
                    out.solanaPaymentAmount,
                    out.solanaPaymentDecimals
                )

                require(solanaInstruction == expectedInstruction) {
                    "The Solana instruction in the transaction not the expected transfer instruction:\n" +
                            "transaction: $solanaInstruction\n" +
                            "expected:    $expectedInstruction"
                }
            }
        }
    }
}
