package net.corda.samples.negotiation.contracts

import net.corda.core.contracts.*
import net.corda.core.crypto.keyrotation.crossprovider.PartyIdentityResolver
import net.corda.core.transactions.LedgerTransaction
import net.corda.samples.negotiation.states.ProposalState
import net.corda.samples.negotiation.states.TradeState

class ProposalAndTradeContract : Contract {
    companion object {
        const val ID = "net.corda.samples.negotiation.contracts.ProposalAndTradeContract"
    }

    // A transaction is considered valid if the verify() function of the contract of each of the transaction's input
    // and output states does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        val cmd = tx.commands.requireSingleCommand<Commands>()

        when (cmd.value) {
            is Commands.Propose -> requireThat {
                // No change is required since the flow creating the transaction will always use the most up-to-date identities
                // when building the transaction.
                "There are no inputs" using (tx.inputStates.isEmpty())
                "There is exactly one output" using (tx.outputStates.size == 1)
                "The single output is of type ProposalState" using (tx.outputsOfType<ProposalState>().size == 1)
                "There is exactly one command" using (tx.commands.size == 1)
                "There is no timestamp" using (tx.timeWindow == null)

                val output = tx.outputsOfType<ProposalState>().single()
                "The buyer and seller are the proposer and the proposee" using (setOf(output.buyer, output.seller) == setOf(output.proposer, output.proposee))

                "The proposer is a required signer" using (cmd.signers.contains(output.proposer.owningKey))
                "The proposee is a required signer" using (cmd.signers.contains(output.proposee.owningKey))
            }

            is Commands.Accept -> requireThat {
                "There is exactly one input" using (tx.inputStates.size == 1)
                "The single input is of type ProposalState" using (tx.inputsOfType<ProposalState>().size == 1)
                "There is exactly one output" using (tx.outputStates.size == 1)
                "The single output is of type TradeState" using (tx.outputsOfType<TradeState>().size == 1)
                "There is exactly one command" using (tx.commands.size == 1)
                "There is no timestamp" using (tx.timeWindow == null)

                val input = tx.inputsOfType<ProposalState>().single()
                val output = tx.outputsOfType<TradeState>().single()

                // Create a resolver using the proof chain map from the command.
                //
                // This allows the resolver to resolve parties across key rotations,
                // ensuring that the same logical parties are identified in the transaction
                // even when their public keys have changed.
                val resolver = PartyIdentityResolver(cmd.keyRotationProofChainMap)

                "The amount is unmodified in the output" using (output.amount == input.amount)

                // After a key rotation, parties in the input and output states should be compared using the resolver,
                // rather than relying on `equals`, which may fail if a party’s public key has changed.
                //
                // This is only strictly necessary when the flow has been updated to replace the old party with the new one in the output state.
                // The proof chain map will be used by the resolver to determine that the old and new parties are in fact the same, allowing the contract to verify successfully.
                "The buyer is unmodified in the output" using (resolver.isSameParty(input.buyer, output.buyer))
                "The seller is unmodified in the output" using ( resolver.isSameParty(input.seller, output.seller))

                // Similarly, the required signers should be checked using the resolver to account for any key rotations.
                // The proof chain map will be used by the resolver to determine that the old and new parties are in fact the same, allowing the contract to verify successfully.
                "The proposer is a required signer" using (resolver.isRequiredSigner(cmd.signers, input.proposer))
                "The proposee is a required signer" using (resolver.isRequiredSigner(cmd.signers, input.proposee))
            }

            is Commands.Modify -> requireThat {
                "There is exactly one input" using (tx.inputStates.size == 1)
                "The single input is of type ProposalState" using (tx.inputsOfType<ProposalState>().size == 1)
                "There is exactly one output" using (tx.outputStates.size == 1)
                "The single output is of type ProposalState" using (tx.outputsOfType<ProposalState>().size == 1)
                "There is exactly one command" using (tx.commands.size == 1)
                "There is no timestamp" using (tx.timeWindow == null)

                val output = tx.outputsOfType<ProposalState>().single()
                val input = tx.inputsOfType<ProposalState>().single()


                // Create a resolver using the proof chain map from the command.
                //
                // This allows the resolver to resolve parties across key rotations,
                // ensuring that the same logical parties are identified in the transaction
                // even when their public keys have changed.
                val resolver: PartyIdentityResolver = PartyIdentityResolver(cmd.keyRotationProofChainMap)

                "The amount is modified in the output" using (output.amount != input.amount)

                // After a key rotation, parties in the input and output states should be compared using the resolver,
                // rather than relying on `equals`, which may fail if a party’s public key has changed.
                //
                // This is only strictly necessary when the flow has been updated to replace the old party with the new one in the output state.
                // The proof chain map will be used by the resolver to determine that the old and new parties are in fact the same, allowing the contract to verify successfully.
                "The buyer is unmodified in the output" using (resolver.isSameParty(input.buyer, output.buyer))
                "The seller is unmodified in the output" using (resolver.isSameParty(input.seller, output.seller))

                // Similarly, the required signers should be checked using the resolver to account for any key rotations.
                // The proof chain map will be used by the resolver to determine that the old and new parties are in fact the same, allowing the contract to verify successfully.
                "The proposer is a required signer" using (resolver.isRequiredSigner(cmd.signers, input.proposer))
                "The proposee is a required signer" using (resolver.isRequiredSigner(cmd.signers, input.proposee))
            }
        }
    }

    // Used to indicate the transaction's intent.
    sealed class Commands : TypeOnlyCommandData() {
        class Propose : Commands()
        class Accept : Commands()
        class Modify : Commands()
    }
}


