package net.corda.samples.sendfile.states

import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import org.junit.Assert.assertEquals
import org.junit.Test

class InvoiceStateTests {
    private val a = TestIdentity(CordaX500Name("Alice", "", "GB")).party
    private val b = TestIdentity(CordaX500Name("Bob", "", "GB")).party
    private val STRINGID = "StringID that is unique"

    @Test
    fun constructorTest() {
        val (invoiceAttachmentID) = InvoiceState(STRINGID, listOf(a, b))
        assertEquals(STRINGID, invoiceAttachmentID)
    }
}
