package net.corda.samples.solana.dvp.flows

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.node.utilities.solana.TokenManagement
import net.corda.solana.notary.common.SolanaClient
import net.corda.solana.notary.common.SolanaException
import net.corda.solana.notary.common.SolanaTransactionException
import net.corda.solana.sdk.SplToken
import org.slf4j.LoggerFactory
import software.sava.core.accounts.PublicKey
import software.sava.core.accounts.Signer
import software.sava.core.accounts.SolanaAccounts
import software.sava.core.accounts.meta.AccountMeta
import software.sava.core.tx.Instruction
import software.sava.rpc.json.http.response.TransactionError

// TODO this file will be replaced by use of utility classes from other project
/**
 * Manages creation of Solana ATA account on the fly,
 * ATA requests are cached internally in-memory to avoid unnecessary requests to Solana.
 * @param client The Solana RPC client.
 * @param feePayer The signer to pay the transaction fee.
 * @param existingAtaCache The internal results cache, exposed for testing.
 */
class TokenAccountService(
    private val client: SolanaClient,
    private val feePayer: Signer,
    private val tokenManagement: TokenManagement = TokenManagement(client),
    private val existingAtaCache: ExistingAtaCache = BoundedExistingAtaCache(), // configurable for testing
) {
    companion object {
        private val logger = LoggerFactory.getLogger(TokenAccountService::class.java)

        val tokenProgramId : PublicKey =  SplToken.PROGRAM_ID.toSava()
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
     */
    fun createAta(mintAccount: PublicKey, ownerAccount: PublicKey = feePayer.publicKey()): PublicKey {
        if (existingAtaCache.contains(mintAccount, ownerAccount)) {
            // ATA already exists
            return deriveAddress(mintAccount, ownerAccount, tokenProgramId)
        }
        val pda = deriveAddress(mintAccount, ownerAccount, tokenProgramId)
        try {
            tokenManagement.createAta(feePayer, ownerAccount, mintAccount, tokenProgramId)
            logger.info("ATA created successfully, owner=$ownerAccount, mint=$mintAccount, pda=$pda.")
        } catch(e: SolanaException) {
            if (!doesAtaAlreadyExist(e)) {
                logger.error("Exception while creating ATA owner=$ownerAccount, mint=$mintAccount, pda=$pda", e)
                throw e
            }
        }
        existingAtaCache.put(mintAccount, ownerAccount)
        return pda
    }

    fun deriveAddress(mintAccount: PublicKey, ownerAccount: PublicKey = feePayer.publicKey()): PublicKey {
        return deriveAddress(mintAccount, ownerAccount, tokenProgramId)
    }

    // Checks for an expected error when ATA already exists
    private fun doesAtaAlreadyExist(e: SolanaException): Boolean {
        if (e is SolanaTransactionException) {
            val transactionError = e.error
            if (transactionError is TransactionError.AlreadyProcessed) {
                    //TODO
                    return true
                }
            }
        return false
    }

    //TODO move the method to TokenManagement class
    fun TokenManagement.createAta(
        payer: Signer,
        owner: PublicKey,
        mint: PublicKey,
        tokenProgram: PublicKey
    ): PublicKey {
        val solana = SolanaAccounts.MAIN_NET
        val ata = deriveAddress(mint, owner, tokenProgram)
        val createIdempotentIx = Instruction.createInstruction(
            solana.associatedTokenAccountProgram(),
            listOf(
                AccountMeta.createFeePayer(payer.publicKey()),
                AccountMeta.createWrite(ata),
                AccountMeta.createRead(owner),
                AccountMeta.createRead(mint),
                AccountMeta.createRead(solana.systemProgram()),
                AccountMeta.createRead(tokenProgram)
            ),
            byteArrayOf(1)
        )
        client.sendAndConfirm(
            {
                it.createTransaction(listOf(createIdempotentIx))
            },
            payer,
            listOf()
        )
        return ata
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

fun deriveAddress(mintAccount: PublicKey, ownerAccount: PublicKey, programAccount: PublicKey): PublicKey {
    val ataProgram = SolanaAccounts.MAIN_NET.associatedTokenAccountProgram()
    val pda = PublicKey.findProgramAddress(
        listOf(
            ownerAccount.toByteArray(),
            programAccount.toByteArray(),
            mintAccount.toByteArray()
        ),
        ataProgram
    )
    return pda.publicKey()
}