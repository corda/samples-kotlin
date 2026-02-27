package net.corda.samples.solana.bridge.token

import com.r3.corda.lib.solana.core.FileSigner
import com.r3.corda.lib.solana.core.tokens.TokenProgram
import com.r3.corda.lib.solana.testing.SolanaTestValidator
import net.corda.solana.notary.testing.Notary
import net.corda.solana.notary.testing.SolanaNotaryExtension
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.NotarySpec
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@ExtendWith(SolanaNotaryExtension::class)
open class LocalNetBridgeAuthorityTest : TestBase() {
    private lateinit var validator: SolanaTestValidator

    // A directory for Notary to store Corda participant key pairs for signing Solana transactions,
    // the keys are located in a different directory than Corda Notary Program key pair
    @TempDir
    private lateinit var custodiedKeysDir: Path

    @TempDir
    private lateinit var otherDir: Path

    fun getSolanaNotaryConfig(solanaNotarySigner: FileSigner) = mapOf<String, Any>(
        "notary" to mapOf(
            "validating" to false,
            "solana" to mapOf(
                "rpcUrl" to "$solanaRpcUrl",
                "websocketUrl" to "$solanaWebsocketUrl",
                "notaryKeypairFile" to "${solanaNotarySigner.file}",
                "custodiedKeysDir" to "$custodiedKeysDir"
            )
        )
    )

    @BeforeEach
    fun setup(validator: SolanaTestValidator) {
        this.validator = validator
        solanaClient = validator.client()
        tokenManagement = validator.tokens()
        accountManagement = validator.accounts()
        solanaRpcUrl = validator.rpcUrl()
        solanaWebsocketUrl = validator.websocketUrl()

        bridgeAuthoritySigner = FileSigner.random(custodiedKeysDir)
        redemptionWalletForShareholder = FileSigner.random(custodiedKeysDir)
        redemptionWalletForOtherShareholder = FileSigner.random(custodiedKeysDir)
        mintAuthoritySigner = FileSigner.random(custodiedKeysDir)
        shareholderWallet = FileSigner.random(otherDir)
        otherShareholderWallet = FileSigner.random(otherDir)

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

        log.info("  Airdrop for: ${mintAuthoritySigner.publicKey().toBase58()}")
        accountManagement.airdropSol(mintAuthoritySigner.publicKey(), 1)

        // Stockpaydividend Cordapp doesn't use fraction digits however Solana token mint has 2 fraction digits,
        // Bridge Authority handles the conversion between Corda and Solana token amounts automatically.
        val tokenDecimals = 2

        tokenMint =
            tokenManagement.createToken(mintAuthoritySigner, TokenProgram.TOKEN_2022, decimals = tokenDecimals)
        //tokenMint2 not in use in the test, it's a showcase that can be multiple asset mapping
        tokenMint2 =
            tokenManagement.createToken(mintAuthoritySigner, TokenProgram.TOKEN_2022, decimals = tokenDecimals)

        log.info("  Shareholder Token Account: ${shareholderWallet.deriveATA().toBase58()}")
        log.info("  Other Shareholder Token Account: ${otherShareholderWallet.deriveATA().toBase58()}")

        accountManagement.airdropSol(bridgeAuthoritySigner.publicKey(), 1)
        accountManagement.airdropSol(shareholderWallet.publicKey(), 1)
        accountManagement.airdropSol(otherShareholderWallet.publicKey(), 1)
        accountManagement.airdropSol(redemptionWalletForShareholder.publicKey(), 1)
        accountManagement.airdropSol(redemptionWalletForOtherShareholder.publicKey(), 1)

        tokenManagement.createAssociatedTokenAccount(
            mintAuthoritySigner,
            tokenMint,
            otherShareholderWallet.publicKey(),
        )
        tokenManagement.createAssociatedTokenAccount(
            mintAuthoritySigner,
            tokenMint,
            redemptionWalletForOtherShareholder.publicKey(),
        )
        tokenManagement.createAssociatedTokenAccount(
            mintAuthoritySigner,
            tokenMint,
            redemptionWalletForShareholder.publicKey(),
        )
    }

    @Test
    open fun `local net bridge authority test`(@Notary notarySigner: FileSigner) = driver(
        DriverParameters(
            isDebug = false,
            inMemoryDB = false,
            startNodesInProcess = false,
            cordappsForAllNodes = cordappsForAllNodes,
            networkParameters = testNetworkParameters(minimumPlatformVersion = 160).copy(notaries = emptyList()),
            notarySpecs = listOf(
                NotarySpec(generalNotaryName, validating = false, startInProcess = false),
                NotarySpec(solanaNotaryName, getSolanaNotaryConfig(notarySigner), startInProcess = false)
            ),
            waitForAllNodesToFinish = false
        )
    ) {
        log.info("\nStarting bridging test using local Solana validator...")
        runtimeSetup()
        test()
        log.info("\nBridging test is completed.")
    }
}