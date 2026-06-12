package net.corda.samples.referencestates.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.unwrap
import net.corda.samples.referencestates.states.SanctionedEntities

@CordaSerializable
data class SanctionsResponse(
    val hasData: Boolean,
    val message: String = ""
)

@InitiatingFlow
@StartableByRPC
class GetSanctionsListFlow(val otherParty: Party) : FlowLogic<List<StateAndRef<SanctionedEntities>>>() {

    @Suspendable
    override fun call(): List<StateAndRef<SanctionedEntities>> {
        val session = initiateFlow(otherParty)

        // Receive response about data availability
        val response = session.receive<SanctionsResponse>().unwrap { it }

        return if (response.hasData) {
            try {
                // Receive the transaction
                val stx = subFlow(ReceiveTransactionFlow(session, true, StatesToRecord.ALL_VISIBLE))
                logger.info("Successfully received sanctions list transaction")

                // Return the sanctions states
                stx.coreTransaction.outRefsOfType<SanctionedEntities>()
            } catch (e: Exception) {
                logger.error("Failed to receive sanctions transaction: ${e.message}")
                emptyList()
            }
        } else {
            logger.info("No sanctions list available from ${otherParty.name}")
            emptyList()
        }
    }
}

@InitiatedBy(GetSanctionsListFlow::class)
class GetSanctionsListFlowAcceptor(val otherPartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        try {
            // Query for sanctions list
            val sanctionsQuery = serviceHub.vaultService.queryBy(SanctionedEntities::class.java)
            val sanctionsList = sanctionsQuery.states.firstOrNull()

            if (sanctionsList != null) {
                // Send positive response
                otherPartySession.send(SanctionsResponse(hasData = true, message = "Sanctions list available"))

                // Get and send the transaction
                val txHash = sanctionsList.ref.txhash
                val transaction = serviceHub.validatedTransactions.getTransaction(txHash)

                if (transaction != null) {
                    subFlow(SendTransactionFlow(otherPartySession, transaction))
                    logger.info("Successfully sent sanctions list transaction")
                } else {
                    logger.error("Transaction not found for hash: $txHash")
                }
            } else {
                // Send negative response
                otherPartySession.send(SanctionsResponse(hasData = false, message = "No sanctions list found"))
                logger.info("No sanctions list found in vault")
            }
        } catch (e: Exception) {
            logger.error("Error in GetSanctionsListFlow.Acceptor: ${e.message}")
            otherPartySession.send(SanctionsResponse(hasData = false, message = "Error: ${e.message}"))
        }
    }
}