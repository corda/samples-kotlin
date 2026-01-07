package net.corda.samples.solana.bridging.token

import com.lmax.solana4j.Solana
import com.lmax.solana4j.api.PublicKey
import net.corda.core.identity.CordaX500Name
import net.corda.solana.notary.common.Signer
import net.corda.solana.sdk.SplToken
import net.corda.solana.sdk.instruction.Pubkey
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.TestCordapp
import net.corda.testing.solana.SolanaTestValidator
import net.corda.testing.solana.randomKeypairFile
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class BridgingTokenDriverTest {

    companion object {
        private val validator = SolanaTestValidator()

        @JvmStatic
        @AfterAll
        fun stopTestValidator() {
            validator.close()
        }
    }

    private val solanaNotaryName = CordaX500Name("Notary", "London", "GB")
    private lateinit var notaryConfig: Map<String, Any>
    private val cordappsForAllNodes: List<TestCordapp> =
        setOf(
            "com.r3.corda.lib.tokens.contracts",
            "com.r3.corda.lib.tokens.workflows",
            "net.corda.samples.stockpaydividend.flows",
            "net.corda.samples.stockpaydividend.states",
            "net.corda.samples.stockpaydividend.contracts",
        ).map { TestCordapp.findCordapp(it) }

    private lateinit var solanaNotaryKeyFile: Path
    private lateinit var solanaNotaryKey: Signer

    // A directory with Corda Notary key pair for singing Corda Program on Solana
    @TempDir
    private lateinit var notaryKeyDir: Path

    // A directory for Notary to store Corda participant key pairs for sining Solana transactions,
    // intentionally these are located in a different directory than Corda Notary Program key pair
    @TempDir
    private lateinit var custodiedKeysDir: Path

    @BeforeEach
    fun setup() {
        solanaNotaryKeyFile = randomKeypairFile(notaryKeyDir)
        solanaNotaryKey = Signer.fromFile(solanaNotaryKeyFile)
        validator.start()
        validator.defaultNotaryProgramSetup(solanaNotaryKey.account)

        // corda configs
        notaryConfig = mapOf(
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

    @Test
    fun `briding token test`() = withDriver {

    }

    // Runs a test inside the Driver DSL
    private fun withDriver(test: () -> Unit) = driver(
        DriverParameters(
            isDebug = true,
            startNodesInProcess = false,
            cordappsForAllNodes = cordappsForAllNodes,
            notarySpecs = listOf(NotarySpec(solanaNotaryName, notaryConfig, startInProcess = false)),
            networkParameters = testNetworkParameters(minimumPlatformVersion = 160).copy(notaries = emptyList())
        )
    ) { test() }
}

fun Pubkey.toPublicKey(): PublicKey = Solana.account(bytes)