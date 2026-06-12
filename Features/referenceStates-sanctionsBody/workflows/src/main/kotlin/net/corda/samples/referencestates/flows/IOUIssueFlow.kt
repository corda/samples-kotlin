package net.corda.samples.referencestates.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.samples.referencestates.contracts.SanctionableIOUContract
import net.corda.samples.referencestates.contracts.SanctionableIOUContract.Companion.IOU_CONTRACT_ID
import net.corda.samples.referencestates.states.SanctionableIOUState
import net.corda.samples.referencestates.states.SanctionedEntities
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step

object IOUIssueFlow {

    @InitiatingFlow
    @StartableByRPC
    class Initiator(
        val iouValue: Int,
        val otherParty: Party,
        val sanctionsBody: Party,
        private val notary: Party? = null
    ) : FlowLogic<SignedTransaction>() {

        companion object {
            object GENERATING_TRANSACTION : Step("Generating transaction based on new IOU.")
            object VERIFYING_TRANSACTION : Step("Verifying contracts constraints.")
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
            // Determine notary to use
            val selectedNotary = notary ?: serviceHub.networkMapCache.notaryIdentities.firstOrNull()
            ?: throw FlowException("No notary available and none specified.")

            logger.info("Using notary: ${selectedNotary.name}")

            progressTracker.currentStep = GENERATING_TRANSACTION
            val sanctionsListToUse = getSanctionsList(sanctionsBody)
            val iouState = SanctionableIOUState(iouValue, serviceHub.myInfo.legalIdentities.first(), otherParty)
            val txCommand = Command(
                SanctionableIOUContract.Commands.Create(sanctionsBody),
                iouState.participants.map { it.owningKey }
            )

            val txBuilder = TransactionBuilder(selectedNotary)
                .addOutputState(iouState, IOU_CONTRACT_ID)
                .addCommand(txCommand)

            sanctionsListToUse?.let { sanctionsList ->
                txBuilder.addReferenceState(sanctionsList.referenced())
            }

            progressTracker.currentStep = VERIFYING_TRANSACTION
            txBuilder.verify(serviceHub)

            progressTracker.currentStep = SIGNING_TRANSACTION
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            progressTracker.currentStep = GATHERING_SIGS
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
        fun getSanctionsList(sanctionsBody: Party): StateAndRef<SanctionedEntities>? {
            return serviceHub.vaultService.queryBy(SanctionedEntities::class.java)
                .states.filter { it.state.data.issuer == sanctionsBody }.singleOrNull()
        }
    }

    @InitiatedBy(Initiator::class)
    class Acceptor(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an IOU transaction." using (output is SanctionableIOUState)
                    val iou = output as SanctionableIOUState
                    "I won't accept IOUs with a value over 100." using (iou.value <= 100)
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
}