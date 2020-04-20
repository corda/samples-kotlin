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
class TransferPartToken(val part: String,
                   val serial: String,
                   val holder: Party) : FlowLogic<String>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call():String {
        if (part.equals("frame")){
            val frameSerial = serial
            //transfer frame token
            val frameStateAndRef = serviceHub.vaultService.queryBy<FrameTokenState>().states
                    .filter { it.state.data.ModelNum.equals(frameSerial) }[0]

            //get the TokenType object
            val frametokentype = frameStateAndRef.state.data

            //get the pointer pointer to the frame
            val frametokenPointer: TokenPointer<*> = frametokentype.toPointer(frametokentype.javaClass)
            val partyAndFrameToken = PartyAndToken(holder,frametokenPointer)

            val stx = subFlow(MoveNonFungibleTokens(partyAndFrameToken))
            return ("Transfer ownership of the frame (" + this.serial + ") to" + holder.name.organisation
                    + "\nTransaction ID: " + stx.id)
        }else if(part.equals("wheels")){
            val wheelSerial = serial
            //transfer wheel token
            val wheelStateAndRef = serviceHub.vaultService.queryBy<WheelsTokenState>().states
                    .filter { it.state.data.ModelNum.equals(wheelSerial) }[0]

            //get the TokenType object
            val wheeltokentype: WheelsTokenState = wheelStateAndRef.state.data

            //get the pointer pointer to the wheel
            val wheeltokenPointer: TokenPointer<*> = wheeltokentype.toPointer(wheeltokentype.javaClass)
            val partyAndWheelToken = PartyAndToken(holder, wheeltokenPointer)

            val stx = subFlow(MoveNonFungibleTokens(partyAndWheelToken))
            return ("Transfer ownership of the frame (" + this.serial + ") to" + holder.name.organisation
                    + "\nTransaction ID: " + stx.id)
        }else{
            return "Please enter either frame or wheels for parameter part."
        }
    }
}

@InitiatedBy(TransferPartToken::class)
class TransferPartTokenResponder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // Responder flow logic goes here.
        subFlow(MoveNonFungibleTokensHandler(counterpartySession));
    }
}

