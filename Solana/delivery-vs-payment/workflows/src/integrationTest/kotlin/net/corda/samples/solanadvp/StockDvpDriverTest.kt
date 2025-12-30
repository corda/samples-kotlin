package net.corda.samples.solanadvp

import com.lmax.solana4j.Solana
import com.lmax.solana4j.api.PublicKey
import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import net.corda.core.node.NetworkParameters
import net.corda.core.utilities.getOrThrow
import net.corda.samples.solanadvp.flows.CreateAndIssueStock
import net.corda.samples.solanadvp.flows.StockDvP
import net.corda.solana.notary.common.Signer
import net.corda.solana.notary.common.rpc.checkResponse
import net.corda.solana.sdk.SplToken
import net.corda.solana.sdk.instruction.Pubkey
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.TestCordapp
import net.corda.testing.solana.SolanaTestValidator
import net.corda.testing.solana.randomKeypairFile
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.collections.emptyList
import kotlin.lazy
import kotlin.test.assertEquals

// This is a sample of full-fledged test with both Corda Nodes and Solana Local Validator
class StockDvpDriverTest {
    private val networkParameters = NetworkParameters(
        minimumPlatformVersion = 4,
        notaries = emptyList(),
        maxMessageSize = 10485760,
        maxTransactionSize = 10485760,
        modifiedTime = Instant.now(),
        epoch = 1,
        whitelistedContractImplementations = emptyMap(),
        eventHorizon = Duration.ofDays(30),
        packageOwnership = emptyMap(),
    )

    private val validator = SolanaTestValidator()
    private val bankA = TestIdentity(CordaX500Name("BankA", "", "GB"))
    private val bankB = TestIdentity(CordaX500Name("BankB", "", "US"))
    private val observer = CordaX500Name("Observer", "New York", "US")

    private val solanaNotaryName = CordaX500Name("Notary", "London", "GB")
    private lateinit var solanaNotaryKeyFile: Path
    private lateinit var solanaNotaryKey: Signer
    private val mintAuthoritySigner by lazy { Signer.fromFile(randomKeypairFile(custodiedKeysDir)) }
    private lateinit var tokenMint: PublicKey
    private val bankAWallet = Signer.random()
    private val bankBWallet by lazy { Signer.fromFile(randomKeypairFile(custodiedKeysDir)) }
    private lateinit var bankATokenAccount: PublicKey
    private lateinit var bankBTokenAccount: PublicKey
    private val tokenDecimals = 3

    @TempDir
    lateinit var custodiedKeysDir: Path

    @TempDir
    lateinit var generalDir: Path
    val solanaNotaryConfig: Map<String, Any> by lazy {
        mapOf(
            "notary" to mapOf(
                "validating" to false,
                "solana" to mapOf(
                    "rpcUrl" to SolanaTestValidator.RPC_URL,
                    "websocketUrl" to SolanaTestValidator.WS_URL,
                    "notaryKeypairFile" to "$solanaNotaryKeyFile",
                    "custodiedKeysDir" to "$custodiedKeysDir",
                    "programWhitelist" to listOf(SplToken.PROGRAM_ID.toPublicKey().base58()),
                )
            )
        )
    }
    val flowCordapp = TestCordapp.findCordapp("net.corda.samples.solanadvp.flows")
    val cordappsForAllNodes: List<TestCordapp> by lazy {
        setOf(
            "com.r3.corda.lib.tokens.contracts",
            "com.r3.corda.lib.tokens.workflows",
            "net.corda.samples.solanadvp.contracts",
            "net.corda.samples.solanadvp.states",
        ).map { TestCordapp.findCordapp(it) }
    }

    val bankAConfig: Map<String, Any> by lazy {
        mapOf(
            "solanaTokenMint" to tokenMint.base58(),
            "solanaTokenAccount" to bankATokenAccount.base58(),
            "solanaWalletAccount" to bankAWallet.account.base58() // not used in  the test
        )
    }

