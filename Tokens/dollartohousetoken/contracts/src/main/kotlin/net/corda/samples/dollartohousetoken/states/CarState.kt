package net.corda.samples.dollartohousetoken.states

import net.corda.core.contracts.BelongsToContract
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.samples.dollartohousetoken.contracts.CarContract
import java.util.*


// *********
// * State *
// *********
@BelongsToContract(CarContract::class)
data class CarState(override val linearId: UniqueIdentifier,
                    override val maintainers: List<Party>,
                    val carValue: Amount<Currency>,
                    val mileage: Int,
                    val brand: String,
                    val issuer:Party = maintainers.single(),
                    override val fractionDigits: Int = 0
) : EvolvableTokenType()
