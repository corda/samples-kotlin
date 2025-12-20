package net.corda.samples.solanadvp

import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.Vault.StateStatus
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.QueryCriteria.LinearStateQueryCriteria
import net.corda.samples.solanadvp.flows.CreateAndIssueNonFungibleToken
import net.corda.samples.solanadvp.states.DeliveryState
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import java.util.concurrent.Future
import kotlin.test.assertEquals


class NonFungibleDvpTests {
    private var network: MockNetwork? = null
    private var a: StartedMockNode? = null
    private var b: StartedMockNode? = null

    @BeforeEach
    fun setup() {
        network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("net.corda.samples.solanadvp.contracts"),
                TestCordapp.findCordapp("net.corda.samples.solanadvp.flows"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows")
        ), networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary","London","GB")))
        ))
        a = network!!.createPartyNode(null)
        b = network!!.createPartyNode(null)
        network!!.runNetwork()
    }

    @AfterEach
    fun tearDown() {
        network!!.stopNodes()
    }

    @Test
    fun tokenStateCreation() {
        val createAndIssueFlow = CreateAndIssueNonFungibleToken(b!!.info.legalIdentities[0], Amount.parseCurrency("1000 USD"))
        val future: Future<String> = a!!.startFlow(createAndIssueFlow)
        network!!.runNetwork()
        val resultString = future.get()
        println(resultString)
        val subString = resultString.indexOf("UUID: ");
        val nonfungibleTokenId = resultString.substring(subString + 6, resultString.indexOf(". (This"))
        println("-" + nonfungibleTokenId + "-")
        val inputCriteria: QueryCriteria = LinearStateQueryCriteria().withUuid(Arrays.asList(UUID.fromString(nonfungibleTokenId))).withStatus(StateStatus.UNCONSUMED)
        val storedNonFungibleTokenb = b!!.services.vaultService.queryBy(DeliveryState::class.java, inputCriteria).states
        val (linearId) = storedNonFungibleTokenb[0].state.data
        println("-$linearId-")
        assertEquals(linearId.toString(), nonfungibleTokenId)
    }
}