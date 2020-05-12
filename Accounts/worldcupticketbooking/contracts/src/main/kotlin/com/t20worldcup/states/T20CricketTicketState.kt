package com.t20worldcup.states

import com.t20worldcup.contracts.T20CricketTicketContract
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party

// *********
// * State *
// *********
@BelongsToContract(T20CricketTicketContract::class)
data class T20CricketTicketState(override val linearId: UniqueIdentifier,
                                 val ticketTeam: String,
                                 val issuer: Party,
                                 override val fractionDigits: Int = 0,
                                 override val maintainers: List<Party> = listOf(issuer)) : EvolvableTokenType()
