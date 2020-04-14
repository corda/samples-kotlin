package net.corda.samples.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.io.Serializable
import java.util.*
import javax.persistence.*

/**
 * The family of schemas for IOUState.
 */
object InsuranceSchema

/**
 * An IOUState schema.
 */
object InsuranceSchemaV1 : MappedSchema(
        schemaFamily = InsuranceSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentClaim::class.java, PersistentInsurance::class.java, PersistentVehicle::class.java)) {

    @Entity
    @Table(name = "CLAIM_DETAIL")
    class PersistentClaim(
            @Column(name = "claimNumber")
            var claimNumber: String,

            @Column(name = "claimDescription")
            var claimDescription: String,

            @Column(name = "claimAmount")
            var claimAmount: Int
    ) : PersistentState() {
        // Default constructor required by hibernate.
        constructor(): this("", "", 0)
    }

    @Entity
    @Table(name = "VEHICLE_DETAIL")
    class PersistentVehicle(
            @Column(name = "registrationNumber")
            val registrationNumber: String,

            @Column(name = "chasisNumber")
            val chasisNumber: String,

            @Column(name = "make")
            val make: String,

            @Column(name = "model")
            val model: String,

            @Column(name = "variant")
            val variant: String,

            @Column(name = "color")
            val color: String,

            @Column(name = "fuelType")
            val fuelType: String
    ):PersistentState(){
        // Default constructor required by hibernate.
        constructor(): this("", "", "", "","","","")
    }


    @Entity
    @Table(name = "INSURANCE_DETAIL")
    class PersistentInsurance(
            @Column(name = "policyNumber")
            val policyNumber: String,
            @Column(name = "insuredValue")
            val insuredValue:Long,
            @Column(name = "duration")
            val duration:Int,
            @Column(name = "premium")
            val premium: Int,
            @Column(name = "vehicle")
            val vehicle: PersistentVehicle,
            @Column(name = "claims")
            val claims: List<PersistentClaim>
    ):PersistentState(),Serializable{
        constructor(): this("", 0, 0, 0, PersistentVehicle(), listOf())
    }
}