package net.corda.samples.solana.bridging.token

import net.corda.node.utilities.solana.AccountManagement
import net.corda.node.utilities.solana.FileSigner
import net.corda.node.utilities.solana.SolanaClient
import net.corda.node.utilities.solana.TokenManagement
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths

class DevNetBridgingTokenDriverTest : BridgingTokenDriverTest() {

    //TODO replace with devnet addresses
    private val solanaRpcUrl = URI.create("http://127.0.0.1:8899")
    private val solanaWssUrl = URI.create("ws://127.0.0.1:8900")

    override fun startTestValidator() {
        val notaryKeyPath =
            Paths.get("../../Dev7chG99tLCAny3PNYmBdyhaKEVcZnSTp3p1mKVb5m5.json").toAbsolutePath().toString()
        solanaNotarySigner = FileSigner.read(Path.of(notaryKeyPath))
        solanaClient = SolanaClient(solanaRpcUrl, solanaWssUrl).apply { start() }
        tokenManagement = TokenManagement(solanaClient)
        accountManagement = AccountManagement(solanaClient)
        accountManagement.airdropSol(solanaNotarySigner.publicKey(), 10)
    }

    override fun stopTestValidator() = Unit
}