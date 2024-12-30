package net.corda.samples.bikemarket.states

import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.samples.bikemarket.contracts.BikeContract
import net.corda.samples.bikemarket.schema.BikeSchema


@BelongsToContract(BikeContract::class)
class BikeTokenState(
    val maintainer: Party,
    override val linearId: UniqueIdentifier,
    override val fractionDigits: Int,
    val owner: Party,
    val brand: String,
    val name: String,
    val price: Int,
    val wheels: String,
    val groupset: String,
    override val maintainers: List<Party> = listOf(maintainer)
) : EvolvableTokenType(), QueryableState {
    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is BikeSchema -> BikeSchema.PersistentBike(
                this.maintainer.name.toString(),
                this.owner.name.toString(),
                this.brand,
                this.name,
                this.price,
                this.wheels,
                this.groupset,
                this.linearId.id
            )

            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> {
        return listOf(BikeSchema)
    }

    fun copy(owner: Party): BikeTokenState {
        return BikeTokenState(
            maintainer = maintainer,
            linearId = linearId,
            fractionDigits = fractionDigits,
            owner = owner,
            brand = brand,
            name = name,
            price = price,
            wheels = wheels,
            groupset = groupset,
            maintainers = maintainers
        )

    }

}
