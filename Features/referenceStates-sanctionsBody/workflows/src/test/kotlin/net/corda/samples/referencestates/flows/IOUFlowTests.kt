package net.corda.samples.referencestates.flows

import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test

class IOUFlowTests {
    lateinit var network: MockNetwork
    lateinit var a: StartedMockNode
    lateinit var b: StartedMockNode
    lateinit var c: StartedMockNode
    lateinit var issuer: StartedMockNode
    lateinit var issuerParty: Party

    @Before
    fun setup() {
        network = MockNetwork(
            listOf("net.corda.samples.referencestates.contracts"),
            MockNetworkParameters(
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary","London","GB")))
            )
        )
        a = network.createPartyNode()
        b = network.createPartyNode()
        c = network.createPartyNode()
        issuer = network.createPartyNode()
        issuerParty = issuer.info.legalIdentities.single()

        // Register ALL flow responders on ALL nodes
        listOf(a, b, c, issuer).forEach { node ->
            node.registerInitiatedFlow(IOUIssueFlow.Acceptor::class.java)
            node.registerInitiatedFlow(IOUSettleFlow.Acceptor::class.java)
            node.registerInitiatedFlow(IOUTransferFlow.Acceptor::class.java)
            node.registerInitiatedFlow(GetSanctionsListFlow.Acceptor::class.java)
        }

        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    @Test(expected = TransactionVerificationException.ContractRejection::class)
    fun `deal fails if there is no issued sanctions list`() {
        val flow = IOUIssueFlow.Initiator(1, b.info.singleIdentity(), issuerParty)
        val future = a.startFlow(flow)
        network.runNetwork()

        future.getOrThrow()
    }

    @Test
    fun `deal succeeds with issued sanctions`() {
        val issuanceFlow = issuer.startFlow(IssueSanctionsListFlow.Initiator())
        network.runNetwork()
        issuanceFlow.getOrThrow()

        getSanctionsList(a, issuerParty)

        val flow = IOUIssueFlow.Initiator(1, b.info.singleIdentity(), issuerParty)
        val future = a.startFlow(flow)
        network.runNetwork()

        val signedTx = future.getOrThrow()
        signedTx.verifySignaturesExcept(b.info.singleIdentity().owningKey)
    }

    @Test(expected = TransactionVerificationException.ContractRejection::class)
    fun `deal is rejected if party is sanctioned`() {
        val issuanceFlow = issuer.startFlow(IssueSanctionsListFlow.Initiator())
        network.runNetwork()
        issuanceFlow.getOrThrow()

        val updateFuture = issuer.startFlow(UpdateSanctionsListFlow.Initiator(b.info.legalIdentities.first()))
        network.runNetwork()
        updateFuture.getOrThrow()

        getSanctionsList(a, issuerParty)

        val flow = IOUIssueFlow.Initiator(1, b.info.singleIdentity(), issuerParty)
        val future = a.startFlow(flow)
        network.runNetwork()

        future.getOrThrow()
    }

    private fun getSanctionsList(node: StartedMockNode, issuerOfSanctions: Party) {
        val flow = node.startFlow(GetSanctionsListFlow.Initiator(issuerOfSanctions))
        network.runNetwork()
        flow.getOrThrow()
    }
}