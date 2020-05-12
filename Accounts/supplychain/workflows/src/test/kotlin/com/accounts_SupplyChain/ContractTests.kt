package com.accounts_SupplyChain

import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import org.junit.Test

class ContractTests {
    private val ledgerServices = MockServices(listOf("com.accounts_SupplyChain.contracts")) //listOf("com.accounts_SupplyChain.contracts")
    private val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
    private val miniCorp = TestIdentity(CordaX500Name("MiniCorp", "New York", "US"))
    private val loanValue = 1


    @Test
    fun `transaction must have no inputs`() {


    }
}