package com.t20worldcup.flows

import co.paralleluniverse.fibers.Suspendable
import com.t20worldcup.states.T20CricketTicketState
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.ProgressTracker
import java.util.*

// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC
class QuerybyAccount(private val whoAmI:String) : FlowLogic<String>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call():String {
        val myAccount = accountService.accountInfo(whoAmI).single().state.data
        val criteria = QueryCriteria.VaultQueryCriteria(externalIds = listOf(myAccount.identifier.id))

        //Ticket
        val ticketList = serviceHub.vaultService.queryBy<NonFungibleToken>(criteria = criteria).states
        val myTicketIDs = ticketList.map { it.state.data.tokenType.tokenIdentifier }
        val tkList = myTicketIDs.map {
            val uuid = UUID.fromString(it)
            val queryCriteria = QueryCriteria.LinearStateQueryCriteria(uuid = listOf(uuid),status = Vault.StateStatus.UNCONSUMED)
            val stateAndRef = serviceHub.vaultService.queryBy<T20CricketTicketState>(queryCriteria).states[0]
            val description = stateAndRef.state.data.ticketTeam
            description
        }
        //Assets
        val asset = serviceHub.vaultService.queryBy(FungibleToken::class.java, criteria).states
        val myMoney = asset.map { it.state.data.amount.quantity.toString() + " " + it.state.data.tokenType.tokenIdentifier}
        return "\nI have ticket(s) for $tkList" +
                "\nI have money of $myMoney"
    }
}
