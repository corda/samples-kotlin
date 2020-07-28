package net.corda.samples.stockpaydividend.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.money.FiatCurrency.Companion.getInstance
import com.r3.corda.lib.tokens.workflows.utilities.tokenBalance
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.utilities.ProgressTracker
import net.corda.samples.stockpaydividend.flows.utilities.QueryUtilities
import net.corda.samples.stockpaydividend.states.StockState

// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC
class GetStockBalance(val symbol: String) : FlowLogic<String?>() {

    @Suspendable
    @Throws(FlowException::class)
    override fun call(): String {
        val stockPointer: TokenPointer<StockState> = QueryUtilities.queryStockPointer(symbol, serviceHub)
        val (quantity) = serviceHub.vaultService.tokenBalance(stockPointer)
        return "\n You currently have $quantity $symbol stocks\n"
    }
}

@InitiatingFlow
@StartableByRPC
class GetFiatBalance(private val currencyCode: String) : FlowLogic<Amount<TokenType>>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    @Throws(FlowException::class)
    override fun call(): Amount<TokenType> {
        val fiatTokenType = getInstance(currencyCode)
        return serviceHub.vaultService.tokenBalance(fiatTokenType)
    }
}