package net.corda.samples.states

import net.corda.samples.contracts.AssetContract
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty

// *********
// * State *
// *********
@BelongsToContract(AssetContract::class)
data class Asset(
        override val linearId: UniqueIdentifier,
        val title: String,
        val description: String,
        val imageUrl: String,
        override val owner: AbstractParty,
        override val participants: List<AbstractParty> = listOf(owner)) : OwnableState,LinearState {

    override fun withNewOwner(newOwner: net.corda.core.identity.AbstractParty): net.corda.core.contracts.CommandAndState {
        return CommandAndState(AssetContract.Commands.TransferAsset(), Asset(this.linearId, this.title, this.description, this.imageUrl, newOwner))
    }
}