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
import net.corda.core.identity.CordaX500Name
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
        private val notaryName: String
    ) : FlowLogic<SignedTransaction>() {
        constructor(iouValue: Int, otherParty: Party, sanctionsBody: Party) : this(
            iouValue,
            otherParty,
            sanctionsBody,
            DEFAULT_NOTARY_NAME
        )

        companion object {
            private const val DEFAULT_NOTARY_NAME = "O=Notary,L=London,C=GB"
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
            val notary = resolveNotary(notaryName)

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            val sanctionsListToUse = getSanctionsList(sanctionsBody)
            val iouState = SanctionableIOUState(iouValue, serviceHub.myInfo.legalIdentities.first(), otherParty)
            val txCommand = Command(
                SanctionableIOUContract.Commands.Create(sanctionsBody),
                iouState.participants.map { it.owningKey }
            )

            val txBuilder = TransactionBuilder(notary)
                .addOutputState(iouState, IOU_CONTRACT_ID)
                .addCommand(txCommand)

            sanctionsListToUse?.let { sanctionsList ->
                if (sanctionsList.state.notary != notary) {
                    throw FlowException(
                        "Reference state notary ${sanctionsList.state.notary.name} does not match tx notary ${notary.name}"
                    )
                }
                txBuilder.addReferenceState(sanctionsList.referenced())
            }

            // Stage 2.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            // Stage 3.
            progressTracker.currentStep = SIGNING_TRANSACTION
            // Sign the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Stage 4.
            progressTracker.currentStep = GATHERING_SIGS
            // Send the states to the counterparty, and receive it back with their signature.
            val otherPartySession = initiateFlow(otherParty)
            val fullySignedTx = subFlow(
                CollectSignaturesFlow(
                    partSignedTx,
                    setOf(otherPartySession),
                    GATHERING_SIGS.childProgressTracker()
                )
            )

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
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

        @Suspendable
        fun getLatestSanctionsList(sanctionsBody: Party): StateAndRef<SanctionedEntities>? {
            return subFlow(GetSanctionsListFlow.Initiator(sanctionsBody)).firstOrNull()
        }

        private fun resolveNotary(x500Name: String) =
            serviceHub.networkMapCache.getNotary(CordaX500Name.parse(x500Name))
                ?: throw FlowException("Notary not found: $x500Name")
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

            val recordedTx = subFlow(
                ReceiveFinalityFlow(
                    otherPartySession,
                    expectedTxId = txId,
                    statesToRecord = StatesToRecord.ALL_VISIBLE
                )
            )
            return recordedTx
        }
    }
}