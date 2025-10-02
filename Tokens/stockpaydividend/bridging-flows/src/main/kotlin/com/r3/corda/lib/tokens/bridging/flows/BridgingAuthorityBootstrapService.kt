package com.r3.corda.lib.tokens.bridging.flows

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.debug
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.Executors

@CordaService
class BridgingAuthorityBootstrapService(appServiceHub: AppServiceHub) : SingletonSerializeAsToken() {
    private val holdingIdentityPartyAndCertificate: PartyAndCertificate
    private val bridgeAuthorityParty = appServiceHub.myInfo.legalIdentities.first()
    private val logger = LoggerFactory.getLogger(BridgingAuthorityBootstrapService::class.java)

    private val executor = Executors.newSingleThreadExecutor()

    init {
        val cfg = appServiceHub.getAppContext().config
        val holdingIdentityLabel = UUID.fromString(cfg.getString("holdingIdentityLabel"))
        val holdingIdentityPublicKey = appServiceHub
            .identityService
            .publicKeysForExternalId(holdingIdentityLabel)
            .singleOrNull()
        holdingIdentityPartyAndCertificate = if (holdingIdentityPublicKey == null) {
            // Generate a new key pair and self-signed certificate for the holding identity
            appServiceHub.keyManagementService.freshKeyAndCert(
                identity = requireNotNull(appServiceHub.identityService.certificateFromKey(bridgeAuthorityParty.owningKey)) {
                    "Could not find certificate for key ${bridgeAuthorityParty.owningKey}"
                },
                revocationEnabled = false,
                externalId = holdingIdentityLabel
            )
        } else {
            // Reuse the existing key pair and certificate for the holding identity
            checkNotNull(appServiceHub.identityService.certificateFromKey(holdingIdentityPublicKey)) {
                "Could not find certificate for key $holdingIdentityPublicKey"
            }
        }

        appServiceHub.registerUnloadHandler { onStop() }
        onStartup(appServiceHub)
    }

    private fun onStop() {
        executor.shutdown()
    }

    private fun onStartup(appServiceHub: AppServiceHub) {
        //Retrieve states from receiver
        val receivedStates = appServiceHub.vaultService.queryBy(FungibleToken::class.java).states


        callFlow(receivedStates, appServiceHub)
        addVaultListener(appServiceHub)
    }

    private fun addVaultListener(appServiceHub: AppServiceHub) {
        appServiceHub.vaultService.trackBy(FungibleToken::class.java).updates.subscribe {
            val producedStockStates = it.produced
            callFlow(producedStockStates, appServiceHub)
        }
    }

    private fun callFlow(fungibleTokens: Collection<StateAndRef<FungibleToken>>, appServiceHub: AppServiceHub) {
        fungibleTokens.forEach { token ->
            if (bridgeAuthorityParty !in previousOwnersOf(appServiceHub, token)) {
                logger.debug { "Starting flow to bridge ${token.state.data.amount} to Solana" }
                executor.submit {
                    appServiceHub.startFlow(
                        BridgeFungibleTokenFlow(
                            holdingIdentityPartyAndCertificate.party,
                            emptyList(),
                            token,
                            bridgeAuthorityParty
                        )
                    )
                }
            }
        }
    }
}
