package net.corda.samples.solanadvp.states

import net.corda.core.contracts.BelongsToContract
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.samples.solanadvp.contracts.DeliveryContract
import java.util.Currency

/**
 * Generic asset state with a price.
 */
@BelongsToContract(DeliveryContract::class)
data class DeliveryState(override val linearId: UniqueIdentifier,
                         override val maintainers: List<Party>,
                         val price: Amount<Currency>,
                         val issuer: Party = maintainers.single(),
                         override val fractionDigits: Int = 0
                      ) : EvolvableTokenType()
