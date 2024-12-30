package net.corda.samples.bikemarket.schema


import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table
//4.6 changes
import org.hibernate.annotations.Type


/**
 * The family of schemas for IOUState.
 */
object BIKE_SCHEMA

/**
 * An IOUState schema.
 */
object BikeSchema : MappedSchema(
    schemaFamily = BIKE_SCHEMA.javaClass,
    version = 1,
    mappedTypes = listOf(PersistentBike::class.java)
) {

    override val migrationResource: String?
        get() = "iou.changelog-master";

    @Entity
    @Table(name = "bike_states")
    class PersistentBike(

        @Column(name = "maintainer")
        var maintainer: String,

        @Column(name = "owner")
        var owner: String,

        @Column(name = "brand")
        var brand: String,

        @Column(name = "name")
        var name: String,

        @Column(name = "price")
        var price: Int,

        @Column(name = "wheels")
        var wheels: String,

        @Column(name = "groupset")
        var groupset: String,

        @Column(name = "linear_id")
        @Type(type = "uuid-char")
        var linearId: UUID
    ) : PersistentState() {
        // Default constructor required by hibernate.
        constructor() : this("", "", "", "", 0, "", "", UUID.randomUUID())
    }
}