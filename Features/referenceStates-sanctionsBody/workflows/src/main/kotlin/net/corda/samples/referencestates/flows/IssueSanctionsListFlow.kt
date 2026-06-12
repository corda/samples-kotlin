// File: workflows/src/main/kotlin/net/corda/samples/referencestates/flows/IssueSanctionsListFlow.kt

package net.corda.samples.referencestates.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.samples.referencestates.contracts.SanctionedEntitiesContract
import net.corda.samples.referencestates.states.SanctionedEntities
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step

/**
 * This flow allows the sanctions authority to issue an initial empty sanctions list.
 * The sanctions list can later be updated using UpdateSanctionsListFlow.
 */
object IssueSanctionsListFlow {

    @InitiatingFlow
    @StartableByRPC
    class Initiator @JvmOverloads constructor(
        private val notary: Party? = null
    ) : FlowLogic<StateAndRef<SanctionedEntities>>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : Step("Generating Transaction")
            object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
            object FINALISING_TRANSACTION : Step("Recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                GENERATING_TRANSACTION,
                SIGNING_TRANSACTION,
                FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        override fun call(): StateAndRef<SanctionedEntities> {
            // Determine notary to use
            val selectedNotary = notary ?: serviceHub.networkMapCache.notaryIdentities.firstOrNull()
            ?: throw FlowException("No notary available and none specified.")

            logger.info("Using notary: ${selectedNotary.name}")

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            val state = SanctionedEntities(emptyList(), serviceHub.myInfo.legalIdentities.first())
            val txCommand = Command(
                SanctionedEntitiesContract.Commands.Create,
                serviceHub.myInfo.legalIdentities.first().owningKey
            )
            val txBuilder = TransactionBuilder(selectedNotary)
                .addOutputState(state, SanctionedEntitiesContract.SANCTIONS_CONTRACT_ID)
                .addCommand(txCommand)

            txBuilder.verify(serviceHub)

            // Stage 3.
            progressTracker.currentStep = SIGNING_TRANSACTION
            // Sign the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            return subFlow(
                FinalityFlow(
                    partSignedTx,
                    sessions = emptyList(),
                    progressTracker = FINALISING_TRANSACTION.childProgressTracker()
                )
            ).tx.outRefsOfType(SanctionedEntities::class.java).single()
        }
    }
}