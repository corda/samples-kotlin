package net.corda.samples.solana.bridging.token

import com.r3.corda.lib.solana.bridging.token.flows.SavaFactory.toPublicKey
import net.corda.node.utilities.solana.AccountManagement
import net.corda.node.utilities.solana.FileSigner
import net.corda.node.utilities.solana.SolanaClient
import net.corda.node.utilities.solana.TokenManagement
import net.corda.solana.sdk.Token2022
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.NotarySpec
import org.junit.jupiter.api.Test
import software.sava.core.accounts.PublicKey
import java.math.BigDecimal
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths

// Shareholder: https://solscan.io/account/3Wuk6fKtqCzMppikC1S58vK3J5ZbqnbcZXkDhJUHCom7?cluster=devnet#portfolio
// Other Shareholder https://solscan.io/account/AoDHzQwk7s6crxMcC1nptRVHAhd1LASEWbQmAQjCeBKj?cluster=devnet#portfolio
class DevNetBridgingTokenDriverTest : BridgingTokenDriverTest() {

    val testMode = false

    override val solanaRpcUrl = "https://api.devnet.solana.com"
    override val solanaWssUrl = "ws://api.devnet.solana.com"
    val staticCustodiedKeysDir = "src/integrationTest/resources/custodiedKeys"

    override fun getSolanaNotaryConfig() : Map<String, Any> {
        return mapOf<String, Any>(
            "notary" to mapOf(
                "validating" to false,
                "solana" to mapOf(
                    "rpcUrl" to solanaRpcUrl,
                    "websocketUrl" to solanaWssUrl,
                    "notaryKeypairFile" to "${solanaNotarySigner.file}",
                    "custodiedKeysDir" to "${Path.of(staticCustodiedKeysDir).toAbsolutePath()}",
                    "programWhitelist" to listOf(Token2022.PROGRAM_ID.toPublicKey().toBase58())
                )
            )
        )
    }

    override fun startTestValidator() {
        val notaryKeyPath =
            Paths.get("../../../../enterprise/solana-devnet/network-0-notary-1-key.json").toAbsolutePath().toString()
        //TODO use this:
        //     Paths.get("../../Dev7chG99tLCAny3PNYmBdyhaKEVcZnSTp3p1mKVb5m5.json").toAbsolutePath().toString()
        solanaNotarySigner = FileSigner.read(Path.of(notaryKeyPath))
        solanaClient = SolanaClient(URI.create(solanaRpcUrl), URI.create(solanaWssUrl)).apply { start() }
        tokenManagement = TokenManagement(solanaClient)
        accountManagement = AccountManagement(solanaClient)
    }

    override fun setupAccounts() {
        bridgeAuthoritySigner = FileSigner.read(Path.of("$staticCustodiedKeysDir/bridgeAuthority.json").toAbsolutePath())
        redemptionWalletForShareholder =
            FileSigner.read(Path.of("$staticCustodiedKeysDir/redemptionWalletForShareholder.json").toAbsolutePath())
        redemptionWalletForOtherShareholder =
            FileSigner.read(Path.of("$staticCustodiedKeysDir/redemptionWalletForOtherShareholder.json").toAbsolutePath())
        mintAuthoritySigner = FileSigner.read(Path.of("$staticCustodiedKeysDir/mintAuthoritySigner.json").toAbsolutePath())
        val otherKeysDir = "src/integrationTest/resources/other"
        shareholderWallet = FileSigner.read(Path.of("$otherKeysDir/shareholderWallet.json").toAbsolutePath())
        otherShareholderWallet = FileSigner.read(Path.of("$otherKeysDir/otherShareholderWallet.json").toAbsolutePath())

        log.info("\nSolana wallet account addresses:")
        log.info("  Shareholder: ${shareholderWallet.publicKey().toBase58()}")
        log.info("  Other Shareholder: ${otherShareholderWallet.publicKey().toBase58()}")
        log.info("  Bridge Authority: ${bridgeAuthoritySigner.publicKey().toBase58()}")
        log.info("  Mint Authority: ${mintAuthoritySigner.publicKey().toBase58()}")
        log.info("  Redemption on behalf of Shareholder: ${redemptionWalletForShareholder.publicKey().toBase58()}")
        log.info(
            "  Redemption on behalf of Other Shareholder: ${
                redemptionWalletForOtherShareholder.publicKey().toBase58()
            }"
        )

        tokenMint = PublicKey.fromBase58Encoded("GMWmvcYWWWCv1V7WW8pwXeFej97od3SPBvSUR6wsAhSC")
        tokenMint2 = PublicKey.fromBase58Encoded("AhZNYSxWTVCbXMcZNJv27e7G38G7auFaqx4zCBBfWikc")

        log.info("  Creating Solana Token.")
        log.info("  Shareholder Token Account: ${shareholderWallet.deriveATA().toBase58()}")
        log.info("  Other Shareholder Token Account: ${otherShareholderWallet.deriveATA().toBase58()}")
        log.info("  Shareholder Redemption Token Account: ${redemptionWalletForShareholder.deriveATA().toBase58()}")
        log.info(
            "  Other Shareholder Redemption Token Account: ${
                redemptionWalletForOtherShareholder.deriveATA().toBase58()
            }"
        )

        val shareholderBalance = solanaClient.getSolanaTokenBalance(shareholderWallet.deriveATA())
        if (shareholderBalance > BigDecimal.ZERO) {
            tokenManagement.burn(
                shareholderWallet,
                tokenMint,
                shareholderWallet.deriveATA(),
                shareholderBalance.toLong()
            )
        }
        val otherShareholderBalance = solanaClient.getSolanaTokenBalance(otherShareholderWallet.deriveATA())
        if (otherShareholderBalance > BigDecimal.ZERO) {
            tokenManagement.burn(
                otherShareholderWallet,
                tokenMint,
                otherShareholderWallet.deriveATA(),
                otherShareholderBalance.toLong()
            )
        }
    }

    override fun stopTestValidator() = Unit

    @Test
    override fun `briding token test`() = driver(
        DriverParameters(
            isDebug = false,
            inMemoryDB = false,
            startNodesInProcess = false,
            cordappsForAllNodes = cordappsForAllNodes,
            networkParameters = testNetworkParameters(minimumPlatformVersion = 160).copy(notaries = emptyList()),
            notarySpecs = listOf(
                NotarySpec(generalNotaryName, validating = false, startInProcess = false),
                NotarySpec(solanaNotaryName, getSolanaNotaryConfig(), startInProcess = false)
            ),
            waitForAllNodesToFinish = !testMode
        )
    ) {
        log.info("\nStarting bridging test using Solana validator via $solanaRpcUrl...")
        runtimeSetup()
        if (testMode)  {
            test()
            log.info("\nBridging test is completed.")
        } else {
            log.info("\nBridging deployment is running, shut down Corda nodes externally to exit...")
        }
    }
}