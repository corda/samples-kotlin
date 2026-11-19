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
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import java.time.Instant

/**
 * A two-node Corda Enterprise mock network for the key rotation tests, plus one-line helpers to run the sample's flows.
 *
 * The network is created with `MockNetworkParameters.enterpriseNodes` set to true, so every node runs the Enterprise
 * versions of `CollectSignaturesFlow` and `FinalityFlow`, exactly like a real Corda Enterprise node; those are the
 * flows that know how to collect and verify a signature made with a rotated key. `MockNetwork.rotateKey` rotates a
 * node's legal identity key the way cross-provider key rotation does.
 */
abstract class KeyRotationTestBase {
    protected lateinit var network: MockNetwork
    protected lateinit var a: StartedMockNode
    protected lateinit var b: StartedMockNode

    @Before
    fun setUp() {
        val networkParameters = NetworkParameters(
                minimumPlatformVersion = MINIMUM_PLATFORM_VERSION,
                notaries = emptyList(),
                maxMessageSize = 10485760,
                maxTransactionSize = 10485760 * 50,
                modifiedTime = Instant.now(),
                epoch = 1,
                whitelistedContractImplementations = emptyMap()
        )

        network = MockNetwork(MockNetworkParameters()
                .withNotarySpecs(listOf(MockNetworkNotarySpec(CordaX500Name.parse("O=Notary,L=London,C=GB"))))
                .withNetworkParameters(networkParameters)
                .withCordappsForAllNodes(listOf(
                        TestCordapp.findCordapp("net.corda.samples.negotiation.flows"),
                        TestCordapp.findCordapp("net.corda.samples.negotiation.contracts")))
                .withEnterpriseNodes(true))

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
    protected fun rotate(node: StartedMockNode): StartedMockNode {
        val rotated = network.rotateKey(node, null)
        registerResponders(rotated)
        return rotated
    }

    protected fun identityOf(node: StartedMockNode): Party = node.info.legalIdentities.first()

    // --- The sample's flows, one call each -------------------------------------------------------------------------------

    /** `proposer` proposes to `counterparty`; the proposer is the buyer. Only the proposee can then modify or accept. */
    protected fun propose(proposer: StartedMockNode, amount: Int, counterparty: Party): UniqueIdentifier =
            run(proposer, ProposalFlow.Initiator(true, amount, counterparty))

    protected fun modify(node: StartedMockNode, proposalId: UniqueIdentifier, newAmount: Int): SignedTransaction =
            run(node, ModificationFlow.Initiator(proposalId, newAmount))

    protected fun accept(node: StartedMockNode, proposalId: UniqueIdentifier): SignedTransaction =
            run(node, AcceptanceFlow.Initiator(proposalId))

    /** The unconsumed proposal with this id, as recorded in the node's vault. */
    protected fun proposalOf(node: StartedMockNode, proposalId: UniqueIdentifier): ProposalState {
        val criteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(proposalId), status = Vault.StateStatus.UNCONSUMED)
        return node.transaction {
            node.services.vaultService.queryBy<ProposalState>(criteria).states.single().state.data
        }
    }

    private fun <T> run(node: StartedMockNode, flow: FlowLogic<T>): T {
        val result = node.startFlow(flow)
        network.runNetwork()
        return result.getOrThrow()
    }

    private fun registerResponders(node: StartedMockNode) {
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
