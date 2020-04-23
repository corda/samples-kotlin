package net.corda.examples.bikemarket.states

import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.examples.bikemarket.contracts.WheelsContract

// *********
// * State *
// *********
@BelongsToContract(WheelsContract::class)
data class WheelsTokenState(val maintainer: Party,
                            val ModelNum: String,
                            override val fractionDigits: Int,
                            override val linearId: UniqueIdentifier,
                            override val maintainers: List<Party> = listOf(maintainer)) : EvolvableTokenType()
