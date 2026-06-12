// File: workflows/src/main/kotlin/net/corda/samples/referencestates/flows/IOUTransferFlow.kt

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
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import net.corda.core.utilities.unwrap
import net.corda.samples.referencestates.contracts.SanctionableIOUContract
import net.corda.samples.referencestates.states.SanctionableIOUState
import net.corda.samples.referencestates.states.SanctionedEntities

@CordaSerializable
enum class ParticipantRole {
    NEW_LENDER,    // Must sign the transaction
    BORROWER       // Just receives notification
}

@CordaSerializable
data class TransferNotification(
    val role: ParticipantRole,
    val message: String
)

@InitiatingFlow
@StartableByRPC
class IOUTransferFlow(
    private val linearId: UniqueIdentifier,
    private val newLender: Party,
    private val sanctionsBody: Party
) : FlowLogic<SignedTransaction>() {

    companion object {
        object GENERATING_TRANSACTION : Step("Generating transfer transaction.")
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

        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
        val iouStateAndRef = serviceHub.vaultService.queryBy<SanctionableIOUState>(queryCriteria).states.singleOrNull()
            ?: throw FlowException("IOU with linear ID $linearId not found.")

        val inputIOU = iouStateAndRef.state.data
        val notary = iouStateAndRef.state.notary
        val ourIdentity = serviceHub.myInfo.legalIdentities.first()

        require(ourIdentity == inputIOU.lender) {
            "Only the current lender can transfer the IOU."
        }

        logger.info("Transferring IOU from ${inputIOU.lender.name} to ${newLender.name}, borrower: ${inputIOU.borrower.name}")

        val outputIOU = inputIOU.copy(lender = newLender)
        val sanctionsListToUse = getSanctionsList(sanctionsBody)

        val txCommand = Command(
            SanctionableIOUContract.Commands.Transfer(sanctionsBody),
            listOf(inputIOU.lender.owningKey, newLender.owningKey)
        )

        val txBuilder = TransactionBuilder(notary)
            .addInputState(iouStateAndRef)
            .addOutputState(outputIOU, SanctionableIOUContract.IOU_CONTRACT_ID)
            .addCommand(txCommand)

        sanctionsListToUse?.let { sanctionsList ->
            txBuilder.addReferenceState(sanctionsList.referenced())
        }

        progressTracker.currentStep = VERIFYING_TRANSACTION
        txBuilder.verify(serviceHub)

        progressTracker.currentStep = SIGNING_TRANSACTION
        val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

        progressTracker.currentStep = GATHERING_SIGS

        // STEP 1: Get signature from new lender
        val newLenderSession = initiateFlow(newLender)

        // Send role notification to new lender
        newLenderSession.send(TransferNotification(
            ParticipantRole.NEW_LENDER,
            "Please sign this IOU transfer transaction"
        ))

        val fullySignedTx = subFlow(
            CollectSignaturesFlow(
                partSignedTx,
                setOf(newLenderSession),
                GATHERING_SIGS.childProgressTracker()
            )
        )

        progressTracker.currentStep = FINALISING_TRANSACTION

        // STEP 2: Notify borrower (separate session to avoid confusion)
        val borrowerSession = initiateFlow(inputIOU.borrower)

        // Send role notification to borrower
        borrowerSession.send(TransferNotification(
            ParticipantRole.BORROWER,
            "IOU has been transferred to ${newLender.name}"
        ))

        // Include both in finality
        return subFlow(
            FinalityFlow(
                fullySignedTx,
                setOf(newLenderSession, borrowerSession),
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

@InitiatedBy(IOUTransferFlow::class)
class IOUTransferFlowAcceptor(private val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // STEP 1: Receive role notification first
        val notification = otherPartySession.receive<TransferNotification>().unwrap { it }

        logger.info("Received transfer notification: ${notification.message}, Role: ${notification.role}")

        return when (notification.role) {
            ParticipantRole.NEW_LENDER -> {
                // We need to sign the transaction
                logger.info("Acting as new lender - signing transaction")

                val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                    override fun checkTransaction(stx: SignedTransaction) = requireThat {
                        val transaction = stx.tx
                        "Transaction must have exactly one input." using (transaction.inputs.size == 1)
                        "Transaction must have exactly one output." using (transaction.outputs.size == 1)

                        val outputIOU = transaction.outputsOfType<SanctionableIOUState>().single()
                        val ourIdentity = serviceHub.myInfo.legalIdentities.first()

                        "We must be the new lender." using (ourIdentity == outputIOU.lender)
                        "IOU value must be positive." using (outputIOU.value > 0)

                        logger.info("New lender ${ourIdentity.name} validating transfer transaction")
                    }
                }

                val txId = subFlow(signTransactionFlow).id
                subFlow(
                    ReceiveFinalityFlow(
                        otherPartySession,
                        expectedTxId = txId,
                        statesToRecord = StatesToRecord.ALL_VISIBLE
                    )
                )
            }

            ParticipantRole.BORROWER -> {
                // We just receive the finalized transaction
                logger.info("Acting as borrower - receiving transfer notification")

                subFlow(
                    ReceiveFinalityFlow(
                        otherPartySession,
                        statesToRecord = StatesToRecord.ALL_VISIBLE
                    )
                )
            }
        }
    }
}