package net.corda.samples.solana.bridging.token

import com.r3.corda.lib.solana.core.AccountManagement
import com.r3.corda.lib.solana.core.FileSigner
import com.r3.corda.lib.solana.core.SolanaClient
import com.r3.corda.lib.solana.core.tokens.TokenManagement
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.NotarySpec
import org.junit.jupiter.api.Test
import software.sava.core.accounts.PublicKey
import java.math.BigInteger
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths

open class DevNetBridgingTokenDriverTest : BridgingTokenDriverTest() {

    override val solanaRpcUrl = "https://api.devnet.solana.com"
    override val solanaWssUrl = "ws://api.devnet.solana.com"
    override val dvpStablecoinMint = "4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU"
    protected val staticCustodiedKeysDir = "src/integrationTest/resources/custodiedKeys"

    override fun getSolanaNotaryConfig() : Map<String, Any> {
        return mapOf<String, Any>(
            "notary" to mapOf(
                "validating" to false,
                "solana" to mapOf(
                    "rpcUrl" to solanaRpcUrl,
                    "websocketUrl" to solanaWssUrl,
                    "notaryKeypairFile" to "${solanaNotarySigner.file}",
                    "custodiedKeysDir" to "${Path.of(staticCustodiedKeysDir).toAbsolutePath()}"
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
        redemptionWalletForIssuer =
            FileSigner.read(Path.of("$staticCustodiedKeysDir/redemptionWalletForIssuer.json").toAbsolutePath())
        redemptionWalletForCustodian =
            FileSigner.read(Path.of("$staticCustodiedKeysDir/redemptionWalletForCustodian.json").toAbsolutePath())
        mintAuthoritySigner = FileSigner.read(Path.of("$staticCustodiedKeysDir/mintAuthoritySigner.json").toAbsolutePath())
        val otherKeysDir = "src/integrationTest/resources/other"
        issuerWallet = FileSigner.read(Path.of("$otherKeysDir/issuerWallet.json").toAbsolutePath())
        custodianWallet = FileSigner.read(Path.of("$otherKeysDir/custodianWallet.json").toAbsolutePath())

        log.info("\nSolana wallet account addresses:")
        log.info("  Issuer: ${issuerWallet.publicKey().toBase58()}")
        log.info("  Custodian: ${custodianWallet.publicKey().toBase58()}")
        log.info("  Bridge Authority: ${bridgeAuthoritySigner.publicKey().toBase58()}")
        log.info("  Mint Authority: ${mintAuthoritySigner.publicKey().toBase58()}")
        log.info("  Redemption on behalf of Issuer: ${redemptionWalletForIssuer.publicKey().toBase58()}")
        log.info(
            "  Redemption on behalf of Custodian: ${
                redemptionWalletForCustodian.publicKey().toBase58()
            }"
        )

        tokenMint = PublicKey.fromBase58Encoded("FuWNGKEmJKweaon2cnn8bwB8gogYkdDexeUZCWwvb7t5")
        tokenMint2 = PublicKey.fromBase58Encoded("AhZNYSxWTVCbXMcZNJv27e7G38G7auFaqx4zCBBfWikc")

        log.info("  Creating Solana Token.")
        log.info("  Issuer Token Account: ${issuerWallet.deriveATA().toBase58()}")
        log.info("  Custodian Token Account: ${custodianWallet.deriveATA().toBase58()}")
        log.info("  Issuer Redemption Token Account: ${redemptionWalletForIssuer.deriveATA().toBase58()}")
        log.info(
            "  Custodian Redemption Token Account: ${
                redemptionWalletForCustodian.deriveATA().toBase58()
            }"
        )

        // Guard against the ATA not existing yet (e.g. first run with a new mint).
        // getTokenAccountBalance throws JsonRpcException for non-existent accounts,
        // so check via getAccountInfo first – the same pattern used in solanaBalance().
        if (solanaClient.getAccountInfo(issuerWallet.deriveATA()) != null) {
            val issuerBalance = solanaClient.getSolanaTokenBalance(issuerWallet.deriveATA())
            if (issuerBalance > BigInteger.ZERO) {
                log.info("  Issuer Token Account cleanup - burn existing $issuerBalance tokens")
                tokenManagement.burn(
                    issuerWallet,
                    tokenMint,
                    issuerWallet.deriveATA(),
                    issuerBalance.toLong()
                )
            }
        }
        if (solanaClient.getAccountInfo(custodianWallet.deriveATA()) != null) {
            val custodianBalance = solanaClient.getSolanaTokenBalance(custodianWallet.deriveATA())
            if (custodianBalance > BigInteger.ZERO) {
                log.info("  Custodian Token Account cleanup - burn existing $custodianBalance tokens")
                tokenManagement.burn(
                    custodianWallet,
                    tokenMint,
                    custodianWallet.deriveATA(),
                    custodianBalance.toLong()
                )
            }
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