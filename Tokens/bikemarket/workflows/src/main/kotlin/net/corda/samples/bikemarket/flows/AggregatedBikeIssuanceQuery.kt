package net.corda.samples.bikemarket.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.utilities.ProgressTracker
import net.corda.samples.bikemarket.db.BikeValuesDatabaseService


/**
 * Adds a crypto token and associated value to the table of crypto values.
 */
@InitiatingFlow
@StartableByRPC
class AggregatedBikeIssuanceQuery() : FlowLogic<List<Pair<String, Int>>>() {
    override val progressTracker: ProgressTracker = ProgressTracker()

    @Suspendable
    override fun call(): List<Pair<String, Int>> {
        val databaseService = serviceHub.cordaService(BikeValuesDatabaseService::class.java)
        // BE CAREFUL when accessing the node's database in flows:
        // 1. The operation must be executed in a BLOCKING way. Flows don't
        //    currently support suspending to await a database operation's
        //    response
        // 2. The operation must be idempotent. If the flow fails and has to
        //    restart from a checkpoint, the operation will also be replayed
        return databaseService.aggregateBikesByBrand()
    }
}

/**
 * Updates the value of a crypto token in the table of crypto values.
 */
//@InitiatingFlow
//@StartableByRPC
//class UpdateTokenValueFlow(private val token: String, private val value: Int) : FlowLogic<Unit>() {
//    override val progressTracker: ProgressTracker = ProgressTracker()
//
//    @Suspendable
//    override fun call() {
//        val databaseService = serviceHub.cordaService(CryptoValuesDatabaseService::class.java)
//        databaseService.updateTokenValue(token, value)
//    }
//}
//
///**
// * Retrieves the value of a crypto token in the table of crypto values.
// */
//@InitiatingFlow
//@StartableByRPC
//class QueryTokenValueFlow(private val token: String) : FlowLogic<Int>() {
//    override val progressTracker: ProgressTracker = ProgressTracker()
//
//    @Suspendable
//    override fun call(): Int {
//        val databaseService = serviceHub.cordaService(CryptoValuesDatabaseService::class.java)
//        return databaseService.queryTokenValue(token)
//    }
//}