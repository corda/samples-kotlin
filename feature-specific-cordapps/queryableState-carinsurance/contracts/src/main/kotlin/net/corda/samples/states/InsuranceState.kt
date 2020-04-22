package net.corda.samples.states

import net.corda.samples.contracts.InsuranceContract
import net.corda.samples.schema.InsuranceSchemaV1
import net.corda.core.contracts.BelongsToContract
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.lang.IllegalArgumentException
import java.util.*
import kotlin.collections.ArrayList


// *********
// * State *
// *********
@BelongsToContract(InsuranceContract::class)
data class InsuranceState(val policyNumber: String,
                          val insuredValue: Long,
                          val duration: Int,
                          val premium:Int,
                          val insurer: Party,
                          val insuree: Party,
                          val vehicleDetail: VehicleDetail,
                          val claims:List<Claim> = listOf(),
                          override val participants: List<AbstractParty> = listOf(insuree,insurer)) : QueryableState {
    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        if (schema is InsuranceSchemaV1){
            var persistentClaims = ArrayList<InsuranceSchemaV1.PersistentClaim>()
            if(claims.isNotEmpty()) {
                for (item in claims){
                    persistentClaims.add(InsuranceSchemaV1.PersistentClaim(
                            UUID.randomUUID(),
                            item.claimNumber,
                            item.claimDescription,
                            item.claimAmount))
                }
            }
            var vDetail = InsuranceSchemaV1.PersistentVehicle(vehicleDetail.registrationNumber,
                    vehicleDetail.chasisNumber,
                    vehicleDetail.make,
                    vehicleDetail.model,
                    vehicleDetail.variant,
                    vehicleDetail.color,
                    vehicleDetail.fuelType)

            return InsuranceSchemaV1.PersistentInsurance(
                    policyNumber,
                    insuredValue,
                    duration,
                    premium,
                    vDetail,
                    persistentClaims
            )
        }else
            throw IllegalArgumentException("Unsupported Schema")
    }
    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(InsuranceSchemaV1)
}

