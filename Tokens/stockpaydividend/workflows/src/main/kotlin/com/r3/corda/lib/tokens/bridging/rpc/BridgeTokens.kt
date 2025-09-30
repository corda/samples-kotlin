package com.r3.corda.lib.tokens.bridging.rpc

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.bridging.BridgeFungibleTokensFlow
import com.r3.corda.lib.tokens.bridging.contracts.BridgingContract
import com.r3.corda.lib.tokens.bridging.states.BridgedAssetLockState
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.flows.move.MoveTokensFlowHandler
import com.r3.corda.lib.tokens.workflows.utilities.sessionsForParties
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.solana.sdk.instruction.Pubkey
import java.util.*

@InitiatingFlow
@StartableByRPC
class BridgeStock(
    val symbol: String,
    val quantity: Long,
    val bridgeAuthority: Party,
    val destination: Pubkey,
    val mint: Pubkey,
    val mintAuthority: Pubkey,
    val holdingIdentityLabel: UUID
) : FlowLogic<String>() {

    constructor(
        symbol: String,
        quantity: Long,
        bridgeAuthority: Party,
        destination: String,
        mint: String,
        mintAuthority: String,
        holdingIdentityLabel: String
    ) : this(
        symbol,
        quantity,
        bridgeAuthority,
        Pubkey.fromBase58(destination),
        Pubkey.fromBase58(mint),
        Pubkey.fromBase58(mintAuthority),
        UUID.fromString(holdingIdentityLabel)
    )

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): String {
        val additionalOutput: ContractState = BridgedAssetLockState(listOf(ourIdentity))
        val additionalCommand = BridgingContract.BridgingCommand.BridgeToSolana(
            destination,
            bridgeAuthority
        )
        //Use built-in flow for move tokens to the recipient
        val stx = subFlow(
            BridgeFungibleTokens(
                emptyList(),
                additionalOutput,
                additionalCommand,
                destination,
                mint,
                mintAuthority,
                holdingIdentityLabel
            )
        )

        return ("\nBridged " + quantity + " " + symbol + " stocks to "
                + ourIdentity.name.organisation + ".\nTransaction ID: " + stx.id)
    }
}

/**
 * Initiating flow used to bridge amounts of tokens of the same party.
 *
 * Call this for one [TokenType] at a time.
 *
 * @param observers optional observing parties to which the transaction will be broadcast
 */
@StartableByService
@StartableByRPC
@InitiatingFlow
class BridgeFungibleTokens
@JvmOverloads
constructor(
    val observers: List<Party> = emptyList(),
    val additionalOutput: ContractState,
    val additionalCommand: BridgingContract.BridgingCommand,
    val destination: Pubkey,
    val mint: Pubkey,
    val mintAuthority: Pubkey,
    val holdingIdentityLabel: UUID
) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val holdingIdentityPublicKey = serviceHub
            .identityService
            .publicKeysForExternalId(holdingIdentityLabel)
            .singleOrNull()
        val holdingIdentityPartyAndCertificate = if (holdingIdentityPublicKey == null) {
            serviceHub.keyManagementService.freshKeyAndCert(
                identity = serviceHub.identityService.certificateFromKey(ourIdentity.owningKey)!!,
                revocationEnabled = false,
                externalId = holdingIdentityLabel
            )
        } else {
            serviceHub.identityService.certificateFromKey(holdingIdentityPublicKey)
                ?: throw IllegalStateException("Could not find certificate for key $holdingIdentityPublicKey")
        }

        val participants = listOf(holdingIdentityPartyAndCertificate.party)
        val observerSessions = sessionsForParties(observers)
        val participantSessions = sessionsForParties(participants)

        //TODO add list of StetRef to bridge in flow parameters
        val criteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED)

        serviceHub.vaultService.queryBy(FungibleToken::class.java, criteria)

        val token: StateAndRef<FungibleToken> = serviceHub.vaultService
            .queryBy(FungibleToken::class.java, criteria)
            .states.first()

        return subFlow(
            BridgeFungibleTokensFlow(
                participantSessions = participantSessions,
                observerSessions = observerSessions,
                token = token,
                additionalOutput = additionalOutput,
                additionalCommand = additionalCommand,
                destination = destination,
                mint = mint,
                mintAuthority = mintAuthority,
                holdingIdentityPartyAndCertificate.party,
            )
        )
    }
}

/**
 * Responder flow for [BridgeFungibleTokens].
 */
@InitiatedBy(BridgeFungibleTokens::class)
class BridgeFungibleTokensHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(MoveTokensFlowHandler(otherSession))
}
