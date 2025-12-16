package net.corda.samples.solanadvp.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.withNotary
import com.r3.corda.lib.tokens.workflows.flows.rpc.CreateEvolvableTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.UniqueIdentifier.Companion.fromString
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.utilities.ProgressTracker
import net.corda.samples.solanadvp.states.DeliveryState
import java.util.*

// *********
// * Flows *
// *********
@StartableByRPC
class CreateAndIssueToken(val owner: Party, val price: Amount<Currency>) : FlowLogic<String>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call():String {
        // Obtain a reference from a notary we wish to use.
        val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB"))

        /* Get a reference of own identity */
        val issuer = ourIdentity

        /* Construct the output state */
        fromString(UUID.randomUUID().toString())
        val state = DeliveryState(UniqueIdentifier(), listOf(issuer), price)

        /* Create an instance of TransactionState using the deliveryState token and the notary */
        val transactionState = state withNotary notary!!

        /* Create the token. TokenSDK provides the CreateEvolvableTokens flow which could be called to create an evolvable token in the ledger.*/
        subFlow(CreateEvolvableTokens(transactionState))

        /*
        * Create an instance of IssuedTokenType, it is used by our Non-Fungible token which would be issued to the owner. Note that the IssuedTokenType takes
        * a TokenPointer as an input, since EvolvableTokenType is not TokenType, but is a LinearState. This is done to separate the state info from the token
        * so that the state can evolve independently.
        * IssuedTokenType is a wrapper around the TokenType and the issuer.
        */

        val issuedToken = state.toPointer(state.javaClass) issuedBy  issuer

        /* Create an instance of the non-fungible token with the owner as the token holder.
        The last parameter is a hash of the jar containing the TokenType, use the helper function to fetch it. */
        val token = NonFungibleToken(issuedToken, owner, UniqueIdentifier())

        /* Issue the token by calling the IssueTokens flow provided with the TokenSDK */
        val stx = subFlow(IssueTokens(listOf(token)))

        return ("\nThe non-fungible token is created with UUID: " + state.linearId + ". (This is what you will use in next step)"
                + "\nTransaction ID: " + stx.id)
    }
}
