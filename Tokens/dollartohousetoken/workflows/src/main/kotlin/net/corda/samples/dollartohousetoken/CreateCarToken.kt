package net.corda.samples.dollartohousetoken.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.utilities.withNotary
import com.r3.corda.lib.tokens.workflows.flows.rpc.CreateEvolvableTokens
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.UniqueIdentifier.Companion.fromString
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.ProgressTracker
import net.corda.samples.dollartohousetoken.states.CarState
import java.util.*

// *********
// * Flows *
// *********
@StartableByRPC
class CreateCarToken(val carValue: Amount<Currency>, val mileage: Int,
                     val brand: String
) : FlowLogic<String>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): String {
        // Obtain a reference from a notary we wish to use.
        val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB"))

        /* Get a reference of own identity */
        val issuer = ourIdentity

        /* Construct the Car state */
        val carState = CarState(UniqueIdentifier(), Arrays.asList(issuer), carValue, mileage, brand)

        /* Create an instance of TransactionState using the carState token and the notary */
        val transactionState = carState withNotary notary!!

        /* Create the car token.*/
        subFlow(CreateEvolvableTokens(transactionState))

        return ("\nThe non-fungible car token is created with ID: ${carState.linearId}")
    }
}