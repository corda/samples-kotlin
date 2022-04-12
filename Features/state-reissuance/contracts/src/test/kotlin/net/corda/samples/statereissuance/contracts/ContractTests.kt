package net.corda.samples.statereissuance.contracts

import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import org.junit.Test

class ContractTests {
    private val ledgerServices: MockServices = MockServices(listOf("net.corda.samples.statereissuance"))
    private val alice = TestIdentity(CordaX500Name("Alice", "TestLand", "US"))
    private val bob = TestIdentity(CordaX500Name("Alice", "TestLand", "US"))

    @Test
    fun dummytest() {
    }
}
