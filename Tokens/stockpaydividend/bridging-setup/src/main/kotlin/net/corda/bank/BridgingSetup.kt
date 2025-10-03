package net.corda.bank

import com.lmax.solana4j.api.PublicKey
import joptsimple.OptionParser
import net.corda.bank.solana.TokenManagement
import net.corda.bank.solana.TokenManagement.Companion.LAMPORTS_PER_SOL
import net.corda.core.identity.CordaX500Name
import net.corda.solana.aggregator.common.Signer
import net.corda.solana.aggregator.common.toPublicKey
import net.corda.solana.sdk.instruction.Pubkey
import java.io.File
import kotlin.io.path.Path
import kotlin.io.readText
import kotlin.io.writeText
import kotlin.jvm.java
import kotlin.system.exitProcess
import kotlin.text.trimIndent

//TODO use from Corda Enterprise
object BridgingSetup {

    private val NOTARY_NAME = CordaX500Name(organisation = "Notary Service", locality = "Zurich", country = "CH")

    private const val AUTHORITY_RPC_PORT = 10021

    @Suppress("ComplexMethod", "SpreadOperator")
    @JvmStatic
    fun main(args: Array<String>) {
        val parser = OptionParser()
        val rpcUrlArg =
            parser.accepts("rpcUrl").withRequiredArg().ofType(String::class.java).describedAs("Solana RPC URL")
        val roleArg = parser.accepts("role").withRequiredArg().ofType(Role::class.java)
            .describedAs("[FUND_WALLET|DEFUND_WALLET|CREATE_TOKEN|CREATE_TOKEN_ACCOUNT]")
        val funderArg = parser.accepts("funder").withRequiredArg().ofType(String::class.java)
            .describedAs("File path to the funder wallet for funding or defunding")
        val targetArg = parser.accepts("target").withRequiredArg().ofType(String::class.java)
            .describedAs("File path to the target wallet for funding or defunding")
        val tokenMintArg = parser.accepts("mint").withRequiredArg().ofType(String::class.java)
            .describedAs("File path to the token mint pubkey file")
        val tokenAccArg = parser.accepts("account").withRequiredArg().ofType(String::class.java)
            .describedAs("File path to the token account pubkey file")
        val quantityArg = parser.accepts("quantity").withOptionalArg().ofType(Long::class.java)
        val currencyArg =
            parser.accepts("currency").withOptionalArg().ofType(String::class.java).describedAs("[GBP|USD|CHF|EUR]")

        val options = try {
            parser.parse(*args)
        } catch (e: Exception) {
            println(e.message)
            printHelp(parser)
            exitProcess(1)
        }

        try {
            val role = options.valueOf(roleArg)
            val rpcUrl = options.valueOf(rpcUrlArg)
            val funderWallet = Signer.fromFile(Path(options.valueOf(funderArg)!!))
            when (role) {
                Role.FUND_WALLET -> {
                    require(rpcUrl != null) { "${role.name} requires a Solana rpc URL." }
                    val targetWallet = Signer.fromFile(Path(options.valueOf(targetArg)!!))
                    val quantity = options.valueOf(quantityArg) ?: 1
                    println("rpcUrl $rpcUrl")
                    println("funderWallet $funderWallet")
                    println("targetWallet.account ${targetWallet.account}")
                    println("quantity * LAMPORTS_PER_SOL ${quantity * LAMPORTS_PER_SOL}")
                    TokenManagement(rpcUrl).fundAccount(funderWallet, targetWallet.account, quantity * LAMPORTS_PER_SOL)
                }

                Role.DEFUND_WALLET -> {
                    require(rpcUrl != null) { "${role.name} requires a Solana rpc URL." }
                    val targetWallet = Signer.fromFile(Path(options.valueOf(targetArg)!!))

                    TokenManagement(rpcUrl).defundAccount(targetWallet, funderWallet.account)
                }

                Role.CREATE_TOKEN -> {
                    require(rpcUrl != null) { "${role.name} requires a Solana rpc URL." }
                    val mintKeyFile = options.valueOf(tokenMintArg)!!
                    // only 2 decimals as this example will bridge dollars

                    println("funderWallet $funderWallet")
                    val tokenMint = TokenManagement(rpcUrl).createToken(funderWallet, decimals = 2)
                    tokenMint.writeToFile(mintKeyFile)
                }

                Role.CREATE_TOKEN_ACCOUNT -> {
                    require(rpcUrl != null) { "${role.name} requires a Solana rpc URL." }
                    val tokenMint = readKeyFromFile(options.valueOf(tokenMintArg)!!)
                    val tokenAccountFile = options.valueOf(tokenAccArg)!!
                    val tokenAccount = TokenManagement(rpcUrl).createTokenAccount(funderWallet, tokenMint)
                    tokenAccount.writeToFile(tokenAccountFile)
                }

                Role.CREATE_BRIDGING_CONFIG -> {
//                    println("Requesting creation of bridging config state")
//                    val tokenMint = readKeyFromFile(options.valueOf(tokenMintArg)!!)
//                    val params = BankOfCordaWebApi.BridgeConfigRequestParams(
//                            Currency.getInstance(options.valueOf(currencyArg)),
//                            "1",
//                            BOC_NAME,
//                            NOTARY_NAME,
//                            tokenMint.base58(),
//                            funderWallet.account.base58()
//                    )
//                    val result = BankOfCordaClientApi.requestBridgingConfigCreation(NetworkHostAndPort("localhost", AUTHORITY_RPC_PORT), params)
//                    println("Success! Created state $result")
                }

                else -> printHelp(parser)
            }
        } catch (e: Throwable) {
            println("Caught exception: $e")
            e.printStackTrace()
            printHelp(parser)
            throw e
        }
    }

    fun readKeyFromFile(filename: String): PublicKey =
        Pubkey.fromBase58(File(filename).readText(Charsets.US_ASCII)).toPublicKey()

    fun PublicKey.writeToFile(filename: String) {
        File(filename).writeText(this.base58(), Charsets.US_ASCII)
    }

    private fun printHelp(parser: OptionParser) {
        println(
            """Modes of operation:
                Fund accounts:
                <program> --role FUND_WALLET --funder <path to funder wallet> --target <path to target wallet> [--amount <amount in SOL>]
                This will transfer an amount of SOL from the funder to the target on the ledger
                
                Defund accounts:
                <program> --role DEFUND_WALLET --funder <path to funder wallet> --target <path to target wallet>
                This will transfer all SOL from the target wallet back to the funder (marking target for deletion)
                
                Create token:
                <program> --role CREATE_TOKEN --funder <path to signer wallet> --mint <path to token mint pubkey>
                This will create a Token2022 definition with funder as payer/mint authority, and store the key in the mint pubkey file
                
                Create token account:
                <program> --role CREATE_TOKEN_ACCOUNT --funder <path to signer wallet> --mint <path to token mint pubkey> --account <path to account pubkey>
                This will create an account owned and payed for by funder that can hold tokens as defined by mint. The key will be written to account.
                
                Create bridging config
                <program> --role CREATE_BRIDGING_CONFIG --funder <path to mint authority wallet> --mint <path to token mint pubkey> --currency <currency symbol>
            """.trimIndent()
        )
        parser.printHelpOn(System.out)
    }

    enum class Role {
        FUND_WALLET,
        DEFUND_WALLET,
        CREATE_TOKEN,
        CREATE_TOKEN_ACCOUNT,
        CREATE_BRIDGING_CONFIG
    }
}