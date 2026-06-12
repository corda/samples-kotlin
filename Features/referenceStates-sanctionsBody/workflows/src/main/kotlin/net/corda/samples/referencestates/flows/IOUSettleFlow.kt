// File: workflows/src/main/kotlin/net/corda/samples/referencestates/flows/IOUSettleFlow.kt

package net.corda.samples.referencestates.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import net.corda.samples.referencestates.contracts.SanctionableIOUContract
import net.corda.samples.referencestates.states.SanctionableIOUState
import net.corda.samples.referencestates.states.SanctionedEntities

@InitiatingFlow
@StartableByRPC
class IOUSettleFlow(
    private val linearId: UniqueIdentifier,
    private val sanctionsBody: Party
) : FlowLogic<SignedTransaction>() {

    companion object {
        object GENERATING_TRANSACTION : Step("Generating settlement transaction.")
        object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
        object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
        object GATHERING_SIGS : Step("Gathering the counterparty's signature.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }
        object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
            GENERATING_TRANSACTION,
            VERIFYING_TRANSACTION,
            SIGNING_TRANSACTION,
            GATHERING_SIGS,
            FINALISING_TRANSACTION
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = GENERATING_TRANSACTION

        // Find the IOU to settle
        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
        val iouStateAndRef = serviceHub.vaultService.queryBy<SanctionableIOUState>(queryCriteria).states.singleOrNull()
            ?: throw FlowException("IOU with linear ID $linearId not found.")

        val iouState = iouStateAndRef.state.data
        val notary = iouStateAndRef.state.notary
        val ourIdentity = serviceHub.myInfo.legalIdentities.first()

        // Verify we are either the lender or borrower
        require(ourIdentity == iouState.lender || ourIdentity == iouState.borrower) {
            "Only the lender or borrower can settle the IOU. Current lender: ${iouState.lender.name}, borrower: ${iouState.borrower.name}, you are: ${ourIdentity.name}"
        }

        logger.info("Settling IOU: lender=${iouState.lender.name}, borrower=${iouState.borrower.name}, value=${iouState.value}")

        // Get the sanctions list
        val sanctionsListToUse = getSanctionsList(sanctionsBody)

        // Build the transaction
        val txCommand = Command(
            SanctionableIOUContract.Commands.Settle(sanctionsBody),
            listOf(iouState.lender.owningKey, iouState.borrower.owningKey)
        )

        val txBuilder = TransactionBuilder(notary)
            .addInputState(iouStateAndRef)
            .addCommand(txCommand)

        // Add reference state if available
        sanctionsListToUse?.let { sanctionsList ->
            txBuilder.addReferenceState(sanctionsList.referenced())
        }

        progressTracker.currentStep = VERIFYING_TRANSACTION
        txBuilder.verify(serviceHub)

        progressTracker.currentStep = SIGNING_TRANSACTION
        val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

        progressTracker.currentStep = GATHERING_SIGS
        // Determine who we need to collect signatures from
        val otherParty = if (ourIdentity == iouState.lender) iouState.borrower else iouState.lender
        val otherPartySession = initiateFlow(otherParty)

        val fullySignedTx = subFlow(
            CollectSignaturesFlow(
                partSignedTx,
                setOf(otherPartySession),
                GATHERING_SIGS.childProgressTracker()
            )
        )

        progressTracker.currentStep = FINALISING_TRANSACTION
        return subFlow(
            FinalityFlow(
                fullySignedTx,
                setOf(otherPartySession),
                FINALISING_TRANSACTION.childProgressTracker()
            )
        )
    }

    @Suspendable
    private fun getSanctionsList(sanctionsBody: Party): StateAndRef<SanctionedEntities>? {
        return try {
            serviceHub.vaultService.queryBy(SanctionedEntities::class.java)
                .states.filter { it.state.data.issuer == sanctionsBody }.singleOrNull()
        } catch (e: Exception) {
            logger.warn("Could not retrieve sanctions list: ${e.message}")
            null
        }
    }
}

@InitiatedBy(IOUSettleFlow::class)
class IOUSettleFlowAcceptor(private val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val transaction = stx.tx

                "Transaction must have exactly one input." using (transaction.inputs.size == 1)
                "Transaction must have no outputs for settlement." using (transaction.outputs.isEmpty())

                logger.info("Accepting settlement transaction")
            }
        }

        val txId = subFlow(signTransactionFlow).id
        return subFlow(
            ReceiveFinalityFlow(
                otherPartySession,
                expectedTxId = txId,
                statesToRecord = StatesToRecord.ALL_VISIBLE
            )
        )
    }
}