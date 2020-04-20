package net.corda.examples.bikemarket.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveNonFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveNonFungibleTokensHandler
import com.r3.corda.lib.tokens.workflows.types.PartyAndToken
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.ProgressTracker
import net.corda.examples.bikemarket.states.FrameTokenState
import net.corda.examples.bikemarket.states.WheelsTokenState

// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC
class TransferBikeToken(val frameSerial: String,
                   val wheelSerial: String,
                   val holder: Party) : FlowLogic<String>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call():String {
        //Step 1: Frame Token
        //get frame states on ledger
        val frameStateAndRef = serviceHub.vaultService.queryBy<FrameTokenState>().states
                .filter { it.state.data.ModelNum.equals(frameSerial) }[0]

        //get the TokenType object
        val frametokentype = frameStateAndRef.state.data

        //get the pointer pointer to the frame
        val frametokenPointer: TokenPointer<*> = frametokentype.toPointer(frametokentype.javaClass)
        val partyAndFrameToken = PartyAndToken(holder,frametokenPointer)

        val stx1 = subFlow(MoveNonFungibleTokens(partyAndFrameToken))

        //Step 2: Wheels Token
        val wheelStateAndRef = serviceHub.vaultService.queryBy<WheelsTokenState>().states
                .filter { it.state.data.ModelNum.equals(wheelSerial) }[0]

        //get the TokenType object
        val wheeltokentype: WheelsTokenState = wheelStateAndRef.state.data

        //get the pointer pointer to the wheel
        val wheeltokenPointer: TokenPointer<*> = wheeltokentype.toPointer(wheeltokentype.javaClass)
        val partyAndWheelToken = PartyAndToken(holder, wheeltokenPointer)

        val stx2 = subFlow(MoveNonFungibleTokens(partyAndWheelToken))

        return ("\nTransfer ownership of a bike (Frame serial#: " + this.frameSerial + ", Wheels serial#: " + this.wheelSerial + ") to "
                + holder.name.organisation + "\nTransaction IDs: "
                + stx1.id + ", " + stx2.id)
    }
}

@InitiatedBy(TransferBikeToken::class)
class TransferBikeTokenResponder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // Responder flow logic goes here.
        subFlow(MoveNonFungibleTokensHandler(counterpartySession));
    }
}

