package net.corda.samples.bikemarket.db

import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService

val TABLE_NAME = "bike_states"
/**
 * A database service subclass for handling a table of bike values.
 *
 * @param services The node's service hub.
 */
@CordaService
class BikeValuesDatabaseService(services: ServiceHub) : DatabaseService(services) {
    init {
        setUpStorage()
    }


    fun aggregateBikesByBrand(): List<Pair<String, Int>> {
        val query = "select brand, count(*) as total from $TABLE_NAME group by brand"

        return executeQuery(query, emptyMap()) { it -> Pair(it.getString("brand"), it.getInt("total")) }
    }






    /**
     * Initialises the table of crypto values.
     */
    private fun setUpStorage() {
        val query = "create table if not exists $TABLE_NAME (token varchar(64), token_value int) "

        executeUpdate(query, emptyMap())
        log.info("Created bike_table table.")
    }
}