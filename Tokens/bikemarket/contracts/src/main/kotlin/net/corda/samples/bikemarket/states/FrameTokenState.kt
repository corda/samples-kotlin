package net.corda.samples.bikemarket.states

import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.samples.bikemarket.contracts.FrameContract


@BelongsToContract(FrameContract::class)
class FrameTokenState(val maintainer: Party,
                      override val linearId: UniqueIdentifier,
                      override val fractionDigits: Int,
                      val serialNum: String,
                      override val maintainers: List<Party> = listOf(maintainer)) : EvolvableTokenType()


