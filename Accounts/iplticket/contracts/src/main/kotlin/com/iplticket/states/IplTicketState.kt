package com.iplticket.states

import com.iplticket.contracts.IplTicketStateContract
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

// *********
// * State *
// *********
@BelongsToContract(IplTicketStateContract::class)
data class IplTicketState(override val linearId: UniqueIdentifier,
                          val ticketTeam: String,
                          val issuer: Party,
                          override val fractionDigits: Int = 0,
                          override val maintainers: List<Party> = listOf(issuer)) : EvolvableTokenType()
