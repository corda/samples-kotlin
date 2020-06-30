package net.corda.examples.bikemarket.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.utilities.withNotary
import com.r3.corda.lib.tokens.workflows.flows.rpc.CreateEvolvableTokens
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.utilities.ProgressTracker
import net.corda.examples.bikemarket.states.WheelsTokenState

// *********
// * Flows *
// *********
@StartableByRPC
class CreateWheelToken(private val wheelSerial: String) : FlowLogic<String>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call():String {
        // Obtain a reference from a notary we wish to use.
        /**
         *  METHOD 1: Take first notary on network, WARNING: use for test, non-prod environments, and single-notary networks only!*
         *  METHOD 2: Explicit selection of notary by CordaX500Name - argument can by coded in flow or parsed from config (Preferred)
         *
         *  * - For production you always want to use Method 2 as it guarantees the expected notary is returned.
         */
        val notary = serviceHub.networkMapCache.notaryIdentities.single() // METHOD 1
        // val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB")) // METHOD 2

        //Create non-fungible frame token
        val uuid = UniqueIdentifier()
        val wheel = WheelsTokenState(ourIdentity, wheelSerial,0,uuid)

        //warp it with transaction state specifying the notary
        val transactionState = wheel withNotary notary

        subFlow(CreateEvolvableTokens(transactionState))

        return "\nCreated a wheel token for bike wheels. (Serial #" + this.wheelSerial + ")."

    }
}
