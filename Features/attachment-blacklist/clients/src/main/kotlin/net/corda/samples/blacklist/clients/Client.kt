package net.corda.samples.blacklist.clients

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.crypto.SecureHash
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.NetworkHostAndPort.Companion.parse
import net.corda.core.utilities.loggerFor
import net.corda.samples.blacklist.BLACKLISTED_PARTIES
import net.corda.samples.blacklist.BLACKLIST_JAR_NAME
import net.corda.samples.blacklist.contracts.AgreementContract.Companion.BLACKLIST_FILE_NAME
import org.slf4j.Logger
import java.io.FileNotFoundException
import java.util.jar.JarInputStream

/**
 * Uploads the jar of blacklisted counterparties with whom agreements cannot be struck to the node.
 */
fun main(args: Array<String>) {
    UploadBlacklistClient().main(args)
}

private class UploadBlacklistClient {
    companion object {
        val logger: Logger = loggerFor<UploadBlacklistClient>()
    }

    fun main(args: Array<String>) {
        require(args.isNotEmpty()) { "Usage: uploadBlacklist <node address 1> <node address 2> ..." }
        args.forEach { arg ->
            val nodeAddress = parse(arg)
            val rpcConnection = CordaRPCClient(nodeAddress).start("user1", "test")
            val proxy = rpcConnection.proxy

            val attachmentHash = uploadAttachment(proxy, BLACKLIST_JAR_NAME)
            logger.info("Blacklist uploaded to node at $nodeAddress")

            val attachmentJar = downloadAttachment(proxy, attachmentHash)
            logger.info("Blacklist downloaded from node at $nodeAddress")

            checkAttachment(attachmentJar, BLACKLIST_FILE_NAME, BLACKLISTED_PARTIES)
            logger.info("Attachment contents checked on node at $nodeAddress")

            rpcConnection.notifyServerAndClose()
        }
    }

    /**
     * Uploads the attachment at [attachmentPath] to the node.
     */
    @Suppress("SameParameterValue")
    private fun uploadAttachment(proxy: CordaRPCOps, attachmentPath: String): SecureHash {
        val attachmentUploadInputStream = javaClass.classLoader.getResourceAsStream(attachmentPath)
            ?: throw FileNotFoundException("$attachmentPath not found")
        return proxy.uploadAttachment(attachmentUploadInputStream)
    }
}

/**
 * Downloads the attachment with hash [attachmentHash] from the node.
 */
private fun downloadAttachment(proxy: CordaRPCOps, attachmentHash: SecureHash): JarInputStream {
    val attachmentDownloadInputStream = proxy.openAttachment(attachmentHash)
    return JarInputStream(attachmentDownloadInputStream)
}

/**
 * Checks the [expectedFileName] and [expectedContents] of the downloaded [attachmentJar].
 */
@Suppress("SameParameterValue")
private fun checkAttachment(attachmentJar: JarInputStream, expectedFileName: String, expectedContents: List<String>) {
    var name: String? = null
    while (name != expectedFileName) {
        val jarEntry = attachmentJar.nextEntry ?: throw FileNotFoundException("$expectedFileName not found")
        name = jarEntry.name
    }

    val contents = attachmentJar.bufferedReader().readLines()

    if (contents != expectedContents) {
        throw IllegalArgumentException("Downloaded JAR did not have the expected contents.")
    }
}
