package net.corda.samples.solana.dvp.flows

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.r3.corda.lib.solana.core.SolanaClient
import com.r3.corda.lib.solana.core.SolanaException
import com.r3.corda.lib.solana.core.tokens.TokenManagement
import com.r3.corda.lib.solana.core.tokens.TokenManagement.Companion.getAssociatedTokenAccountAddress
import org.slf4j.LoggerFactory
import software.sava.core.accounts.PublicKey
import software.sava.core.accounts.Signer

/**
 * Manages creation of Solana ATA account on the fly,
 * ATA requests are cached internally in-memory to avoid unnecessary requests to Solana.
 * Uses [TokenManagement] with addition of a cache.
 * @param client The Solana RPC client.
 * @param existingAtaCache The internal results cache, exposed for testing.
 */
class CachedTokenManagement(
    private val client: SolanaClient,
    private val tokenManagement: TokenManagement = TokenManagement(client),
    private val existingAtaCache: ExistingAtaCache = BoundedExistingAtaCache(), // configurable for testing
) {
    companion object {
        private val logger = LoggerFactory.getLogger(CachedTokenManagement::class.java)
    }

    /**
     * Optimistically attempts to create an associated token account (ATA) for the given SPL token [tokenMint],
     * [accountOwner] and Token2022. The method is idempotent and may be rerun in case a flow restart.
     *
     * The transaction is built and signed using this service's fee payer.
     *
     * @param payer The fee payer for the transaction
     * @param tokenMint The SPL token mint for which the associated token account is created.
     * @param accountOwner The owner of the associated token account, if omitted it defaults to a fee payer
     */
    fun createAssociatedTokenAccount(
        payer: Signer,
        tokenMint: PublicKey,
        accountOwner: PublicKey = payer.publicKey()
    ): PublicKey {
        if (existingAtaCache.contains(tokenMint, accountOwner)) {
            // ATA already exists
            return getAssociatedTokenAccountAddress(tokenMint, accountOwner)
        }
        val pda = getAssociatedTokenAccountAddress(tokenMint, accountOwner)
        try {
            tokenManagement.createAssociatedTokenAccount(payer, tokenMint)
            logger.info("ATA created successfully, owner=$accountOwner, mint=$tokenMint, pda=$pda.")
        } catch (e: SolanaException) {
            logger.error("Exception while creating ATA owner=$accountOwner, mint=$tokenMint, pda=$pda", e)
            throw e
        }
        existingAtaCache.put(tokenMint, accountOwner)
        return pda
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