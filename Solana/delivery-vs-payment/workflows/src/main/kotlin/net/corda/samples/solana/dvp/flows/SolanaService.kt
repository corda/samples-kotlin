package net.corda.samples.solana.dvp.flows

import com.r3.corda.lib.solana.core.FileSigner
import com.r3.corda.lib.solana.core.SolanaClient
import com.r3.corda.lib.solana.core.tokens.TokenManagement.Companion.getAssociatedTokenAccountAddress
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.solana.Pubkey
import net.corda.notary.solana.toPubkey
import software.sava.core.accounts.PublicKey
import software.sava.core.accounts.Signer
import software.sava.core.accounts.token.Mint
import software.sava.rpc.json.http.client.SolanaRpcClient
import software.sava.rpc.json.http.request.Commitment
import software.sava.rpc.json.http.response.AccountInfo
import java.net.URI
import java.nio.file.Paths


@CordaService
class SolanaService(appServiceHub: AppServiceHub) : SingletonSerializeAsToken() {
    private val solanaClient: SolanaClient
    private val accountService: CachedTokenManagement
    private val wallet: Signer

    init {
        val config = appServiceHub.getAppContext().config
        val rpcUrl = URI.create(config.getString("solanaRpcUrl"))
        val websocketUrl = URI.create(config.getString("solanaWebsocketUrl"))
        solanaClient = SolanaClient(rpcUrl, websocketUrl, Commitment.CONFIRMED)
        solanaClient.start()
        appServiceHub.registerUnloadHandler { solanaClient.close() }

        wallet = FileSigner.read(Paths.get(config.getString("solanaWalletFile")))
        accountService = CachedTokenManagement(solanaClient)
    }

    fun getMyWalletAddress(): Pubkey = wallet.publicKey().toPubkey()

    fun getAccountInfo(account: Pubkey): AccountInfo<ByteArray> {
        val accountInfo = solanaClient.call(SolanaRpcClient::getAccountInfo, account.toPublicKey())
        return accountInfo
    }

    fun getAccountMintDecimals(account: Pubkey): Int {
        val accountInfo = getAccountInfo(account)
        val mint = Mint.read(accountInfo.pubKey(), accountInfo.data)
        return mint.decimals
    }

    fun createAta(mint: PublicKey): PublicKey = accountService.createAssociatedTokenAccount(wallet, mint)

    fun deriveAtaAddress(mint: Pubkey): PublicKey = getAssociatedTokenAccountAddress(mint.toPublicKey(), wallet.publicKey())
}

fun Pubkey.toPublicKey(): PublicKey = PublicKey.createPubKey(bytes)