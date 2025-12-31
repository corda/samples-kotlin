package net.corda.samples.solana.dvp.contracts

import com.r3.corda.lib.tokens.contracts.commands.Create
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.samples.solana.dvp.states.StockState
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test
import java.math.BigDecimal
import java.util.Date

class ContractTests {
    private val ledgerServices = MockServices()
    val issuer = TestIdentity(CordaX500Name(organisation = "Alice", locality = "TestLand", country = "US"))

    //sample tests
    @Test
    fun `Price must be greater than zero`() {

        val tokenPass = StockState(
            issuer.identity.party,
            "AAPL",
            "Apple",
            "USD",
            BigDecimal(273.12),
            BigDecimal.ZERO,
            Date(),
            Date(),
            UniqueIdentifier()
        )
        val tokenFail = StockState(
            issuer.identity.party,
            "AAPL",
            "Apple",
            "USD",
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            Date(),
            Date(),
            UniqueIdentifier()
        )
        ledgerServices.ledger {
            transaction {
                output(StockContract.CONTRACT_ID, tokenFail)
                command(issuer.publicKey, Create())
                this.fails()
            }
        }
        ledgerServices.ledger {
            transaction {
                output(StockContract.CONTRACT_ID, tokenPass)
                command(issuer.publicKey, Create())
                this.verifies()
            }
        }
    }
}