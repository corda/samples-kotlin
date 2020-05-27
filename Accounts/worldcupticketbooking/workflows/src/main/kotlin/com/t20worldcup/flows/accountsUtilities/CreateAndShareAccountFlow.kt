package com.t20worldcup.flows.accountsUtilities

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfo
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.utilities.ProgressTracker

// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC
class CreateAndShareAccountFlow(private val accountName: String,
                                private val partyToShareAccountInfoTo: List<Party>) : FlowLogic<String>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call():String {

        //Call inbuilt CreateAccount flow to create the AccountInfo object
        subFlow(ShareAccountInfo(subFlow(CreateAccount(accountName)),partyToShareAccountInfoTo))
        return "$accountName account has been created and shared to $partyToShareAccountInfoTo."
    }
}
