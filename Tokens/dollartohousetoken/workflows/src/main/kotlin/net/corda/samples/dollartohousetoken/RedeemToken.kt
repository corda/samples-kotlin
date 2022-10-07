package net.corda.samples.dollartohousetoken

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.workflows.flows.rpc.RedeemNonFungibleTokens
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.samples.dollartohousetoken.states.CarState

@StartableByRPC
class RedeemToken(val carId: String, val issuer: Party) : FlowLogic<String>() {

    @Suspendable
    override fun call(): String {

        val ourParty = ourIdentity

        /* Fetch the car state from the vault using the vault query */
        val inputCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(UniqueIdentifier.fromString(carId)))
        val carStateAndRef = serviceHub.vaultService.queryBy<CarState>(criteria = inputCriteria).states.single()
        val carState = carStateAndRef.state.data

        val issuedCarToken = carState.toPointer(carState.javaClass) issuedBy issuer

        subFlow(RedeemNonFungibleTokens(issuedCarToken, issuer, listOf(ourParty)))

        return "Finished Redeeming ${issuedCarToken}"
    }
}