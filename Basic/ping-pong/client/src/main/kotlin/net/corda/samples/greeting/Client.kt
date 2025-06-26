package net.corda.samples.greeting

import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCConnection
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.NetworkHostAndPort.Companion.parse
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.samples.greeting.flows.SendGreeting

val RPC_USERNAME = "user1"
val RPC_PASSWORD = "test"

fun main(args: Array<String>) {
    // Hardcoded values for testing
    val rpcAddressString = "localhost:10006"
    val recipientName = "O=PartyB,L=New York,C=US"
    val senderName = "Alice"
    val message = "Hello from the Greeting Cordapp!"

    println("Using hardcoded values for testing:")
    println("Address: $rpcAddressString")
    println("Recipient: $recipientName")
    println("Sender: $senderName")
    println("Message: $message")

    val greetingClient = GreetingClient(rpcAddressString)
    greetingClient.sendGreeting(recipientName, senderName, message)
    greetingClient.closeRpcConnection()
}

class GreetingClient(rpcAddressString: String) {
    companion object {
        val logger = loggerFor<GreetingClient>()
    }

    private val rpcConnection: CordaRPCConnection

    init {
        rpcConnection = establishRpcConnection(rpcAddressString, RPC_USERNAME, RPC_PASSWORD)
    }

    private fun establishRpcConnection(rpcAddressString: String, username: String, password: String): CordaRPCConnection {
        val nodeAddress = parse(rpcAddressString)
        val client = CordaRPCClient(nodeAddress)
        return client.start(username, password)
    }

    fun closeRpcConnection() {
        rpcConnection.close()
    }

    fun sendGreeting(recipientName: String, senderName: String, message: String) {
        val rpcProxy = rpcConnection.proxy

        val result = sendGreetingToRecipient(rpcProxy, recipientName, senderName, message)

        println("\nGreeting result: $result")
        logger.info("\nGreeting result: $result")
    }

    private fun sendGreetingToRecipient(
        rpcProxy: CordaRPCOps,
        recipientName: String,
        senderName: String,
        message: String
    ): String {
        val recipientX500Name = CordaX500Name.parse(recipientName)
        val recipient = rpcProxy.wellKnownPartyFromX500Name(recipientX500Name)
            ?: throw IllegalArgumentException("Recipient $recipientName not found in the network map.")

        val flowFuture = rpcProxy.startFlow(::SendGreeting, recipient, senderName, message).returnValue
        return flowFuture.getOrThrow()
    }
}