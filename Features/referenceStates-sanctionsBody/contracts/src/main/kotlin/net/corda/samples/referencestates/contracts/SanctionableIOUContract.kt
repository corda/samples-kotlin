package net.corda.samples.referencestates.contracts

import net.corda.samples.referencestates.states.SanctionableIOUState
import net.corda.samples.referencestates.states.SanctionedEntities
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireThat
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction

class SanctionableIOUContract : Contract {
    companion object {
        @JvmStatic
        val IOU_CONTRACT_ID = "net.corda.samples.referencestates.contracts.SanctionableIOUContract"
    }

    override fun verify(tx: LedgerTransaction) {
        // Handle Create command (original logic)
        val createCommands = tx.commandsOfType<Commands.Create>()
        val settleCommands = tx.commandsOfType<Commands.Settle>()
        val transferCommands = tx.commandsOfType<Commands.Transfer>()

        when {
            createCommands.isNotEmpty() -> {
                val command = createCommands.single()
                verifyCreate(tx, command.value, command.signers)
            }
            settleCommands.isNotEmpty() -> {
                val command = settleCommands.single()
                verifySettle(tx, command.value, command.signers)
            }
            transferCommands.isNotEmpty() -> {
                val command = transferCommands.single()
                verifyTransfer(tx, command.value, command.signers)
            }
            else -> throw IllegalArgumentException("Unrecognized command")
        }
    }

    private fun verifyCreate(tx: LedgerTransaction, createCommand: Commands.Create, signers: List<java.security.PublicKey>) {
        require(tx.referenceInputRefsOfType(SanctionedEntities::class.java).singleOrNull() != null) {
            "All transactions require a list of sanctioned entities"
        }

        val sanctionedEntities = tx.referenceInputRefsOfType(SanctionedEntities::class.java).single().state.data
        require(sanctionedEntities.issuer.name == createCommand.sanctionsBody.name) {
            "${sanctionedEntities.issuer.name.organisation} is an invalid issuer of sanctions lists for this contract"
        }

        requireThat {
            "No inputs should be consumed when issuing an IOU." using (tx.inputs.isEmpty())
            "Only one output state should be created." using (tx.outputs.size == 1)
            val out = tx.outputsOfType<SanctionableIOUState>().single()
            "The lender and the borrower cannot be the same entity." using (out.lender != out.borrower)
            "All of the participants must be signers." using (signers.containsAll(out.participants.map { it.owningKey }))
            "The IOU's value must be non-negative." using (out.value > 0)
            "The lender ${out.lender.name} is a sanctioned entity" using !sanctionedEntities.badPeople.contains(out.lender)
            "The borrower ${out.borrower.name} is a sanctioned entity" using !sanctionedEntities.badPeople.contains(out.borrower)
        }
    }

    private fun verifySettle(tx: LedgerTransaction, settleCommand: Commands.Settle, signers: List<java.security.PublicKey>) {
        requireThat {
            "There must be one input IOU when settling." using (tx.inputsOfType<SanctionableIOUState>().size == 1)
            "There must be no output IOUs when settling." using (tx.outputsOfType<SanctionableIOUState>().isEmpty())
            val inputIOU = tx.inputsOfType<SanctionableIOUState>().single()
            "The borrower must sign the settlement transaction." using (signers.contains(inputIOU.borrower.owningKey))
            "The lender must sign the settlement transaction." using (signers.contains(inputIOU.lender.owningKey))
        }
    }

    private fun verifyTransfer(tx: LedgerTransaction, transferCommand: Commands.Transfer, signers: List<java.security.PublicKey>) {
        require(tx.referenceInputRefsOfType(SanctionedEntities::class.java).singleOrNull() != null) {
            "All transactions require a list of sanctioned entities"
        }

        val sanctionedEntities = tx.referenceInputRefsOfType(SanctionedEntities::class.java).single().state.data
        require(sanctionedEntities.issuer.name == transferCommand.sanctionsBody.name) {
            "${sanctionedEntities.issuer.name.organisation} is an invalid issuer of sanctions lists for this contract"
        }

        requireThat {
            "There must be one input IOU when transferring." using (tx.inputsOfType<SanctionableIOUState>().size == 1)
            "There must be one output IOU when transferring." using (tx.outputsOfType<SanctionableIOUState>().size == 1)
            val inputIOU = tx.inputsOfType<SanctionableIOUState>().single()
            val outputIOU = tx.outputsOfType<SanctionableIOUState>().single()
            "The IOU value must remain the same during transfer." using (inputIOU.value == outputIOU.value)
            "The linear ID must remain the same during transfer." using (inputIOU.linearId == outputIOU.linearId)
            "The borrower must remain the same during transfer." using (inputIOU.borrower == outputIOU.borrower)
            "The lender must change during transfer." using (inputIOU.lender != outputIOU.lender)
            "The current lender must sign the transfer transaction." using (signers.contains(inputIOU.lender.owningKey))
            "The new lender must sign the transfer transaction." using (signers.contains(outputIOU.lender.owningKey))
            "The new lender ${outputIOU.lender.name} is a sanctioned entity" using !sanctionedEntities.badPeople.contains(outputIOU.lender)
        }
    }

    interface Commands : CommandData {
        class Create(val sanctionsBody: Party) : Commands
        class Settle(val sanctionsBody: Party) : Commands
        class Transfer(val sanctionsBody: Party) : Commands
    }
}