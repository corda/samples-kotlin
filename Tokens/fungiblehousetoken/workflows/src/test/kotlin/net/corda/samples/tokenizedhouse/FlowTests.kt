package net.corda.samples.tokenizedhouse

import net.corda.core.identity.CordaX500Name
import net.corda.samples.tokenizedhouse.flows.CreateHouseTokenFlow
import net.corda.samples.tokenizedhouse.flows.IssueHouseTokenFlow
import net.corda.samples.tokenizedhouse.states.FungibleHouseTokenState
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future

class FlowTests {
    private lateinit var network: MockNetwork
    private lateinit var a: StartedMockNode
    private lateinit var b: StartedMockNode

    @Before
    fun setup() {
        network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("net.corda.samples.tokenizedhouse.contracts"),
                TestCordapp.findCordapp("net.corda.samples.tokenizedhouse.flows"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows")
        ), networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary","London","GB")))
        ))
        a = network.createPartyNode(null)
        b = network.createPartyNode(null)
        network.runNetwork()
    }

    @After
    fun tearDown() {
        if (::network.isInitialized) {
            network.stopNodes()
        }
    }

    @Test
    @Throws(ExecutionException::class, InterruptedException::class)
    fun houseTokenStateCreation() {
        val createFlow = CreateHouseTokenFlow("NYCHelena", 1000000)
        val future: Future<String> = a.startFlow(createFlow)
        network.runNetwork()

        //get house states on ledger with uuid as input tokenId
        val (state) = a.services.vaultService.queryBy(FungibleHouseTokenState::class.java).states.stream()
                .filter { (state) -> state.data.symbol == "NYCHelena" }.findAny()
                .orElseThrow { IllegalArgumentException("FungibleHouseTokenState not found from vault") }

        //get the RealEstateEvolvableTokenType object
        val (valuation) = state.data
        assertEquals(1000000, valuation)
    }

    @Test
    @Throws(ExecutionException::class, InterruptedException::class)
    fun houseTokenStateIssuance() {
        val createFlow = CreateHouseTokenFlow("NYCHelena", 1000000)
        val future: Future<String> = a.startFlow(createFlow)
        network.runNetwork()
        val issueFlow = IssueHouseTokenFlow("NYCHelena", 20, b.info.legalIdentities[0])
        val future2: Future<String> = a.startFlow(issueFlow)
        network.runNetwork()

        //get house states on ledger with uuid as input tokenId
        val (state) = b.services.vaultService.queryBy(FungibleHouseTokenState::class.java).states.stream()
                .filter { (state) -> state.data.symbol == "NYCHelena" }.findAny()
                .orElseThrow { java.lang.IllegalArgumentException("FungibleHouseTokenState not found from vault") }

        //get the RealEstateEvolvableTokenType object
        val (valuation) = state.data
        assert(valuation == 1000000)
    }
}