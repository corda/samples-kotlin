package net.corda.samples.solana.bridge.token

import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.NotarySpec
import org.junit.jupiter.api.Test

class DevNetBridgeTokenDemo : DevNetBridgeTokenTest() {

    @Test
    fun `dev net bridge token demo`() = driver(
        DriverParameters(
            isDebug = false,
            inMemoryDB = false,
            startNodesInProcess = false,
            cordappsForAllNodes = cordappsForAllNodes,
            networkParameters = testNetworkParameters(minimumPlatformVersion = 160).copy(notaries = emptyList()),
            notarySpecs = listOf(
                NotarySpec(generalNotaryName, getSolanaNotaryConfig(solanaNotarySigner), startInProcess = false),
                NotarySpec(solanaNotaryName, getSolanaNotaryConfig(solanaNotarySigner), startInProcess = false)
            ),
            waitForAllNodesToFinish = true
        )
    ) {
        log.info("\nStarting bridge test using Solana validator via $solanaRpcUrl...")
        runtimeSetup()
        log.info("\nBridge deployment is running, shut down Corda nodes externally to exit...")
        log.info("\nShareholders wallets:")
        log.info("\n Shareholder: https://solscan.io/account/3Wuk6fKtqCzMppikC1S58vK3J5ZbqnbcZXkDhJUHCom7?cluster=devnet#portfolio")
        log.info("\n Other Shareholder: https://solscan.io/account/AoDHzQwk7s6crxMcC1nptRVHAhd1LASEWbQmAQjCeBKj?cluster=devnet#portfolio")
    }
}