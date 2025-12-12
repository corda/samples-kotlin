package net.corda.samples.dollartohousetoken

import com.lmax.solana4j.Solana
import com.lmax.solana4j.api.PublicKey
import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import net.corda.core.node.NetworkParameters
import net.corda.core.utilities.getOrThrow
import net.corda.samples.dollartohousetoken.flows.CreateAndIssueHouseToken
import net.corda.samples.dollartohousetoken.flows.FiatCurrencyIssueFlow
import net.corda.samples.dollartohousetoken.flows.HouseSale
import net.corda.solana.notary.common.Signer
import net.corda.solana.notary.common.rpc.checkResponse
import net.corda.solana.sdk.instruction.Pubkey
import net.corda.solana.sdk.internal.SplToken
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
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
import java.util.concurrent.Future
import kotlin.collections.emptyList
import kotlin.lazy
import kotlin.test.assertEquals

class DriverBasedTest {
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
        mapOf<String, Any>(
            "notary" to mapOf(
                "validating" to false,
                "solana" to mapOf(
                    "rpcUrl" to SolanaTestValidator.RPC_URL,
                    "notaryKeypairFile" to "$solanaNotaryKeyFile",
                    "custodiedKeysDir" to "$custodiedKeysDir",
                    "programWhitelist" to listOf(SplToken.PROGRAM_ID.toPublicKey().base58()),
                )
            )
        )
    }
    val cordappsForAllNodes : List<TestCordapp> by lazy {
        setOf(
            "com.r3.corda.lib.tokens.contracts",
            "com.r3.corda.lib.tokens.workflows",
            "net.corda.samples.dollartohousetoken.contracts",
            "net.corda.samples.dollartohousetoken.states",
        ).map { TestCordapp.findCordapp(it) } + TestCordapp.findCordapp("net.corda.samples.dollartohousetoken.flows")
            .withConfig(
                mapOf(
                    "solanaTokenMint" to tokenMint.base58(),
                    "solanaSourceAccount" to bankBTokenAccount.base58(),
                    "solanaDestinationAccount" to bankATokenAccount.base58(),
                    "solanaMintAuthority" to bankBWallet.account.base58(),
                    "solanaTokenMintDecimals" to tokenDecimals
                )
            )
    }

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
    fun `node test`() = withDriver {
        val (partyAHandle, partyBHandle) = startNodes(bankA, bankB)

        assertEquals(bankB.name, partyAHandle.resolveName(bankB.name))
        assertEquals(bankA.name, partyBHandle.resolveName(bankA.name))

        assertEquals(BigDecimal.ZERO, validator.getTokenBalance(bankATokenAccount))
        assertEquals(BigDecimal("1000"), validator.getTokenBalance(bankBTokenAccount))

        val result = (partyAHandle.rpc.startFlow(::CreateAndIssueHouseToken,
            partyAHandle.nodeInfo.legalIdentities[0],
            Amount.parseCurrency("1000 USD"), 10, "500 sqft", "NA", "NYC")
            .returnValue.get())
        val houseID = result.substringAfter("UUID: ").substringBefore(".").trim()
        partyBHandle.rpc.startFlow(::FiatCurrencyIssueFlow, "USD", 100000, partyBHandle.nodeInfo.legalIdentities[0])
            .returnValue.get()

        partyAHandle.rpc.startFlow(::HouseSale, houseID, partyBHandle.nodeInfo.legalIdentities[0])
            .returnValue.get()

        assertEquals(BigDecimal("100"), validator.getTokenBalance(bankATokenAccount))
        assertEquals(BigDecimal("900"), validator.getTokenBalance(bankBTokenAccount))
    }

    // Runs a test inside the Driver DSL, which provides useful functions for starting nodes, etc.
    private fun withDriver(test: DriverDSL.() -> Unit) = driver(
        DriverParameters(
            isDebug = true, startNodesInProcess = true, cordappsForAllNodes = cordappsForAllNodes,
            notarySpecs = listOf(NotarySpec(solanaNotaryName, solanaNotaryConfig, startInProcess = true)),
            networkParameters = networkParameters
        )
    ) { test() }

    // Makes an RPC call to retrieve another node's name from the network map.
    private fun NodeHandle.resolveName(name: CordaX500Name) = rpc.wellKnownPartyFromX500Name(name)!!.name

    // Resolves a list of futures to a list of the promised values.
    private fun <T> List<Future<T>>.waitForAll(): List<T> = map { it.getOrThrow() }

    // Starts multiple nodes simultaneously, then waits for them all to be ready.
    private fun DriverDSL.startNodes(vararg identities: TestIdentity) = identities
        .map { startNode(providedName = it.name) }
        .waitForAll()
}

fun Pubkey.toPublicKey(): PublicKey = Solana.account(bytes)

fun SolanaTestValidator.getTokenBalance(publicKey: PublicKey): BigDecimal = this
    .client
    .getTokenAccountBalance(publicKey.base58(), this.rpcParams)
    .checkResponse("getTokenAccountBalance")!!
    .uiAmountString
    .toBigDecimal()