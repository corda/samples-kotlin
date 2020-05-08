package com.t20worldcup.flows

import co.paralleluniverse.fibers.Suspendable
import com.t20worldcup.states.T20CricketTicketState
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.services.Vault.StateStatus
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.QueryCriteria.LinearStateQueryCriteria
import java.util.*

// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC
class IssueNonFungibleTicketFlow(private val tokenId:String,
                                 val dealerAccountName:String) : FlowLogic<String>() {
    @Suspendable
    override fun call():String {

        val targetAccount = accountService.accountInfo(dealerAccountName)[0].state.data
        val targetAcctAnonymousParty = subFlow(RequestKeyForAccount(targetAccount))

        val uuid = UUID.fromString(tokenId)
        //construct the query criteria and get the base token type
        val queryCriteria: QueryCriteria = LinearStateQueryCriteria(uuid = listOf(uuid),status = StateStatus.UNCONSUMED)

        //grab the created ticket type off the ledger
        val stateAndRef = serviceHub.vaultService.queryBy(T20CricketTicketState::class.java, queryCriteria).states[0]
        val ticketState  = stateAndRef.state.data

        //assign the issuer to the t20worldcup type who is the BCCI node
        val issuedTicketToken = ticketState.toPointer(ticketState.javaClass) issuedBy ourIdentity

        val houseToken = NonFungibleToken(issuedTicketToken, targetAcctAnonymousParty, UniqueIdentifier())

        //call built in flow to issue non fungible tokens
        val stx = subFlow(IssueTokens(listOf(houseToken)))
        return "Issued the ticket of ${ticketState.ticketTeam} to $dealerAccountName" +
                "\ntxId: ${stx.id}"
    }
}
