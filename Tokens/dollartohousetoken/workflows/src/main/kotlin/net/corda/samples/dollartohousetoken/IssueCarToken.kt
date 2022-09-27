package net.corda.samples.dollartohousetoken.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.samples.dollartohousetoken.states.CarState

@StartableByRPC
class IssueCarToken(val owner: Party, val brand: String) : FlowLogic<String>() {

    @Suspendable
    override fun call(): String {
        val issuer = ourIdentity

        val stateAndRef  = serviceHub.vaultService.queryBy<CarState>()
                .states.filter { it.state.data.brand.equals(brand) }[0]

        val carState = stateAndRef.state.data

        val issuedCarToken = carState.toPointer(carState.javaClass) issuedBy issuer

        val carToken = NonFungibleToken(issuedCarToken, owner, UniqueIdentifier())

        subFlow(IssueTokens(listOf(carToken)))

        return "Issued Car Token to $owner"
    }
}