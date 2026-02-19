package net.corda.samples.solana.bridge.token

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
import org.slf4j.LoggerFactory
import software.sava.core.accounts.PublicKey
import java.math.BigDecimal
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths

open class DevNetBridgeTokenTest : TestBase() {

    private val log = LoggerFactory.getLogger(DevNetBridgeTokenTest::class.java)

    // A directory for Notary to store Corda participant key pairs for signing Solana transactions,
    // intentionally these are located in a different directory than Corda Notary Program key pair
    protected val staticCustodiedKeysDir = "src/integrationTest/resources/custodiedKeys"
    protected lateinit var solanaNotarySigner: FileSigner

    fun getSolanaNotaryConfig(solanaNotarySigner: FileSigner) = mapOf<String, Any>(
            "notary" to mapOf(
                "validating" to false,
                "solana" to mapOf(
                    "rpcUrl" to solanaRpcUrl,
                    "websocketUrl" to solanaWebsocketUrl,
                    "notaryKeypairFile" to "${solanaNotarySigner.file}",
                    "custodiedKeysDir" to "${Path.of(staticCustodiedKeysDir).toAbsolutePath()}"
                )
            )
        )

    @BeforeEach
    fun setup() {
        solanaRpcUrl = "https://api.devnet.solana.com"
        solanaWebsocketUrl = "ws://api.devnet.solana.com"
        val notaryKeyPath =
            Paths.get("../../Dev7chG99tLCAny3PNYmBdyhaKEVcZnSTp3p1mKVb5m5.json").toAbsolutePath().toString()
        solanaNotarySigner = FileSigner.read(Path.of(notaryKeyPath))
        solanaClient = SolanaClient(URI.create(solanaRpcUrl), URI.create(solanaWebsocketUrl)).apply { start() }
        tokenManagement = TokenManagement(solanaClient)
        accountManagement = AccountManagement(solanaClient)

        bridgeAuthoritySigner =
            FileSigner.read(Path.of("$staticCustodiedKeysDir/bridgeAuthority.json").toAbsolutePath())
        redemptionWalletForShareholder =
            FileSigner.read(Path.of("$staticCustodiedKeysDir/redemptionWalletForShareholder.json").toAbsolutePath())
        redemptionWalletForOtherShareholder =
            FileSigner.read(
                Path.of("$staticCustodiedKeysDir/redemptionWalletForOtherShareholder.json").toAbsolutePath()
            )
        mintAuthoritySigner =
            FileSigner.read(Path.of("$staticCustodiedKeysDir/mintAuthoritySigner.json").toAbsolutePath())
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
        log.info("  Shareholder Token Account: ${shareholderWallet.toATA().toBase58()}")
        log.info("  Other Shareholder Token Account: ${otherShareholderWallet.toATA().toBase58()}")
        log.info("  Shareholder Redemption Token Account: ${redemptionWalletForShareholder.toATA().toBase58()}")
        log.info(
            "  Other Shareholder Redemption Token Account: ${
                redemptionWalletForOtherShareholder.toATA().toBase58()
            }"
        )

        val shareholderBalance = solanaClient.getSolanaTokenBalance(shareholderWallet.toATA())
        if (shareholderBalance > BigDecimal.ZERO) {
            log.info("  Shareholder Token Account cleanup - burn existing $shareholderBalance tokens")
            tokenManagement.burn(
                shareholderWallet,
                tokenMint,
                shareholderWallet.toATA(),
                shareholderBalance.toLong()
            )
        }
        val otherShareholderBalance = solanaClient.getSolanaTokenBalance(otherShareholderWallet.toATA())
        if (otherShareholderBalance > BigDecimal.ZERO) {
            log.info("  Other Shareholder Token Account cleanup - burn existing $otherShareholderBalance tokens")
            tokenManagement.burn(
                otherShareholderWallet,
                tokenMint,
                otherShareholderWallet.toATA(),
                otherShareholderBalance.toLong()
            )
        }
    }

    @Test
    fun `dev net bridge token test`() = driver(
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