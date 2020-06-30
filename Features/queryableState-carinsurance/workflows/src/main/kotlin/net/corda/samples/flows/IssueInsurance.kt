package net.corda.samples.flows

import co.paralleluniverse.fibers.Suspendable
import com.google.common.collect.ImmutableList
import net.corda.samples.contracts.InsuranceContract
import net.corda.samples.states.InsuranceState
import net.corda.samples.states.VehicleDetail
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker


// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC
class IssueInsurance(val insuranceInfo: InsuranceInfo,
                     val insuree:Party) : FlowLogic<SignedTransaction>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call():SignedTransaction {
        // Initiator flow logic goes here.

        // Obtain a reference from a notary we wish to use.
        /**
         *  METHOD 1: Take first notary on network, WARNING: use for test, non-prod environments, and single-notary networks only!*
         *  METHOD 2: Explicit selection of notary by CordaX500Name - argument can by coded in flow or parsed from config (Preferred)
         *
         *  * - For production you always want to use Method 2 as it guarantees the expected notary is returned.
         */
        val notary = serviceHub.networkMapCache.notaryIdentities.single() // METHOD 1
        // val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB")) // METHOD 2

        val insurer = ourIdentity

        val vehicleInfo: VehicleInfo = insuranceInfo.vehicleInfo
        val vDetail = VehicleDetail(vehicleInfo.registrationNumber,
                vehicleInfo.chasisNumber, vehicleInfo.make,
                vehicleInfo.model, vehicleInfo.variant, vehicleInfo.color, vehicleInfo.fuelType)

        // Build the insurance output state.
        val output = InsuranceState(insuranceInfo.policyNumber, insuranceInfo.insuredValue, insuranceInfo.duration, insuranceInfo.premium, insurer, insuree, vDetail)

        // Build the transaction
        val txBuilder = TransactionBuilder(notary)
                .addOutputState(output)
                .addCommand(InsuranceContract.Commands.IssueInsurance(), listOf(insurer.owningKey))

        // Verify the transaction
        txBuilder.verify(serviceHub)

        // Sign the transaction
        val stx = serviceHub.signInitialTransaction(txBuilder)

        // Call finality Flow
        val ownerSession = initiateFlow(insuree)
        return subFlow(FinalityFlow(stx, listOf(ownerSession)))
    }
}

@InitiatedBy(IssueInsurance::class)
class IssueInsuranceResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call():SignedTransaction {
        subFlow(object : SignTransactionFlow(counterpartySession) {
            @Throws(FlowException::class)
            override fun checkTransaction(stx: SignedTransaction) {
            }
        })
        return subFlow(ReceiveFinalityFlow(counterpartySession))
    }
}


@CordaSerializable
class InsuranceInfo(val policyNumber: String, val insuredValue: Long, val duration: Int, val premium: Int, val vehicleInfo: VehicleInfo)


@CordaSerializable
class VehicleInfo(val registrationNumber: String, val chasisNumber: String, val make: String, val model: String, val variant: String,
                  val color: String, val fuelType: String)
