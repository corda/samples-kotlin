// File: workflows/src/test/kotlin/net/corda/samples/referencestates/flows/IOUSettleFlowTests.kt

package net.corda.samples.referencestates.flows

import net.corda.samples.referencestates.states.SanctionableIOUState
import net.corda.core.identity.CordaX500Name
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IOUSettleFlowTests {
    lateinit var network: MockNetwork
    lateinit var a: StartedMockNode
    lateinit var b: StartedMockNode
    lateinit var c: StartedMockNode
    lateinit var issuer: StartedMockNode

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

        // Register ALL flow responders on ALL nodes
        listOf(a, b, c, issuer).forEach { node ->
            node.registerInitiatedFlow(IOUIssueFlowAcceptor::class.java)
            node.registerInitiatedFlow(IOUSettleFlowAcceptor::class.java)
            node.registerInitiatedFlow(IOUTransferFlowAcceptor::class.java)
            node.registerInitiatedFlow(GetSanctionsListFlowAcceptor::class.java)
        }

        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    fun `IOU can be settled successfully by lender`() {
        // Issue sanctions list
        val issuanceFlow = issuer.startFlow(IssueSanctionsListFlow())
        network.runNetwork()
        issuanceFlow.getOrThrow()

        // Get sanctions list on node A
        getSanctionsList(a, issuer.info.singleIdentity())

        // Issue IOU (A lends to B)
        val issueFlow = IOUIssueFlow(100, b.info.singleIdentity(), issuer.info.singleIdentity())
        val issueFuture = a.startFlow(issueFlow)
        network.runNetwork()
        val issueTx = issueFuture.getOrThrow()

        val iouState = issueTx.tx.outputsOfType<SanctionableIOUState>().single()

        // Settle IOU from lender side (A)
        val settleFlow = IOUSettleFlow(iouState.linearId, issuer.info.singleIdentity())
        val settleFuture = a.startFlow(settleFlow)
        network.runNetwork()
        val settleTx = settleFuture.getOrThrow()

        // Verify settlement
        assertTrue(settleTx.tx.inputs.isNotEmpty(), "Settlement transaction should have inputs")
        assertTrue(settleTx.tx.outputsOfType<SanctionableIOUState>().isEmpty(),
            "Settlement transaction should have no IOU outputs")

        // Verify IOU is consumed from both vaults
        val aIOUs = a.services.vaultService.queryBy(SanctionableIOUState::class.java).states
        val bIOUs = b.services.vaultService.queryBy(SanctionableIOUState::class.java).states
        assertTrue(aIOUs.isEmpty(), "Node A should have no unconsumed IOUs")
        assertTrue(bIOUs.isEmpty(), "Node B should have no unconsumed IOUs")
    }

    @Test
    fun `IOU can be settled successfully by borrower`() {
        // Issue sanctions list
        val issuanceFlow = issuer.startFlow(IssueSanctionsListFlow())
        network.runNetwork()
        issuanceFlow.getOrThrow()

        // Get sanctions list on node A
        getSanctionsList(a, issuer.info.singleIdentity())

        // Issue IOU (A lends to B)
        val issueFlow = IOUIssueFlow(100, b.info.singleIdentity(), issuer.info.singleIdentity())
        val issueFuture = a.startFlow(issueFlow)
        network.runNetwork()
        val issueTx = issueFuture.getOrThrow()

        val iouState = issueTx.tx.outputsOfType<SanctionableIOUState>().single()

        // Settle IOU from borrower side (B)
        val settleFlow = IOUSettleFlow(iouState.linearId, issuer.info.singleIdentity())
        val settleFuture = b.startFlow(settleFlow)
        network.runNetwork()
        val settleTx = settleFuture.getOrThrow()

        // Verify settlement
        assertTrue(settleTx.tx.inputs.isNotEmpty(), "Settlement transaction should have inputs")
        assertTrue(settleTx.tx.outputsOfType<SanctionableIOUState>().isEmpty(),
            "Settlement transaction should have no IOU outputs")
    }

    @Test
    fun `IOU can be transferred and then settled`() {
        // Issue sanctions list
        val issuanceFlow = issuer.startFlow(IssueSanctionsListFlow())
        network.runNetwork()
        issuanceFlow.getOrThrow()

        // Get sanctions list on ALL nodes that will participate
        getSanctionsList(a, issuer.info.singleIdentity())
        getSanctionsList(b, issuer.info.singleIdentity())
        getSanctionsList(c, issuer.info.singleIdentity())

        // Issue IOU (A lends to B)
        val issueFlow = IOUIssueFlow(100, b.info.singleIdentity(), issuer.info.singleIdentity())
        val issueFuture = a.startFlow(issueFlow)
        network.runNetwork()
        val issueTx = issueFuture.getOrThrow()

        val originalIOU = issueTx.tx.outputsOfType<SanctionableIOUState>().single()
        println("Original IOU: lender=${originalIOU.lender.name}, borrower=${originalIOU.borrower.name}, linearId=${originalIOU.linearId}")

        // Transfer IOU from A to C
        val transferFlow = IOUTransferFlow(
            originalIOU.linearId,
            c.info.singleIdentity(),
            issuer.info.singleIdentity()
        )
        val transferFuture = a.startFlow(transferFlow)
        network.runNetwork()
        val transferTx = transferFuture.getOrThrow()

        // Verify transfer
        val outputIOUs = transferTx.tx.outputsOfType<SanctionableIOUState>()
        assertEquals(1, outputIOUs.size)
        val transferredIOU = outputIOUs.single()
        assertEquals(c.info.singleIdentity(), transferredIOU.lender)
        assertEquals(b.info.singleIdentity(), transferredIOU.borrower)
        assertEquals(originalIOU.linearId, transferredIOU.linearId)

        println("Transferred IOU: lender=${transferredIOU.lender.name}, borrower=${transferredIOU.borrower.name}, linearId=${transferredIOU.linearId}")

        // Verify the IOU is in C's vault
        val cIOUs = c.services.vaultService.queryBy(SanctionableIOUState::class.java).states
        assertEquals(1, cIOUs.size)
        assertEquals(transferredIOU.linearId, cIOUs.single().state.data.linearId)

        // Now settle from new lender (C)
        val settleFlow = IOUSettleFlow(transferredIOU.linearId, issuer.info.singleIdentity())
        val settleFuture = c.startFlow(settleFlow)
        network.runNetwork()
        val settleTx = settleFuture.getOrThrow()

        // Verify settlement
        assertTrue(settleTx.tx.inputs.isNotEmpty(), "Settlement transaction should have inputs")
        assertTrue(settleTx.tx.outputsOfType<SanctionableIOUState>().isEmpty(),
            "Settlement transaction should have no IOU outputs")

        // Verify IOU is consumed from all vaults
        val aIOUsAfterSettle = a.services.vaultService.queryBy(SanctionableIOUState::class.java).states
        val bIOUsAfterSettle = b.services.vaultService.queryBy(SanctionableIOUState::class.java).states
        val cIOUsAfterSettle = c.services.vaultService.queryBy(SanctionableIOUState::class.java).states

        assertTrue(aIOUsAfterSettle.isEmpty(), "Node A should have no unconsumed IOUs after settlement")
        assertTrue(bIOUsAfterSettle.isEmpty(), "Node B should have no unconsumed IOUs after settlement")
        assertTrue(cIOUsAfterSettle.isEmpty(), "Node C should have no unconsumed IOUs after settlement")
    }

    private fun getSanctionsList(node: StartedMockNode, issuerOfSanctions: net.corda.core.identity.Party) {
        val flow = node.startFlow(GetSanctionsListFlow(issuerOfSanctions))
        network.runNetwork()
        flow.getOrThrow()
    }
}