    val bankBConfig: Map<String, Any> by lazy {
        mapOf(
            "solanaTokenMint" to tokenMint.base58(),
            "solanaTokenAccount" to bankBTokenAccount.base58(),
            "solanaWalletAccount" to bankBWallet.account.base58(),
        )
    }

    val STOCK_SYMBOL = "TEST"
    val STOCK_NAME = "Test Stock"
    val STOCK_CURRENCY = "USD"
    val STOCK_PRICE = BigDecimal.valueOf(7.4)
    val ISSUING_STOCK_QUANTITY = 200000L

    @BeforeEach
    fun setup() {
        solanaNotaryKeyFile = randomKeypairFile(generalDir)
        solanaNotaryKey = Signer.fromFile(solanaNotaryKeyFile)
        validator.start()
        validator.defaultNotaryProgramSetup(solanaNotaryKey.account)
        setOf(mintAuthoritySigner, bankAWallet, bankBWallet).forEach {
            validator.fundAccount(100000, it)
        }
        tokenMint = validator.createToken(mintAuthoritySigner, decimals = tokenDecimals.toByte(), isToken2022 = false)
        bankATokenAccount = validator.createTokenAccount(bankAWallet, tokenMint, isToken2022 = false)
        bankBTokenAccount = validator.createTokenAccount(bankBWallet, tokenMint, isToken2022 = false)
        validator.mintTo(mintAuthoritySigner, tokenMint, bankBTokenAccount, 1000000, isToken2022 = false)
    }

    @AfterEach
    fun stopTestValidator() {
        validator.close()
    }

    @Test
    fun `dvp test`() = withDriver {
        val partyA = startNode(
            providedName = bankA.name,
            defaultParameters = NodeParameters().withAdditionalCordapps(setOf(flowCordapp.withConfig(bankAConfig)))
        ).getOrThrow()
        val partyB = startNode(
            providedName = bankB.name,
            defaultParameters = NodeParameters().withAdditionalCordapps(setOf(flowCordapp.withConfig(bankBConfig)))
        ).getOrThrow()
        startNode(providedName = observer).getOrThrow()

        assertEquals(BigDecimal.ZERO, validator.getTokenBalance(bankATokenAccount))
        assertEquals(BigDecimal("1000"), validator.getTokenBalance(bankBTokenAccount))

        val result = partyA.rpc.startFlow(::CreateAndIssueStock,
            STOCK_SYMBOL,
            STOCK_NAME,
            STOCK_CURRENCY,
            STOCK_PRICE,
            ISSUING_STOCK_QUANTITY
        ).returnValue.get()

        partyA.rpc.startFlow(::StockDvP,
            STOCK_SYMBOL,
            100, // TODO compute stock amount * stock price
            Amount.parseCurrency("1000 USD"),
            partyB.nodeInfo.legalIdentities[0]
        ).returnValue.get()

        assertEquals(BigDecimal("100"), validator.getTokenBalance(bankATokenAccount))
        assertEquals(BigDecimal("900"), validator.getTokenBalance(bankBTokenAccount))
    }

    // Runs a test inside the Driver DSL, which provides useful functions for starting nodes, etc.
    private fun withDriver(test: DriverDSL.() -> Unit) = driver(
        DriverParameters(
            isDebug = true,
            startNodesInProcess = false,
            cordappsForAllNodes = cordappsForAllNodes,
            notarySpecs = listOf(NotarySpec(solanaNotaryName, solanaNotaryConfig, startInProcess = false)),
            networkParameters = networkParameters
        )
    ) { test() }
}


fun Pubkey.toPublicKey(): PublicKey = Solana.account(bytes)

fun SolanaTestValidator.getTokenBalance(publicKey: PublicKey): BigDecimal =
    client
        .getTokenAccountBalance(publicKey.base58(), rpcParams)
        .checkResponse("getTokenAccountBalance")!!
        .uiAmountString
        .toBigDecimal()