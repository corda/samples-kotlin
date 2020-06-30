package net.corda.samples.fungiblehousetoken.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.utilities.withNotary
import com.r3.corda.lib.tokens.workflows.flows.rpc.CreateEvolvableTokens
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.samples.fungiblehousetoken.states.FungibleHouseTokenState
import java.math.BigDecimal

// *********
// * Flows *
// *********
@StartableByRPC
class CreateHouseTokenFlow(val valuationOfHouse:BigDecimal,
                           val symbol: String) : FlowLogic<String>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call():String {
        // Obtain a reference from a notary we wish to use.
        /**
         *  METHOD 1: Take first notary on network, WARNING: use for test, non-prod environments, and single-notary networks only!*
         *  METHOD 2: Explicit selection of notary by CordaX500Name - argument can by coded in flow or parsed from config (Preferred)
         *
         *  * - For production you always want to use Method 2 as it guarantees the expected notary is returned.
         */
        val notary = serviceHub.networkMapCache.notaryIdentities.single() // METHOD 1
        // val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB")) // METHOD 2

        //create token type
        val evolvableTokenTypeHouseState = FungibleHouseTokenState(valuationOfHouse,ourIdentity,symbol,0,UniqueIdentifier())

        //warp it with transaction state specifying the notary
        val transactionState = evolvableTokenTypeHouseState withNotary notary

        //call built in sub flow CreateEvolvableTokens. This can be called via rpc or in unit testing
        val stx = subFlow(CreateEvolvableTokens(transactionState))

        return "Fungible house token $symbol has created with valuationL: $valuationOfHouse " +
                "\ntxId: ${stx.id}"
    }
}

