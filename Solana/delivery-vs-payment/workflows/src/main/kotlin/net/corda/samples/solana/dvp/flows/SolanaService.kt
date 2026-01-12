package net.corda.samples.solana.dvp.flows

import com.lmax.solana4j.api.PublicKey
import com.lmax.solana4j.client.jsonrpc.SolanaJsonRpcClient
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.node.utilities.solana.SolanaClient
import net.corda.notary.solana.toSava
import net.corda.solana.notary.common.Signer
import net.corda.solana.sdk.instruction.Pubkey
import software.sava.core.accounts.token.Mint
import software.sava.rpc.json.http.client.SolanaRpcClient
import software.sava.rpc.json.http.response.AccountInfo
import java.net.URI
import java.net.http.HttpClient
import kotlin.io.path.Path

@CordaService
class SolanaService(appServiceHub: AppServiceHub) : SingletonSerializeAsToken() {
    private val solanaClient: SolanaClient
    private val accountService: TokenAccountService
    private val rpcClient: SolanaJsonRpcClient

    init {
        val config = appServiceHub.getAppContext().config
        val wsUrl = config.getString("solanaWsUrl")
        val rpcUrl = config.getString("solanaRpcUrl")
        solanaClient = SolanaClient(URI.create(rpcUrl), URI.create(wsUrl))
        solanaClient.start()
        appServiceHub.registerUnloadHandler { solanaClient.close() }

        rpcClient = SolanaJsonRpcClient(HttpClient.newHttpClient(), rpcUrl)
        accountService = TokenAccountService(rpcClient,  Signer.fromFile(Path(config.getString("solanaWalletFile"))))
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

    fun createAta(mint: Pubkey) : PublicKey = accountService.createAta(mint.toPublicKey())

    fun deriveAtaAddress(mint: Pubkey) : PublicKey = accountService.deriveAddress(mint.toPublicKey())

}