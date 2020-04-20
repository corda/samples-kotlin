package net.corda.examples.bikemarket.states

import net.corda.core.contracts.BelongsToContract
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.examples.bikemarket.contracts.FrameContract

// *********
// * State *
// *********
@BelongsToContract(FrameContract::class)
data class FrameTokenState(val maintainer: Party,
                           val ModelNum: String,
                           override val fractionDigits: Int,
                           override val linearId: UniqueIdentifier,
                           override val maintainers: List<Party> = listOf(maintainer)) : EvolvableTokenType()
