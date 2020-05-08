package com.t20worldcup.flows.accountsUtilities

import co.paralleluniverse.fibers.Suspendable
import com.t20worldcup.flows.accountsUtilities.CreateNewAccount
import com.t20worldcup.flows.accountsUtilities.ShareAccountTo
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.utilities.ProgressTracker

// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC
class CreateAndShareAccountFlow(private val accountName: String,
                                private val partyToShareAccountInfoTo: Party) : FlowLogic<String>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call():String {
        // Initiator flow logic goes here.
        subFlow(CreateNewAccount(accountName))
        subFlow(ShareAccountTo(accountName,partyToShareAccountInfoTo))
        return "$accountName account has been created and shared to $partyToShareAccountInfoTo."
    }
}
