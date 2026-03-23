package net.corda.samples.solana.bridge.authority

import com.r3.corda.lib.solana.core.AccountManagement
import com.r3.corda.lib.solana.core.FileSigner
import com.r3.corda.lib.solana.core.SolanaClient
import com.r3.corda.lib.solana.core.tokens.TokenManagement
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.NotarySpec
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import software.sava.core.accounts.PublicKey
import java.math.BigInteger
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths

open class DevNetBridgeAuthorityTest : TestBase() {
    // A directory for Notary to load Corda participant key pairs for signing Solana transactions,
    // the keys are located in a different directory than Corda Notary Program key pair
    protected val custodiedKeysDir = "../integration-tests/src/test/resources/custodiedKeys"
    protected lateinit var solanaNotarySigner: FileSigner

    fun getSolanaNotaryConfig(solanaNotarySigner: FileSigner) = mapOf<String, Any>(
            "notary" to mapOf(
                "validating" to false,
                "solana" to mapOf(
                    "rpcUrl" to "$solanaRpcUrl",
                    "websocketUrl" to "$solanaWebsocketUrl",
                    "notaryKeypairFile" to "${solanaNotarySigner.file}",
                    "custodiedKeysDir" to "${Path.of(custodiedKeysDir).toAbsolutePath()}"
                )
            )
        )

    @BeforeEach
    fun setup() {
        solanaRpcUrl = URI.create("https://api.devnet.solana.com")
        solanaWebsocketUrl = URI.create("ws://api.devnet.solana.com")
        val notaryKeyPath = Paths.get("../../devnet-sample-notary-keypair.json").toAbsolutePath()
        solanaNotarySigner = FileSigner.read(notaryKeyPath)
        solanaClient = SolanaClient(solanaRpcUrl, solanaWebsocketUrl).apply { start() }
        tokenManagement = TokenManagement(solanaClient)
        accountManagement = AccountManagement(solanaClient)

        bridgeAuthoritySigner =
            FileSigner.read(Path.of("$custodiedKeysDir/bridgeAuthority.json").toAbsolutePath())
        redemptionWalletForShareholder =
            FileSigner.read(Path.of("$custodiedKeysDir/redemptionWalletForShareholder.json").toAbsolutePath())
        redemptionWalletForOtherShareholder =
            FileSigner.read(
                Path.of("$custodiedKeysDir/redemptionWalletForOtherShareholder.json").toAbsolutePath()
            )
        mintAuthoritySigner =
            FileSigner.read(Path.of("$custodiedKeysDir/mintAuthoritySigner.json").toAbsolutePath())
        val otherKeysDir = "../integration-tests/src/test/resources/other"
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

        tokenMint = PublicKey.fromBase58Encoded("GB1b7YV6TYHejA7abGatmmW1BEe2xaxPXwvoAuirFKd1")
        tokenMint2 = PublicKey.fromBase58Encoded("8EcKmuk3wYBH3rRQxDjZvcUZf7iBBvps2eUpmNGEYcwi")

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
        if (shareholderBalance > BigInteger.ZERO) {
            log.info("  Shareholder Token Account cleanup - burn existing $shareholderBalance tokens")
            tokenManagement.burn(
                shareholderWallet,
                tokenMint,
                shareholderWallet.deriveATA(),
                shareholderBalance.toLong()
            )
        }
        val otherShareholderBalance = solanaClient.getSolanaTokenBalance(otherShareholderWallet.deriveATA())
        if (otherShareholderBalance > BigInteger.ZERO) {
            log.info("  Other Shareholder Token Account cleanup - burn existing $otherShareholderBalance tokens")
            tokenManagement.burn(
                otherShareholderWallet,
                tokenMint,
                otherShareholderWallet.deriveATA(),
                otherShareholderBalance.toLong()
            )
        }
    }

    @Test
    fun `dev net bridge authority test`() = driver(
        DriverParameters(
            isDebug = false,
            inMemoryDB = false,
            startNodesInProcess = false,
            cordappsForAllNodes = cordappsForAllNodes,
            networkParameters = testNetworkParameters(minimumPlatformVersion = 160).copy(notaries = emptyList()),
            notarySpecs = listOf(
                NotarySpec(generalNotaryName, validating = false, startInProcess = false),
                NotarySpec(solanaNotaryName, getSolanaNotaryConfig(solanaNotarySigner), startInProcess = false)
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
