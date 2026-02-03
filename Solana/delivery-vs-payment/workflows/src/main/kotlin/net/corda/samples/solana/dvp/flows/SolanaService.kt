package net.corda.samples.solana.dvp.flows

import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.notary.solana.toPubkey
import net.corda.solana.notary.common.FileSigner
import net.corda.solana.notary.common.SolanaClient
import net.corda.solana.sdk.instruction.Pubkey
import software.sava.core.accounts.PublicKey
import software.sava.core.accounts.token.Mint
import software.sava.rpc.json.http.client.SolanaRpcClient
import software.sava.rpc.json.http.request.Commitment
import software.sava.rpc.json.http.response.AccountInfo
import java.net.URI
import java.nio.file.Paths


@CordaService
class SolanaService(appServiceHub: AppServiceHub) : SingletonSerializeAsToken() {
    private val solanaClient: SolanaClient
    private val accountService: TokenAccountService
    val mintAuthority: Pubkey
    init {
        val config = appServiceHub.getAppContext().config
        val rpcUrl = URI.create(config.getString("solanaRpcUrl"))
        val websocketUrl = URI.create(config.getString("solanaWsUrl"))
        solanaClient = SolanaClient(rpcUrl, websocketUrl, Commitment.CONFIRMED)
        solanaClient.start()
        appServiceHub.registerUnloadHandler { solanaClient.close() }

        val payer = FileSigner.read( Paths.get(config.getString("solanaWalletFile")))
        mintAuthority = payer.publicKey().toPubkey()
        accountService = TokenAccountService(solanaClient, payer)
    }

    fun getAccountInfo(account: Pubkey): AccountInfo<ByteArray> {
        val accountInfo = solanaClient.call(SolanaRpcClient::getAccountInfo, account.toSava())
        return accountInfo
    }

    fun getAccountMintDecimals(account: Pubkey): Int {
        val accountInfo = getAccountInfo(account)
        val mint = Mint.read(accountInfo.pubKey(), accountInfo.data)
        return mint.decimals
    }

    fun createAta(mint: PublicKey) : PublicKey = accountService.createAta(mint)

    fun deriveAtaAddress(mint: Pubkey) : PublicKey = accountService.deriveAddress(mint.toSava())
}

fun Pubkey.toSava(): PublicKey = PublicKey.createPubKey(bytes)