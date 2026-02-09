package net.corda.samples.solana.bridging.token

import net.corda.node.utilities.solana.AccountManagement
import net.corda.node.utilities.solana.TokenManagement
import net.corda.solana.notary.common.FileSigner
import net.corda.solana.notary.common.SolanaClient
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

open class DevNetBridgingTokenDriverTest : BridgingTokenDriverTest() {

    override val solanaRpcUrl = "https://api.devnet.solana.com"
    override val solanaWssUrl = "ws://api.devnet.solana.com"
    protected val staticCustodiedKeysDir = "src/integrationTest/resources/custodiedKeys"

    override fun getSolanaNotaryConfig() : Map<String, Any> {
        return mapOf<String, Any>(
            "notary" to mapOf(
                "validating" to false,
                "solana" to mapOf(
                    "rpcUrl" to solanaRpcUrl,
                    "websocketUrl" to solanaWssUrl,
                    "notaryKeypairFile" to "${solanaNotarySigner.file}",
                    "custodiedKeysDir" to "${Path.of(staticCustodiedKeysDir).toAbsolutePath()}",
                    "programWhitelist" to listOf(Token2022.PROGRAM_ID.toSava().toBase58())
                )
            )
        )
    }

    override fun startTestValidator() {
        val notaryKeyPath =
             Paths.get("../../Dev7chG99tLCAny3PNYmBdyhaKEVcZnSTp3p1mKVb5m5.json").toAbsolutePath().toString()
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
            log.info("  Shareholder Token Account cleanup - burn existing $shareholderBalance tokens")
            tokenManagement.burn(
                shareholderWallet,
                tokenMint,
                shareholderWallet.deriveATA(),
                shareholderBalance.toLong()
            )
        }
        val otherShareholderBalance = solanaClient.getSolanaTokenBalance(otherShareholderWallet.deriveATA())
        if (otherShareholderBalance > BigDecimal.ZERO) {
            log.info("  Other Shareholder Token Account cleanup - burn existing $otherShareholderBalance tokens")
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
            waitForAllNodesToFinish = false
        )
    ) {
        log.info("\nStarting bridging test using Solana validator via $solanaRpcUrl...")
        runtimeSetup()
        test()
        log.info("\nBridging test is completed.")
    }
}