package net.corda.samples.dollartohousetoken.states

import net.corda.core.contracts.BelongsToContract
import net.corda.core.identity.AbstractParty
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.samples.dollartohousetoken.contracts.HouseContract
import java.util.*


// *********
// * State *
// *********
@BelongsToContract(HouseContract::class)
data class HouseState(val constructionArea: String,
                      val additionInfo: String,
                      val valuationOfHouse: Amount<Currency>,
                      val address: String,
                      val noOfBedRooms: Int,
                      val issuer:Party,
                      override val linearId: UniqueIdentifier,
                      override val fractionDigits: Int = 0,
                      override val maintainers: List<Party> = listOf(issuer)) : EvolvableTokenType()
