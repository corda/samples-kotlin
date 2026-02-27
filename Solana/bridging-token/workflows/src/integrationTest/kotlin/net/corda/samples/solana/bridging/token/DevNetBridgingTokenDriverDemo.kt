package net.corda.samples.solana.bridging.token

import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.NotarySpec
import org.junit.jupiter.api.Test

// Issuer: https://solscan.io/account/3Wuk6fKtqCzMppikC1S58vK3J5ZbqnbcZXkDhJUHCom7?cluster=devnet#portfolio
// Custodian https://solscan.io/account/AoDHzQwk7s6crxMcC1nptRVHAhd1LASEWbQmAQjCeBKj?cluster=devnet#portfolio
class DevNetBridgingTokenDriverDemo : DevNetBridgingTokenDriverTest() {

    @Test
    override fun `briding token test`() = driver(
        DriverParameters(
            isDebug = false,
            inMemoryDB = false,
            startNodesInProcess = false,
            cordappsForAllNodes = cordappsForAllNodes,
            networkParameters = testNetworkParameters(minimumPlatformVersion = 160).copy(notaries = emptyList()),
            notarySpecs = listOf(
                NotarySpec(generalNotaryName, getSolanaNotaryConfig(), startInProcess = false),
                NotarySpec(solanaNotaryName, getSolanaNotaryConfig(), startInProcess = false)
            ),
            waitForAllNodesToFinish = true
        )
    ) {
        log.info("\n╔══════════════════════════════════════════════════════╗")
        log.info("║  Corda-Solana Bridging Demo                         ║")
        log.info("║  Solana RPC: $solanaRpcUrl")
        log.info("╚══════════════════════════════════════════════════════╝")
        log.info("\nStarting notary nodes (General + Solana)...")
        log.info("  This may take a minute while Corda nodes boot up...")
        runtimeSetup()
        log.info("\n╔══════════════════════════════════════════════════════╗")
        log.info("║  DEMO READY                                         ║")
        log.info("║                                                      ║")
        log.info("║  Issuer RPC:            localhost:10345               ║")
        log.info("║  Custodian RPC:         localhost:10349               ║")
        log.info("║                                                      ║")
        log.info("║  Start the BFF and UI instances to begin.            ║")
        log.info("║  Shut down Corda nodes externally to exit.           ║")
        log.info("╚══════════════════════════════════════════════════════╝")
    }
}