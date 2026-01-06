package net.corda.samples.solana.dvp.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.utilities.ProgressTracker
import net.corda.solana.sdk.instruction.Pubkey

/**
 * Helper flow to create Solana Associated Token Account (ATA) for a given Token Mint and Node's wallet address.
 * ATA is automatically created (if needed) by [SharesDvP] flow for the seller side, hence this flow is used effectively for a buyer.
 */
@StartableByRPC
class CreateAtaFlow : FlowLogic<String>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): String {
        val config = serviceHub.getAppContext().config
        val solanaTokenMint = Pubkey.fromBase58(config.getString("solanaTokenMint"))
        val solanaService = serviceHub.cordaService(SolanaService::class.java)
        return solanaService.createAta(solanaTokenMint).base58()
    }
}
