package net.corda.samples.solana.dvp.flows

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.lmax.solana4j.Solana
import com.lmax.solana4j.api.PublicKey
import com.lmax.solana4j.client.jsonrpc.SolanaJsonRpcClient
import com.lmax.solana4j.programs.AssociatedTokenProgram
import net.corda.solana.notary.common.Signer
import net.corda.solana.notary.common.rpc.DefaultRpcParams
import net.corda.solana.notary.common.rpc.SolanaTransactionException
import net.corda.solana.notary.common.rpc.sendAndConfirm
import net.corda.solana.sdk.SplToken
import net.corda.solana.sdk.instruction.Pubkey
import org.slf4j.LoggerFactory

// TODO this file will be replaced by use of utility classes from other project
/**
 * Manages creation of Solana ATA account on the fly,
 * ATA requests are cached internally in-memory to avoid unnecessary requests to Solana.
 * @param client The Solana RPC client.
 * @param feePayer The signer to pay the transaction fee.
 * @param existingAtaCache The internal results cache, exposed for testing.
 */
class TokenAccountService(
    private val client: SolanaJsonRpcClient,
    private val feePayer: Signer,
    private val existingAtaCache: ExistingAtaCache = BoundedExistingAtaCache(), // configurable for testing
) {
    companion object {
        private val logger = LoggerFactory.getLogger(TokenAccountService::class.java)

        // preflight required to avoid running transaction which will fail on chain
        private val rpcParams = DefaultRpcParams(globalCommitmentLevelLmax, false)
    }

    /**
     * Optimistically attempts to create an associated token account (ATA) for the given SPL token [mintAccount],
     * [ownerAccount] and Token2022. The method is idempotent and may be rerun in case a flow restart.
     *
     * The transaction is built and signed using this service's fee payer and submitted with preflight
     * checks enabled. If submission fails due to a stale blockhash, the creation is retried with a new blockhash.
     *
     * @param mintAccount The SPL token mint for which the associated token account is created.
     * @param ownerAccount The owner of the associated token account, if omitted it defaults to a fee payer account
     *
     * @throws net.corda.solana.notary.common.rpc.SolanaException if the transaction cannot be constructed
     *         or is too large.
     * @throws com.lmax.solana4j.client.jsonrpc.SolanaJsonRpcClientException if the underlying RPC calls fail.
     */
    fun createAta(mintAccount: PublicKey, ownerAccount: PublicKey = feePayer.account): PublicKey {
        if (existingAtaCache.contains(mintAccount, ownerAccount)) {
            val pda = AssociatedTokenProgram.deriveAddress(ownerAccount, tokenProgramId, mintAccount)
            return pda.address() // ATA already exists
        }
        val pda = AssociatedTokenProgram.deriveAddress(ownerAccount, tokenProgramId, mintAccount)
        val instruction = AssociatedTokenProgram.createAssociatedTokenAccount(
            pda,
            mintAccount,
            ownerAccount,
            feePayer.account,
            tokenProgramId,
            false,
        )
        try {
            val result = client.sendAndConfirm(
                { txBuilder ->
                    txBuilder.append(instruction)
                },
                feePayer,
                emptyList(),
                rpcParams
            )
            logger.info(
                "ATA created successfully, slot=${result.slot}, owner=$ownerAccount, mint=$mintAccount, pda=$pda."
            )
        } catch (e: SolanaTransactionException) {
            if (!doesAtaAlreadyExist(e)) {
                logger.error("Exception while creating ATA owner=$ownerAccount, mint=$mintAccount, pda=$pda", e)
                throw e
            }
        }
        existingAtaCache.put(mintAccount, ownerAccount)
        return pda.address()
    }

    fun deriveAddress(mintAccount: PublicKey, ownerAccount: PublicKey = feePayer.account): PublicKey {
        val pda = AssociatedTokenProgram.deriveAddress(ownerAccount, tokenProgramId, mintAccount)
        return pda.address()
    }

    // Checks for an expected error when ATA already exists
    private fun doesAtaAlreadyExist(e: SolanaTransactionException): Boolean {
        val errors = e.error
        if (errors is Map<*, *>) {
            val errorEntries = errors["InstructionError"]
            if (errorEntries != null && errorEntries is List<*> && errorEntries.contains("IllegalOwner")) {
                return true
            }
        }
        return false
    }
}

/**
 * Holds pairs of mint account and owner account that represents a relevant PDA.
 */
interface ExistingAtaCache {
    fun put(mintAccount: PublicKey, ownerAccount: PublicKey)

    fun contains(mintAccount: PublicKey, ownerAccount: PublicKey): Boolean
}

class BoundedExistingAtaCache : ExistingAtaCache {
    private val cache: Cache<Pair<PublicKey, PublicKey>, Unit> = Caffeine
        .newBuilder()
        .maximumSize(10_000)
        .build()

    override fun put(mintAccount: PublicKey, ownerAccount: PublicKey) {
        cache.put(mintAccount to ownerAccount, Unit)
    }

    override fun contains(mintAccount: PublicKey, ownerAccount: PublicKey): Boolean {
        return cache.getIfPresent(mintAccount to ownerAccount) != null
    }
}

val globalCommitmentLevelLmax = com.lmax.solana4j.client.api.Commitment.CONFIRMED
val tokenProgramId = SplToken.PROGRAM_ID.toPublicKey()
fun Pubkey.toPublicKey(): PublicKey = Solana.account(bytes)