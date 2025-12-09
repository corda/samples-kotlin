package net.corda.samples.dollartohousetoken

import com.lmax.solana4j.Solana
import com.lmax.solana4j.api.PublicKey
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.NetworkParameters
import net.corda.core.utilities.getOrThrow
import net.corda.solana.notary.common.Signer
import net.corda.solana.sdk.instruction.Pubkey
import net.corda.solana.sdk.internal.Token2022
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
import java.lang.IllegalStateException
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Future
import kotlin.test.assertEquals

class DriverBasedTest {
    companion object {
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
    }
    private val validator = SolanaTestValidator()
    private val bankA = TestIdentity(CordaX500Name("BankA", "", "GB"))
    private val bankB = TestIdentity(CordaX500Name("BankB", "", "US"))
    private val solanaNotaryName = CordaX500Name("Solana Notary Service", "London", "GB")
    private lateinit var solanaNotaryKeyFile: Path
    private lateinit var solanaNotaryKey: Signer
    @TempDir
    lateinit var custodiedKeysDir: Path
    @TempDir
    lateinit var generalDir: Path
    val solanaNotaryConfig: Map<String, Any> by lazy {
        mapOf<String, Any>(
            "notary" to mapOf(
                "validating" to false,
                // "serviceLegalName" doesn't work with Driver, because it needs a distributed key that is not created
                "solana" to mapOf(
                    "rpcUrl" to SolanaTestValidator.RPC_URL,
                    "notaryKeypairFile" to "$solanaNotaryKeyFile",
                    "custodiedKeysDir" to "$custodiedKeysDir",
                    "programWhitelist" to listOf(Token2022.PROGRAM_ID.toPublicKey().base58()),
                )
            )
        )
    }
    val cordappsForAllNodes = listOf(
        TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
        TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
        TestCordapp.findCordapp("net.corda.samples.dollartohousetoken.flows"),
        TestCordapp.findCordapp("net.corda.samples.dollartohousetoken.contracts"),
        TestCordapp.findCordapp("net.corda.samples.dollartohousetoken.states"),
    )

    @BeforeEach
    fun setup () {
        solanaNotaryKeyFile = randomKeypairFile(generalDir)
        solanaNotaryKey = Signer.fromFile(solanaNotaryKeyFile)
        validator.start()
        validator.defaultNotaryProgramSetup(solanaNotaryKey.account)
    }

    @AfterEach
    fun stopTestValidator() {
        validator.close()
    }

    @Test
    fun nodeTest() {
        withDriver {
            // Start a pair of nodes and wait for them both to be ready.
            val (partyAHandle, partyBHandle) = startNodes(bankA, bankB)

            // From each node, make an RPC call to retrieve another node's name from the network map, to verify that the
            // nodes have started and can communicate.

            // This is a very basic test: in practice tests would be starting flows, and verifying the states in the vault
            // and other important metrics to ensure that your CorDapp is working as intended.
            assertEquals(bankB.name, partyAHandle.resolveName(bankB.name))
            assertEquals(bankA.name, partyBHandle.resolveName(bankA.name))
        }
    }

    // Runs a test inside the Driver DSL, which provides useful functions for starting nodes, etc.
    private fun withDriver(test: DriverDSL.() -> Unit) = driver(
        DriverParameters(isDebug = true, startNodesInProcess = true,
            cordappsForAllNodes = cordappsForAllNodes,
            notarySpecs = listOf(NotarySpec(solanaNotaryName, solanaNotaryConfig, startInProcess = false)),
            networkParameters = networkParameters)
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