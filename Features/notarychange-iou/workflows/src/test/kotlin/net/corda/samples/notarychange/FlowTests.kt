package net.corda.samples.notarychange

import net.corda.core.contracts.UniqueIdentifier.Companion.fromString
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.samples.notarychange.flows.IssueFlow
import net.corda.samples.notarychange.flows.SettleFlow
import net.corda.samples.notarychange.flows.SwitchNotaryFlow
import net.corda.testing.node.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.rules.TestRule
import java.util.concurrent.ExecutionException
import kotlin.test.assertFailsWith

class FlowTests {
    private lateinit var network: MockNetwork
    private lateinit var a: StartedMockNode
    private lateinit var b: StartedMockNode

    @Rule
    @JvmField
    val exception: TestRule = ExpectedException.none()

    @Before
    fun setup() {
        network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("net.corda.samples.notarychange.contracts"),
                TestCordapp.findCordapp("net.corda.samples.notarychange.flows")
        ), notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("NotaryA", "London", "GB")),
                MockNetworkNotarySpec(CordaX500Name("NotaryB", "Toronto", "CA")))))
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
    fun noNotaryChangesExpectToFail() {
        val issueflow = IssueFlow.Initiator(20, b.info.legalIdentities[0])
        val future = a.startFlow(issueflow)
        network.runNetwork()
        val returnString = future.get()
        println("\n----------")
        println(returnString)
        val id = returnString.substring(returnString.indexOf("linearId: ") + 10)
        println(id)
        val settleflow = SettleFlow.Initiator(fromString(id),
                network.notaryNodes[1].info.legalIdentities[0])
        val future2 = b.startFlow(settleflow)
        network.runNetwork()
        assertFailsWith<IllegalArgumentException> { future2.getOrThrow() }
    }

    @Test
    @Throws(ExecutionException::class, InterruptedException::class)
    fun condunctNotaryChanges() {
        val issueflow = IssueFlow.Initiator(20, b.info.legalIdentities[0])
        val future = a.startFlow(issueflow)
        network.runNetwork()
        val returnString = future.get()
        println("\n----------")
        println(returnString)
        val id = returnString.substring(returnString.indexOf("linearId: ") + 10)
        println(id)

        //notary change
        val notarychange = SwitchNotaryFlow(fromString(id),
                network.notaryNodes[1].info.legalIdentities[0])
        b.startFlow(notarychange)
        network.runNetwork()

        //settle
        val settleflow = SettleFlow.Initiator(fromString(id),
                network.notaryNodes[1].info.legalIdentities[0])
        val future2 = b.startFlow(settleflow)
        network.runNetwork()
        future2.get()
    }
}
