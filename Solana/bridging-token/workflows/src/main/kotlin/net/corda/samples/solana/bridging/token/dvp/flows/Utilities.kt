package net.corda.samples.solana.bridging.token.dvp.flows

import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import net.corda.core.contracts.StateAndRef
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.queryBy
import net.corda.samples.stockpaydividend.states.StockState
import java.math.BigDecimal
import java.math.RoundingMode

object StockQueryUtilities {
    /**
     * Retrieve any unconsumed StockState and filter by the given symbol
     */
    fun queryStock(symbol: String, serviceHub: ServiceHub): StateAndRef<StockState> {
        val stateAndRefs: List<StateAndRef<StockState>> = serviceHub.vaultService.queryBy<StockState>().states
        // Match the query result with the symbol. If no results match, throw exception
        return stateAndRefs.stream()
            .filter { (state) -> state.data.symbol == symbol }.findAny()
            .orElseThrow { IllegalArgumentException("StockState symbol=\"$symbol\" not found from vault") }
    }

    /**
     * Retrieve any unconsumed StockState and filter by the given symbol
     * Then return the pointer to this StockState
     */
    fun queryStockPointer(symbol: String, serviceHub: ServiceHub): TokenPointer<StockState> {
        val (state) = queryStock(symbol, serviceHub)
        return state.data.toPointer(StockState::class.java)
    }
}

fun BigDecimal.toScaledLong(decimals: Int, rounding: RoundingMode = RoundingMode.DOWN): Long =
    this.movePointRight(decimals)
        .setScale(0, rounding)   // make it an integer
        .longValueExact()
