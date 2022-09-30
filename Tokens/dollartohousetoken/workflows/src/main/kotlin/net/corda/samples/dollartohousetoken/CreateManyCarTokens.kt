package net.corda.samples.dollartohousetoken

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.withNotary
import com.r3.corda.lib.tokens.money.FiatCurrency
import com.r3.corda.lib.tokens.workflows.flows.rpc.CreateEvolvableTokens
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.randomOrNull
import net.corda.samples.dollartohousetoken.states.CarState
import java.util.*


@StartableByRPC
class CreateManyCarTokens(
        val carValue: Amount<Currency>, val mileage: Int,
        val total: Int
) : FlowLogic<String>(){

    val brands = listOf("VW", "Mercedes", "BMW", "Ferrari")
    val random = Random()
    var randomValue: Int = 0

    @Suspendable
    override fun call(): String {
        val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB"))
        val issuer = ourIdentity

        for (i in  1..total) {

            val brand =   brands.randomOrNull();
            randomValue = rand(1000, 5000)
            /* Construct the Car state */
            val carState = CarState(UniqueIdentifier(), Arrays.asList(issuer), carValue,mileage, brand!!)

            /* Create an instance of TransactionState using the carState token and the notary */
            val transactionState = carState withNotary notary!!

            /* Create the car token.*/
            subFlow(CreateEvolvableTokens(transactionState))
        }

        return "Tokens created"
    }

    fun rand(from: Int, to: Int) : Int {
        return random.nextInt(to - from) + from
    }
}