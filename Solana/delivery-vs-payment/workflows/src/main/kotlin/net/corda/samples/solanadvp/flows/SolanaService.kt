package net.corda.samples.solanadvp.flows

import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.node.utilities.solana.SolanaClient
import net.corda.notary.solana.toSava
import net.corda.solana.sdk.instruction.Pubkey
import software.sava.rpc.json.http.client.SolanaRpcClient
import software.sava.rpc.json.http.response.AccountInfo
import java.net.URI

@CordaService
class SolanaService(appServiceHub: AppServiceHub): SingletonSerializeAsToken() {
    private val rpcUrl = "http://127.0.0.1:8899" // TODO read from config
    private val wslUrl = "ws://127.0.0.1:8900" // TODO read from config
    private val solanaClient = SolanaClient(URI.create(rpcUrl), URI.create(wslUrl))

    init {
        solanaClient.start()
        appServiceHub.registerUnloadHandler { solanaClient.close() }
    }

    fun getAccountInfo(account: Pubkey): AccountInfo<ByteArray> {
        val accountInfo = solanaClient.call(SolanaRpcClient::getAccountInfo, account.toSava())
        return accountInfo
    }

    fun getAccountMintDecimals(account: Pubkey): Int {
        val accountInfo = getAccountInfo(account)
        val mintDecimals = parseMintDecimals(accountInfo.data)
        return mintDecimals
    }

    private fun parseMintDecimals(mintAccountData: ByteArray): Int {
        require(mintAccountData.size >= 45) { "Mint data too small: ${mintAccountData.size}" }
        return mintAccountData[44].toInt() and 0xFF
    }
}