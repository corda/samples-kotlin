package net.corda.samples.negotiation

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.NetworkParameters
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.samples.negotiation.flows.AcceptanceFlow
import net.corda.samples.negotiation.flows.ModificationFlow
import net.corda.samples.negotiation.flows.ProposalFlow
import net.corda.samples.negotiation.states.ProposalState
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.TestCordapp
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNetwork.Companion.createKeyRotationMockNode
import net.corda.testing.node.internal.TestCordappInternal
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Before
import java.time.Instant

/**
 * A two-node Corda Enterprise mock network for the key rotation tests, plus one-line helpers to run the sample's flows.
 *
 * The nodes are created with `InternalMockNetwork.createKeyRotationMockNode`, which is what the Corda Enterprise
 * repository's own key rotation tests use: such a node runs the Enterprise versions of `CollectSignaturesFlow` and
 * `FinalityFlow`, exactly like a real Enterprise node, and those are the flows that know how to collect and verify a
 * signature made with a rotated key. The public `MockNetwork` creates plain nodes running the Corda OS flows, which do not.
 */
abstract class KeyRotationTestBase {
    protected lateinit var network: InternalMockNetwork
    protected lateinit var a: TestStartedNode
    protected lateinit var b: TestStartedNode

    @Before
    fun setUp() {
        network = InternalMockNetwork(
                // The sample's flows look the notary up by this name.
                notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary", "London", "GB"))),
                cordappsForAllNodes = setOf(
                        TestCordapp.findCordapp("net.corda.samples.negotiation.flows") as TestCordappInternal,
                        TestCordapp.findCordapp("net.corda.samples.negotiation.contracts") as TestCordappInternal
                ),
                initialNetworkParameters = NetworkParameters(
                        minimumPlatformVersion = MINIMUM_PLATFORM_VERSION,
                        notaries = emptyList(),
                        maxMessageSize = 10485760,
                        maxTransactionSize = 10485760 * 50,
                        modifiedTime = Instant.now(),
                        epoch = 1,
                        whitelistedContractImplementations = emptyMap()
                ),
                defaultFactory = ::createKeyRotationMockNode
        )

        a = network.createPartyNode(CordaX500Name("Node A", "London", "GB"))
        b = network.createPartyNode(CordaX500Name("Node B", "New York", "US"))
        registerResponders(a)
        registerResponders(b)
        network.runNetwork()
    }

    @After
    fun tearDown() {
        if (::network.isInitialized) {
            network.stopNodes()
        }
    }

    /**
     * Rotates the node's legal identity key the way the Corda Enterprise cross-provider key rotation does: a new key is
     * generated, the OLD key signs the NEW public key (that signature is the rotation proof) and the node restarts on the new
     * key with the proof in its identity service. Use the returned node from here on; the one passed in is gone.
     */
    protected fun rotate(node: TestStartedNode): TestStartedNode {
        val rotated = network.rotateKey(node, null)
        registerResponders(rotated)
        return rotated
    }

    protected fun identityOf(node: TestStartedNode): Party = node.info.legalIdentities.first()

    // --- The sample's flows, one call each -------------------------------------------------------------------------------

    /** `proposer` proposes to `counterparty`; the proposer is the buyer. Only the proposee can then modify or accept. */
    protected fun propose(proposer: TestStartedNode, amount: Int, counterparty: Party): UniqueIdentifier =
            run(proposer, ProposalFlow.Initiator(true, amount, counterparty))

    protected fun modify(node: TestStartedNode, proposalId: UniqueIdentifier, newAmount: Int): SignedTransaction =
            run(node, ModificationFlow.Initiator(proposalId, newAmount))

    protected fun accept(node: TestStartedNode, proposalId: UniqueIdentifier): SignedTransaction =
            run(node, AcceptanceFlow.Initiator(proposalId))

    /** The unconsumed proposal with this id, as recorded in the node's vault. */
    protected fun proposalOf(node: TestStartedNode, proposalId: UniqueIdentifier): ProposalState {
        val criteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(proposalId), status = Vault.StateStatus.UNCONSUMED)
        return node.database.transaction {
            node.services.vaultService.queryBy<ProposalState>(criteria).states.single().state.data
        }
    }

    private fun <T> run(node: TestStartedNode, flow: FlowLogic<T>): T {
        val handle = node.services.startFlow(flow)
        network.runNetwork()
        return handle.resultFuture.getOrThrow()
    }

    private fun registerResponders(node: TestStartedNode) {
        RESPONDER_FLOWS.forEach { node.registerInitiatedFlow(it) }
    }

    companion object {
        /** Corda Enterprise 4.15 is platform version 170; key rotation proofs need at least that on the network. */
        private const val MINIMUM_PLATFORM_VERSION = 170

        private val RESPONDER_FLOWS = listOf(
                ProposalFlow.Responder::class.java,
                AcceptanceFlow.Responder::class.java,
                ModificationFlow.Responder::class.java
        )
    }
}
