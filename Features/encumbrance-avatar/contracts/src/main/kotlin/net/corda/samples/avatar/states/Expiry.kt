package net.corda.samples.avatar.states

import net.corda.samples.avatar.contracts.ExpiryContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import java.time.Instant
import java.util.Objects

@BelongsToContract(ExpiryContract::class)
class Expiry(val expiry: Instant,
             val avatarId: String,
             val owner: AbstractParty,
             override val participants: List<AbstractParty> = listOf(owner)) : ContractState {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return (other is Expiry) && expiry == other.expiry && avatarId == other.avatarId
    }

    override fun hashCode(): Int {
        return Objects.hash(expiry, avatarId)
    }
}
