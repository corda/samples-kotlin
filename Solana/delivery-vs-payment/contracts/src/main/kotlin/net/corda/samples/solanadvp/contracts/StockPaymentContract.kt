package net.corda.samples.solanadvp.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import net.corda.samples.solanadvp.states.StockPaymentState
import net.corda.solana.sdk.SplToken
import net.corda.solana.sdk.instruction.SolanaInstruction
import kotlin.collections.singleOrNull

class StockPaymentContract : Contract {
    companion object {
        const val ID = "net.corda.samples.solanadvp.contracts.StockPaymentContract"
    }

    interface Commands : CommandData {
        class Agree : Commands
    }

    override fun verify(tx: LedgerTransaction) {
        val cmd: CommandWithParties<Commands> =
            tx.commands.requireSingleCommand<Commands>()

        when (cmd.value) {
            is Commands.Agree -> requireThat {
                "No inputs should be consumed." using (tx.inputsOfType<StockPaymentState>().isEmpty())
                val outputs = tx.outputsOfType<StockPaymentState>()
                "One output should be created." using (outputs.size == 1)
                val out = tx.outputsOfType<StockPaymentState>().single()
                // This makes buyer signature required in addition to seller
                val required = setOf(out.seller.owningKey, out.buyer.owningKey)
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
                    out.quantity,
                    out.decimals
                )

                require(solanaInstruction == expectedInstruction) {
                    "The Solana instruction in the transaction not the expected burn instruction:\n" +
                            "transaction: $solanaInstruction\n" +
                            "expected:    $expectedInstruction"
                }
            }
        }
    }
}
