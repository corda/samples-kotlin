package net.corda.samples.solana.dvp.flows

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
    private var solanaClient: SolanaClient ?= null

    init {
        val config = appServiceHub.getAppContext().config
        val wsUrl = config.getString("solanaWsUrl")
        val rpcUrl = config.getString("solanaRpcUrl")
        solanaClient = SolanaClient(URI.create(rpcUrl), URI.create(wsUrl))
        solanaClient!!.start()
        appServiceHub.registerUnloadHandler { solanaClient!!.close() }
    }

    fun getAccountInfo(account: Pubkey): AccountInfo<ByteArray> {
        checkNotNull(solanaClient)
        val accountInfo = solanaClient!!.call(SolanaRpcClient::getAccountInfo, account.toSava())
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