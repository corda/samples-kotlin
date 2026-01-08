package net.corda.samples.solana.bridging.token

import com.lmax.solana4j.Solana
import com.lmax.solana4j.api.PublicKey
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.solana.notary.common.Signer
import net.corda.solana.sdk.Token2022
import net.corda.solana.sdk.instruction.Pubkey
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.TestCordapp
import net.corda.testing.node.User
import net.corda.testing.solana.SolanaTestValidator
import net.corda.testing.solana.randomKeypairFile
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertTrue

class BridgingTokenDriverTest {

    companion object {
        private val validator = SolanaTestValidator()
        private lateinit var solanaNotaryKeyFile: Path
        private lateinit var solanaNotaryKey: Signer

        // A directory with Corda Notary key pair for singing Corda Program on Solana
        @TempDir
        private lateinit var notaryKeyDir: Path

        @JvmStatic
        @BeforeAll
        fun startTestValidator() {
            solanaNotaryKeyFile = randomKeypairFile(notaryKeyDir)
            solanaNotaryKey = Signer.fromFile(solanaNotaryKeyFile)
            validator.start()
            validator.defaultNotaryProgramSetup(solanaNotaryKey.account)
        }

        @JvmStatic
        @AfterAll
        fun stopTestValidator() {
            validator.close()
        }
    }

    private val solanaNotaryName = CordaX500Name("Solana Notary", "London", "GB")
    private val generalNotaryName = CordaX500Name("Notary", "London", "GB")
    private val bridgeAuthority = CordaX500Name("Bridge Authority", "New York", "US")
    private val wayneCoName = CordaX500Name("WayneCo", "SF", "US")
    private lateinit var solanaNotaryConfig: Map<String, Any>
    private val stockpaydividendFlows = TestCordapp.findCordapp("net.corda.samples.stockpaydividend.flows").withConfig(
        mapOf("notary" to "O=Notary,L=London,C=GB")
    )
    private val cordappsForAllNodes: List<TestCordapp> =
        setOf(
            "com.r3.corda.lib.tokens.contracts",
            "com.r3.corda.lib.tokens.workflows",
            "net.corda.samples.stockpaydividend.states",
            "net.corda.samples.stockpaydividend.contracts",
        ).map { TestCordapp.findCordapp(it) } + stockpaydividendFlows
    val bridgingContracts = TestCordapp.findCordapp("com.r3.corda.lib.solana.bridging.token.contracts")
    var bridgingWorkflows: TestCordapp = TestCordapp.findCordapp("com.r3.corda.lib.solana.bridging.token.flows")

    val rpcUsers = listOf(User("user1", "test", permissions = setOf("ALL")))

    private val TOKEN_DECIMALS = 3

    // A directory for Notary to store Corda participant key pairs for sining Solana transactions,
    // intentionally these are located in a different directory than Corda Notary Program key pair
    @TempDir
    private lateinit var custodiedKeysDir: Path

    private lateinit var bridgeAuthorityWalletFile: Path
    private lateinit var bridgeAuthorityWallet: Signer

    private val wayneCoWallet: Signer = Signer.random()

    private lateinit var redemptionWalletForWayneCo: Signer
    private lateinit var mintAuthoritySigner: Signer
    private lateinit var tokenMint: PublicKey

    @BeforeEach
    fun setup() {
        solanaNotaryConfig = mapOf<String, Any>(
            "notary" to mapOf(
                "validating" to false,
                "solana" to mapOf(
                    "rpcUrl" to SolanaTestValidator.RPC_URL,
                    "websocketUrl" to SolanaTestValidator.WS_URL,
                    "notaryKeypairFile" to "$solanaNotaryKeyFile",
                    "custodiedKeysDir" to "$custodiedKeysDir",
                    "programWhitelist" to listOf(Token2022.PROGRAM_ID.toPublicKey().base58())
                )
            )
        )
        bridgeAuthorityWalletFile = randomKeypairFile(custodiedKeysDir)
        bridgeAuthorityWallet = Signer.fromFile(bridgeAuthorityWalletFile)
        redemptionWalletForWayneCo = Signer.fromFile(randomKeypairFile(custodiedKeysDir))

        mintAuthoritySigner = Signer.fromFile(randomKeypairFile(custodiedKeysDir))
        validator.fundAccount(10, mintAuthoritySigner)

        tokenMint = validator.createToken(mintAuthoritySigner, decimals = TOKEN_DECIMALS.toByte())

        bridgingWorkflows = bridgingWorkflows.withConfig(
            mapOf(
                "participants" to mapOf(
                    "$wayneCoName" to wayneCoWallet.account.base58(),
                    //TODO one more participant
                ),
                "redemptionWalletAccountToHolder" to mapOf(
                    redemptionWalletForWayneCo.account.base58() to "$wayneCoName",
                    //TODO one more participant
                ),
                "mintsWithAuthorities" to mapOf(
                    "tokenTypeIdentifier" to
                            mapOf(
                                "tokenMint" to tokenMint.base58(),
                                "mintAuthority" to mintAuthoritySigner.account.base58()
                            )
                ),
                "lockingIdentityLabel" to UUID.randomUUID().toString(),
                "solanaNotaryName" to solanaNotaryName.toString(),
                "generalNotaryName" to generalNotaryName.toString(),
                "solanaWsUrl" to SolanaTestValidator.WS_URL,
                "solanaRpcUrl" to SolanaTestValidator.RPC_URL,
                "bridgeAuthorityWalletFile" to bridgeAuthorityWalletFile.toString()
            )
        )
    }

    @Test
    fun `briding token test`() = withDriver {
        val bridgingAuthorityNode = startNode(
            NodeParameters(
                providedName = bridgeAuthority,
                rpcUsers = rpcUsers,
                additionalCordapps = listOf(
                    bridgingContracts,
                    bridgingWorkflows
                )
            )
        ).getOrThrow()

        val wayneCoNode = startNode(
            NodeParameters(wayneCoName, rpcUsers)
        ).getOrThrow()

        val shareholderNode = startNode(
            NodeParameters(
                CordaX500Name("Shareholder", "New York", "US"),
                rpcUsers
            )
        ).getOrThrow()

        val bankNode = startNode(
            NodeParameters(
                CordaX500Name("Bank", "Washington DC", "US"),
                rpcUsers
            )
        ).getOrThrow()

        val observerNode = startNode(
            NodeParameters(
                CordaX500Name("Observer", "Washington DC", "US"),
                rpcUsers
            )
        ).getOrThrow()

        assertTrue(true)
        // TODO
    }

    // Runs a test inside the Driver DSL
    private fun withDriver(test: DriverDSL.() -> Unit) = driver(
        DriverParameters(
            isDebug = false,
            inMemoryDB = false,
            startNodesInProcess = false,
            cordappsForAllNodes = cordappsForAllNodes,
            networkParameters = testNetworkParameters(minimumPlatformVersion = 160).copy(notaries = emptyList()),
            notarySpecs = listOf(
                NotarySpec(generalNotaryName, validating = false, startInProcess = false),
                NotarySpec(solanaNotaryName, solanaNotaryConfig, startInProcess = false)
            )
        )
    ) { test() }
}

fun Pubkey.toPublicKey(): PublicKey = Solana.account(bytes)