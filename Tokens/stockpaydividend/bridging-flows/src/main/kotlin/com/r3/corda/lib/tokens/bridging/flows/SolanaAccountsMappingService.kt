package com.r3.corda.lib.tokens.bridging.flows

import net.corda.core.identity.CordaX500Name
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.solana.sdk.instruction.Pubkey
import java.util.UUID


@CordaService
class SolanaAccountsMappingService(appServiceHub: AppServiceHub) : SingletonSerializeAsToken() {
    var participants: Map<CordaX500Name, Pubkey>
    var mints: Map<UUID, Pubkey>
    var mintAuthorities: Map<UUID, Pubkey>

    init {
        val cfg = appServiceHub.getAppContext().config
        participants = try {
            (cfg.get("participants") as? Map<String, String>)?.map { (k, v) ->
                CordaX500Name.parse(k) to Pubkey.fromBase58(
                    v
                )
            }?.toMap()
                ?: emptyMap()
        } catch (_: Exception) {
            emptyMap() //TODO here and other occurrences, for now ignore misconfiguration as the service is used by Notary in the mock network
        }
        mints = try {
            (cfg.get("mints") as? Map<String, String>)?.map { (k, v) -> UUID.fromString(k) to Pubkey.fromBase58(v) }
                ?.toMap()
                ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
        mintAuthorities = try {
            (cfg.get("mintAuthorities") as? Map<String, String>)?.map { (k, v) ->
                UUID.fromString(k) to Pubkey.fromBase58(
                    v
                )
            }?.toMap()
                ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
