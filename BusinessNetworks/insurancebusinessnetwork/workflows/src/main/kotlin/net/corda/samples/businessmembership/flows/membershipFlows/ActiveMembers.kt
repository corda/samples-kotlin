package net.corda.samples.businessmembership.flows.membershipFlows

import co.paralleluniverse.fibers.Suspendable
import net.corda.bn.flows.ActivateMembershipFlow
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC

@StartableByRPC
class ActiveMembers(val membershipId: UniqueIdentifier): FlowLogic<String>()  {

    @Suspendable
    override fun call(): String {
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        subFlow(ActivateMembershipFlow(this.membershipId,notary))
        return "\nMember(${this.membershipId})'s network membership has been activated."
    }
}
//flow start ActiveMembers membershipId: