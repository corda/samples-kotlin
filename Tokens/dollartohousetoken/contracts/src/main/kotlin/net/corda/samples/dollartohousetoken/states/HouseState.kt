package net.corda.samples.dollartohousetoken.states

import net.corda.core.contracts.BelongsToContract
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.samples.dollartohousetoken.contracts.HouseContract
import java.util.Currency


// *********
// * State *
// *********
@BelongsToContract(HouseContract::class)
data class HouseState(override val linearId: UniqueIdentifier,
                      override val maintainers: List<Party>,
                      val valuationOfHouse: Amount<Currency>,
                      val noOfBedRooms: Int,
                      val constructionArea: String,
                      val additionInfo: String,
                      val address: String,
                      val issuer:Party = maintainers.single(),
                      override val fractionDigits: Int = 0
                      ) : EvolvableTokenType()
