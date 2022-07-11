package net.corda.samples.obligation.contract

import net.corda.core.contracts.*
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.createComponentGroups
import net.corda.core.internal.unspecifiedCountry
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.samples.obligation.toDeterministicPublish
import net.corda.testing.internal.withTestSerializationEnvIfNotSet
import net.corda.testing.node.MockServices
import org.junit.Test
import java.math.BigInteger

class DeterministicInstructionTests {


    class SuperBasicExample {
        private var ledgerServices = MockServices(listOf("net.corda.samples.obligation.contract"))

        @BelongsToContract(OneTimeUseContract::class)
        class OneTimeUseInstructionInsidePrivacySalt : ContractState {
            override val participants: List<AbstractParty> = emptyList()
        }

        class OneTimeUseContract : Contract {
            companion object {
                @JvmStatic
                val CONTRACT_ID = "net.corda.samples.obligation.contract.DeterministicPublish.OneTimeUseContract"
            }

            interface Commands : CommandData {
                class Publish : TypeOnlyCommandData(), Commands
                class Burn : TypeOnlyCommandData(), Commands
            }


            override fun verify(tx: LedgerTransaction) {
                val command = tx.commands.requireSingleCommand<Commands>()
                when (command.value) {
                    is Commands.Publish -> {
                        val sharedCommonPair =
                            Crypto.deriveKeyPairFromEntropy(Crypto.DEFAULT_SIGNATURE_SCHEME, BigInteger.valueOf(42))
                        val instructions = """ 
                        The true sign of intelligence is not knowledge but imagination.
                    """.trimIndent()
                        val salt = PrivacySalt(instructions.toByteArray(Charsets.US_ASCII))


                        val recalculatedDeterministicTx = WireTransaction(
                            createComponentGroups(
                                emptyList(),
                                tx.outputs,
                                listOf(Command(Commands.Publish(), sharedCommonPair.public)),
                                tx.attachments.map { it.id },
                                null,
                                null,
                                emptyList(),
                                null
                            ),
                            privacySalt = salt
                        )

                        require(recalculatedDeterministicTx.id == tx.id) {
                            "Transaction roots should match. The transaction attempting to issue one time use fact is cheeky!"
                        }
                    }
                    is Commands.Burn -> {
                        require(tx.inputs.size == 1)
                        require(tx.outputs.isEmpty())

                        require(tx.getInput(0) is OneTimeUseInstructionInsidePrivacySalt)
                    }
                    else -> {
                        require(false) { "Hmpf" }
                    }
                }
            }
        }


        @Test
        fun `It's possible to create a deterministic transaction, instruction in salt version`() {

            val instructions = """ 
            The true sign of intelligence is not knowledge but imagination.
        """.trimIndent()


            val notaryPair = Crypto.deriveKeyPairFromEntropy(Crypto.DEFAULT_SIGNATURE_SCHEME, BigInteger.valueOf(42))
            val name =
                CordaX500Name("ledger notary", notaryPair.public.toStringShort(), CordaX500Name.unspecifiedCountry)
            val notary = Party(name, notaryPair.public)
            val sharedCommonPair =
                Crypto.deriveKeyPairFromEntropy(Crypto.DEFAULT_SIGNATURE_SCHEME, BigInteger.valueOf(42))

            val salt = PrivacySalt(instructions.toByteArray(Charsets.US_ASCII))


            withTestSerializationEnvIfNotSet {
                val deterministicTx = TransactionBuilder(notary = notary, privacySalt = salt)
                    .addOutputState(
                        OneTimeUseInstructionInsidePrivacySalt(),
                        constraint = AlwaysAcceptAttachmentConstraint
                    )
                    .addCommand(OneTimeUseContract.Commands.Publish(), sharedCommonPair.public)
                    .toWireTransaction(ledgerServices).toDeterministicPublish()

                deterministicTx.toLedgerTransaction(ledgerServices).verify()
            }
        }
    }

    class ExampleWithInstruction {
        private var ledgerServices = MockServices(listOf("net.corda.samples.obligation.contract"))



        @Test
        fun `Publish an actual signed instruction`() {

        }
    }
}