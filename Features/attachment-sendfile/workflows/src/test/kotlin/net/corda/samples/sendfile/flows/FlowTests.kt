package net.corda.samples.sendfile.flows

import java.io.FileNotFoundException
import java.nio.file.Paths
import net.corda.core.identity.CordaX500Name
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FlowTests {
    private val network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
        TestCordapp.findCordapp("net.corda.samples.sendfile.contracts"),
        TestCordapp.findCordapp("net.corda.samples.sendfile.flows")
    ),
            notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary","London","GB")))
    ))
    private val a = network.createNode()
    private val b = network.createNode()

    private val testZip = Paths.get(
        (this::class.java.classLoader.getResource("test.zip") ?: throw FileNotFoundException("test.zip not found")
    ).toURI()).toAbsolutePath()

    init {
        listOf(a, b).forEach {
        }
    }

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()

    //Test #1 check attachments list has more than one element
    //one for contract attachment, another one for attached zip
    //@TODO Make sure change the file path in the SendAttachment flow to "../test.zip" for passing the unit test.
    //because the unit test are in a different working directory than the running node.
    @Test
    fun `attachment list has more than one element`() {
        val future = a.startFlow(SendAttachment(b.info.legalIdentities.first(), testZip.toString()))
        network.runNetwork()
        val ptx = future.get()
        assertTrue(ptx.tx.attachments.size > 1)
    }

    //Test #2 test successful download of the attachment by the receiving node.
    @Test
    fun `attachment downloaded by buyer`() {
        val future = a.startFlow(SendAttachment(b.info.legalIdentities.first(), testZip.toString()))
        network.runNetwork()
        val future1 = b.startFlow(DownloadAttachment(a.info.legalIdentities.first(), "file.zip"))
        network.runNetwork()
    }
}

