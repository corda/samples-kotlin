package net.corda.samples.greeting.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

// Data class to hold our greeting message with a sender name and message content
@CordaSerializable
data class GreetingMessage(val senderName: String, val message: String)

@InitiatingFlow
@StartableByRPC
class SendGreeting(
    private val recipient: Party,
    private val senderName: String,
    private val message: String
) : FlowLogic<String>() {

    override val progressTracker = ProgressTracker(
        SENDING_GREETING,
        RECEIVING_RESPONSE
    )

    companion object {
        object SENDING_GREETING : ProgressTracker.Step("Sending greeting to recipient")
        object RECEIVING_RESPONSE : ProgressTracker.Step("Waiting for acknowledgment")
    }

    @Suspendable
    override fun call(): String {
        progressTracker.currentStep = SENDING_GREETING

        // Create a session with the recipient
        val recipientSession = initiateFlow(recipient)

        // Create and send our greeting message
        val greetingMessage = GreetingMessage(senderName, message)

        // Send the greeting and receive the response
        progressTracker.currentStep = RECEIVING_RESPONSE
        val response = recipientSession.sendAndReceive<String>(greetingMessage).unwrap { it }

        return "Greeting sent successfully. Response: $response"
    }
}

@InitiatedBy(SendGreeting::class)
class ReceiveGreeting(private val counterpartySession: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        // Receive the greeting message
        val receivedGreeting = counterpartySession.receive<GreetingMessage>().unwrap { it }

        // Log the received greeting
        logger.info("Received greeting from ${receivedGreeting.senderName}: ${receivedGreeting.message}")

        // Create a personalized acknowledgment
        val acknowledgment = "Hello ${receivedGreeting.senderName}, thanks for your message!"

        // Send the acknowledgment back
        counterpartySession.send(acknowledgment)
    }
}