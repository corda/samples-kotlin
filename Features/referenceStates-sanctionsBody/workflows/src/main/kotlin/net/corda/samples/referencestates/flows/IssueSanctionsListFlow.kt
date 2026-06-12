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

object IssueSanctionsListFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator : FlowLogic<StateAndRef<SanctionedEntities>> {

        private val notary: Party?

        // Primary constructor for shell usage
        constructor(notary: Party) {
            this.notary = notary
        }

        // Secondary constructor for backward compatibility
        constructor() {
            this.notary = null
        }

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

        @Suspendable
        override fun call(): StateAndRef<SanctionedEntities> {
            // Determine notary to use
            val selectedNotary = notary ?: serviceHub.networkMapCache.notaryIdentities.firstOrNull()
            ?: throw FlowException("No notary available and none specified.")

            logger.info("Using notary: ${selectedNotary.name}")

            progressTracker.currentStep = GENERATING_TRANSACTION
            val state = SanctionedEntities(emptyList(), serviceHub.myInfo.legalIdentities.first())
            val txCommand = Command(
                SanctionedEntitiesContract.Commands.Create,
                serviceHub.myInfo.legalIdentities.first().owningKey
            )
            val txBuilder = TransactionBuilder(selectedNotary)
                .addOutputState(state, SanctionedEntitiesContract.SANCTIONS_CONTRACT_ID)
                .addCommand(txCommand)

            txBuilder.verify(serviceHub)

            progressTracker.currentStep = SIGNING_TRANSACTION
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            progressTracker.currentStep = FINALISING_TRANSACTION
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