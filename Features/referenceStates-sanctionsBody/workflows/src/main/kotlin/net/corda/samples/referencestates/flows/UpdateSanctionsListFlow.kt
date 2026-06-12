// File: workflows/src/main/kotlin/net/corda/samples/referencestates/flows/UpdateSanctionsListFlow.kt

package net.corda.samples.referencestates.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import net.corda.samples.referencestates.contracts.SanctionedEntitiesContract
import net.corda.samples.referencestates.states.SanctionedEntities

/**
 * Flow to update sanctions list by adding a party
 */
@InitiatingFlow
@StartableByRPC
class UpdateSanctionsListFlow(
    val partyToSanction: Party,
    private val notary: Party? = null
) : FlowLogic<StateAndRef<SanctionedEntities>>() {

    companion object {
        object GENERATING_TRANSACTION : Step("Generating Transaction")
        object ADDING_PARTY_TO_LIST : Step("Adding party to sanctions list")
        object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
        object FINALISING_TRANSACTION : Step("Recording transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
            GENERATING_TRANSACTION,
            ADDING_PARTY_TO_LIST,
            SIGNING_TRANSACTION,
            FINALISING_TRANSACTION
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): StateAndRef<SanctionedEntities> {
        val oldList = serviceHub.vaultService.queryBy(SanctionedEntities::class.java).states.single()

        // Use provided notary or notary from existing state
        val selectedNotary = notary ?: oldList.state.notary

        logger.info("Using notary: ${selectedNotary.name}")
        logger.info("Adding party to sanctions: ${partyToSanction.name}")

        val newList = oldList.state.data.copy(badPeople = oldList.state.data.badPeople + listOf(partyToSanction))

        progressTracker.currentStep = GENERATING_TRANSACTION
        val txCommand = Command(
            SanctionedEntitiesContract.Commands.Update,
            serviceHub.myInfo.legalIdentities.first().owningKey
        )
        val txBuilder = TransactionBuilder(selectedNotary)
            .addOutputState(newList, SanctionedEntitiesContract.SANCTIONS_CONTRACT_ID)
            .addInputState(oldList)
            .addCommand(txCommand)

        progressTracker.currentStep = ADDING_PARTY_TO_LIST
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