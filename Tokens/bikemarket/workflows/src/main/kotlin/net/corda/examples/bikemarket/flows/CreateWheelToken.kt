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
        //Notary
        val notary = serviceHub.networkMapCache.notaryIdentities[0]

        //Create non-fungible frame token
        val uuid = UniqueIdentifier()
        val wheel = WheelsTokenState(ourIdentity, wheelSerial,0,uuid)

        //warp it with transaction state specifying the notary
        val transactionState = wheel withNotary notary

        subFlow(CreateEvolvableTokens(transactionState))

        return "\nCreated a wheel token for bike wheels. (Serial #" + this.wheelSerial + ")."

    }
}
