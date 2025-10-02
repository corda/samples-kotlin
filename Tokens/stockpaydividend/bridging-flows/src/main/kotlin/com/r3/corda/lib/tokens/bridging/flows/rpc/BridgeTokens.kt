package com.r3.corda.lib.tokens.bridging.flows.rpc

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.bridging.flows.BridgeFungibleTokenFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import net.corda.core.contracts.StateAndRef
import net.corda.core.utilities.ProgressTracker

// This flow is only for tests. In real life the @BridgingAuthorityBootstrapService.kt would run the BridgeFungibleTokenFlow directly.
@InitiatingFlow
@StartableByRPC
class BridgeToken(
    val token: StateAndRef<FungibleToken>,
    val bridgeAuthority: Party
) : FlowLogic<String>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): String {

        //Use built-in flow for move tokens to the recipient
        val stx = subFlow(
            BridgeFungibleTokenFlow(
                ourIdentity,
                emptyList(),
                token,
                bridgeAuthority
            )
        )

        return ("\nBridged quantity " + token.state.data.amount + " " + " stocks to "
                + ourIdentity.name.organisation + ".\nTransaction ID: " + stx.id)
    }
}

