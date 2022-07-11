package net.corda.samples.obligation

import net.corda.core.contracts.Command
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.PrivacySalt
import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.createComponentGroups
import net.corda.core.transactions.WireTransaction
import net.corda.samples.obligation.contract.DeterministicInstructionTests
import net.corda.testing.core.TestIdentity
import java.math.BigInteger

val ALICE = TestIdentity(CordaX500Name(organisation = "Alice", locality = "TestLand", country = "US"))
val BOB = TestIdentity(CordaX500Name(organisation = "Bob", locality = "TestCity", country = "US"))
var CHARLIE = TestIdentity(CordaX500Name(organisation = "Charlie", locality = "TestVillage", country = "US"))
val MINICORP = TestIdentity(CordaX500Name(organisation = "MiniCorp", locality = "MiniLand", country = "US"))
val MEGACORP = TestIdentity(CordaX500Name(organisation = "MegaCorp", locality = "MiniLand", country = "US"))
val DUMMY = TestIdentity(CordaX500Name(organisation = "Dummy", locality = "FakeLand", country = "US"))


fun WireTransaction.toDeterministicPublish() = this.let { tx->
    val instructions = """ 
                        The true sign of intelligence is not knowledge but imagination.
                    """.trimIndent()
    val salt = PrivacySalt(instructions.toByteArray(Charsets.US_ASCII))

    if (tx.inputs.isNotEmpty() || tx.commands.size > 1) //cba with the rest
        throw IllegalStateException("Trying to convert incompatible tx")

    WireTransaction(
        createComponentGroups(
            emptyList(),
            tx.outputs,
            tx.commands,
            tx.attachments,
            null,
            null,
            emptyList(),
            null
        ),
        privacySalt = salt
    )
}