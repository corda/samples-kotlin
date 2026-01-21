package net.corda.samples.solana.bridging.token

import com.r3.corda.lib.solana.bridging.token.flows.SavaFactory.toPublicKey
import net.corda.node.utilities.solana.AccountManagement
import net.corda.node.utilities.solana.FileSigner
import net.corda.node.utilities.solana.SolanaClient
import net.corda.node.utilities.solana.TokenManagement
import net.corda.node.utilities.solana.TokenProgram
import net.corda.solana.sdk.Token2022
import software.sava.core.accounts.PublicKey
import java.math.BigDecimal
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths

// shareholder https://solscan.io/account/3Wuk6fKtqCzMppikC1S58vK3J5ZbqnbcZXkDhJUHCom7?cluster=devnet#portfolio
// other shareholder https://solscan.io/account/AoDHzQwk7s6crxMcC1nptRVHAhd1LASEWbQmAQjCeBKj?cluster=devnet#portfolio
class DevNetBridgingTokenDriverTest : BridgingTokenDriverTest() {

    override val solanaRpcUrl = "https://api.devnet.solana.com"
    override val solanaWssUrl = "ws://api.devnet.solana.com"
    override val shareholderInitialSolanaTokens: BigDecimal = BigDecimal(40)
    override val otherShareholderInitialSolanaTokens: BigDecimal = BigDecimal(50)

    override fun startTestValidator() {

        val notaryKeyPath =
            Paths.get("../../../../enterprise/solana-devnet/network-0-notary-1-key.json").toAbsolutePath().toString()
       // val notaryKeyPath =
       //     Paths.get("../../Dev7chG99tLCAny3PNYmBdyhaKEVcZnSTp3p1mKVb5m5.json").toAbsolutePath().toString()
        solanaNotarySigner = FileSigner.read(Path.of(notaryKeyPath))
        solanaClient = SolanaClient(URI.create(solanaRpcUrl), URI.create(solanaWssUrl)).apply { start() }
        tokenManagement = TokenManagement(solanaClient)
        accountManagement = AccountManagement(solanaClient)
        //log.info("  Airdrop for: ${solanaNotarySigner.publicKey().toBase58()}")
        //accountManagement.airdropSol(solanaNotarySigner.publicKey(), 1)
    }

    override fun setupAccounts() {
        val custodiedKeysDir = "src/integrationTest/resources/custodiedKeys"
        solanaNotaryConfig = mapOf<String, Any>(
            "notary" to mapOf(
                "validating" to false,
                "solana" to mapOf(
                    "rpcUrl" to solanaRpcUrl,
                    "websocketUrl" to solanaWssUrl,
                    "notaryKeypairFile" to "${solanaNotarySigner.file}",
                    "custodiedKeysDir" to "${Path.of(custodiedKeysDir).toAbsolutePath()}",
                    "programWhitelist" to listOf(Token2022.PROGRAM_ID.toPublicKey().toBase58())
                )
            )
        )
        bridgeAuthoritySigner = FileSigner.read(Path.of("$custodiedKeysDir/bridgeAuthority.json").toAbsolutePath())
        redemptionWalletForShareholder = FileSigner.read(Path.of("$custodiedKeysDir/redemptionWalletForShareholder.json").toAbsolutePath())
        redemptionWalletForOtherShareholder = FileSigner.read(Path.of("$custodiedKeysDir/redemptionWalletForOtherShareholder.json").toAbsolutePath())
        mintAuthoritySigner = FileSigner.read(Path.of("$custodiedKeysDir/mintAuthoritySigner.json").toAbsolutePath())
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
        //accountManagement.airdropSol(mintAuthoritySigner.publicKey(), 1)

        tokenMint = PublicKey.fromBase58Encoded("GMWmvcYWWWCv1V7WW8pwXeFej97od3SPBvSUR6wsAhSC")
        tokenMint2 = PublicKey.fromBase58Encoded("AhZNYSxWTVCbXMcZNJv27e7G38G7auFaqx4zCBBfWikc")
                //tokenManagement.createToken(mintAuthoritySigner, TokenProgram.TOKEN_2022, decimals = TOKEN_DECIMALS)

        log.info("  Creating Solana Token.")
        log.info("  Shareholder Token Account: ${shareholderWallet.deriveATA().toBase58()}")
        log.info("  Other Shareholder Token Account: ${otherShareholderWallet.deriveATA().toBase58()}")
        log.info("  Shareholder Redemption Token Account: ${redemptionWalletForShareholder.deriveATA().toBase58()}")
        log.info("  Other Shareholder Redemption Token Account: ${redemptionWalletForOtherShareholder.deriveATA().toBase58()}")
//      tokenManagement.createAta(
//            mintAuthoritySigner,
//            otherShareholderWallet.publicKey(),
//            tokenMint,
//            Token2022.PROGRAM_ID.toPublicKey()
//        )
//        tokenManagement.createAta(
//            mintAuthoritySigner,
//            redemptionWalletForOtherShareholder.publicKey(),
//            tokenMint,
//            Token2022.PROGRAM_ID.toPublicKey()
//        )
//        tokenManagement.createAta(
//            mintAuthoritySigner,
//            redemptionWalletForShareholder.publicKey(),
//            tokenMint,
//            Token2022.PROGRAM_ID.toPublicKey()
//        )
    }

    override fun stopTestValidator() = Unit
}