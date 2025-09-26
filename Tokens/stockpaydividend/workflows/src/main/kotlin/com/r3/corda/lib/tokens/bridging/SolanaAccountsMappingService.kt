package com.r3.corda.lib.tokens.bridging

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

    // TODO quiet failover as this service is used in MockNetwork by nodes without a config for now
    // will be fail fast after separating flows to specific CordApps and adding Birding Authority which will have exclusive flows and this service
    init {
        val cfg = appServiceHub.getAppContext().config
        participants = try {
            (cfg.get("participants") as? Map<String, String>)?.map { (k, v) ->
                CordaX500Name.parse(k) to Pubkey.fromBase58(
                    v
                )
            }?.toMap() ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
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