package net.corda.samples.solana.dvp.contracts

import com.r3.corda.lib.tokens.contracts.EvolvableTokenContract
import com.r3.corda.lib.tokens.contracts.commands.Create
import com.r3.corda.lib.tokens.contracts.commands.EvolvableTokenTypeCommand
import com.r3.corda.lib.tokens.contracts.commands.Update
import net.corda.core.contracts.Contract
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import net.corda.samples.solana.dvp.states.StockState
import java.math.BigDecimal


class StockContract : EvolvableTokenContract(), Contract {
    companion object {
        const val CONTRACT_ID = "net.corda.samples.solana.dvp.contracts.StockContract"
    }

    @Throws(IllegalArgumentException::class)
    override fun verify(tx: LedgerTransaction) {
        val outputState: StockState = tx.getOutput(0) as StockState
        if (!tx.getCommand<CommandData>(0).signers.contains(outputState.issuer.owningKey))
            throw IllegalArgumentException("Company Signature Required")
        val command = tx.commands.requireSingleCommand<EvolvableTokenTypeCommand>()
        when (command.value) {
            is Create -> additionalCreateChecks(tx)
            is Update -> additionalUpdateChecks(tx)
        }
    }

    override fun additionalCreateChecks(tx: LedgerTransaction) {
        val createdStockState: StockState = tx.outputsOfType(StockState::class.java)[0]
        requireThat {
            "Stock symbol must not be empty".using(!createdStockState.symbol.isEmpty())
            "Stock name must not be empty".using(!createdStockState.name.isEmpty())
            "Stock price must be greater than zero".using(createdStockState.price > BigDecimal.ZERO)
            "Stock dividend must start with zero".using(createdStockState.dividend == BigDecimal.ZERO)
        }
    }

    override fun additionalUpdateChecks(tx: LedgerTransaction) {
        val input: StockState = tx.inputsOfType(StockState::class.java)[0]
        val output: StockState = tx.outputsOfType(StockState::class.java)[0]
        requireThat {
            "Stock Symbol must not be changed.".using(input.symbol == output.symbol)
            "Stock Currency must not be changed.".using(input.currency == output.currency)
            "Stock Name must not be changed.".using(input.name == output.name)
            "Stock Issuer must not be changed.".using(input.issuer == output.issuer)
        }
    }
}