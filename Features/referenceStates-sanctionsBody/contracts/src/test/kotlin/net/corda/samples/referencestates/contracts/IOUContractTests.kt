package net.corda.samples.referencestates.contracts

import net.corda.samples.referencestates.contracts.SanctionableIOUContract.Companion.IOU_CONTRACT_ID
import net.corda.samples.referencestates.contracts.SanctionedEntitiesContract.Companion.SANCTIONS_CONTRACT_ID
import net.corda.samples.referencestates.states.SanctionableIOUState
import net.corda.samples.referencestates.states.SanctionedEntities
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.NotaryInfo
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test

class IOUContractTests {
    val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party

    private val issuer = TestIdentity(CordaX500Name("SanctionsIssuer", "London", "GB"))
    private val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
    private val miniCorp = TestIdentity(CordaX500Name("MiniCorp", "New York", "US"))
    private val naughtyCorp = TestIdentity(CordaX500Name("NaughtyCorp", "Moscow", "RU"))

    private val ledgerServices = MockServices(
        megaCorp,
        networkParameters = testNetworkParameters(
            minimumPlatformVersion = 4,
            notaries = listOf(NotaryInfo(DUMMY_NOTARY, true))
        )
    )

    private val sanctions = SanctionedEntities(listOf(naughtyCorp.party), issuer.party)
    private val iouValue = 1

    // ========== CREATE COMMAND TESTS ==========

    @Test
    fun `transaction must include reference sanctions list command`() {
        ledgerServices.ledger {
            transaction {
                output(IOU_CONTRACT_ID, SanctionableIOUState(iouValue, miniCorp.party, megaCorp.party))
                command(
                    listOf(megaCorp.publicKey, miniCorp.publicKey),
                    SanctionableIOUContract.Commands.Create(issuer.party)
                )
                fails()
                reference(SANCTIONS_CONTRACT_ID, sanctions)
                verifies()
            }
        }
    }

    @Test
    fun `should not allow lender to be sanctioned`() {
        ledgerServices.ledger {
            transaction {
                output(IOU_CONTRACT_ID, SanctionableIOUState(iouValue, naughtyCorp.party, megaCorp.party))
                reference(SANCTIONS_CONTRACT_ID, sanctions)
                command(
                    listOf(naughtyCorp.publicKey, megaCorp.publicKey),
                    SanctionableIOUContract.Commands.Create(issuer.party)
                )
                `fails with`("The lender O=NaughtyCorp, L=Moscow, C=RU is a sanctioned entity")
            }
        }
    }

    @Test
    fun `should not allow borrower to be sanctioned`() {
        ledgerServices.ledger {
            transaction {
                output(IOU_CONTRACT_ID, SanctionableIOUState(iouValue, megaCorp.party, naughtyCorp.party))
                reference(SANCTIONS_CONTRACT_ID, sanctions)
                command(
                    listOf(megaCorp.publicKey, naughtyCorp.publicKey),
                    SanctionableIOUContract.Commands.Create(issuer.party)
                )
                `fails with`("The borrower O=NaughtyCorp, L=Moscow, C=RU is a sanctioned entity")
            }
        }
    }

    @Test
    fun `transaction must include Create command`() {
        ledgerServices.ledger {
            transaction {
                output(IOU_CONTRACT_ID, SanctionableIOUState(iouValue, miniCorp.party, megaCorp.party))
                reference(SANCTIONS_CONTRACT_ID, sanctions)
                fails()
                command(
                    listOf(megaCorp.publicKey, miniCorp.publicKey),
                    SanctionableIOUContract.Commands.Create(issuer.party)
                )
                verifies()
            }
        }
    }

    @Test
    fun `transaction must have no inputs`() {
        ledgerServices.ledger {
            transaction {
                reference(SANCTIONS_CONTRACT_ID, sanctions)
                input(IOU_CONTRACT_ID, SanctionableIOUState(iouValue, miniCorp.party, megaCorp.party))
                output(IOU_CONTRACT_ID, SanctionableIOUState(iouValue, miniCorp.party, megaCorp.party))
                command(
                    listOf(megaCorp.publicKey, miniCorp.publicKey),
                    SanctionableIOUContract.Commands.Create(issuer.party)
                )
                `fails with`("No inputs should be consumed when issuing an IOU.")
            }
        }
    }

    @Test
    fun `transaction must have one output`() {
        ledgerServices.ledger {
            transaction {
                reference(SANCTIONS_CONTRACT_ID, sanctions)
                output(IOU_CONTRACT_ID, SanctionableIOUState(iouValue, miniCorp.party, megaCorp.party))
                output(IOU_CONTRACT_ID, SanctionableIOUState(iouValue, miniCorp.party, megaCorp.party))
                command(
                    listOf(megaCorp.publicKey, miniCorp.publicKey),
                    SanctionableIOUContract.Commands.Create(issuer.party)
                )
                `fails with`("Only one output state should be created.")
            }
        }
    }

    @Test
    fun `lender must sign transaction`() {
        ledgerServices.ledger {
            transaction {
                reference(SANCTIONS_CONTRACT_ID, sanctions)
                output(IOU_CONTRACT_ID, SanctionableIOUState(iouValue, miniCorp.party, megaCorp.party))
                command(miniCorp.publicKey, SanctionableIOUContract.Commands.Create(issuer.party))
                `fails with`("All of the participants must be signers.")
            }
        }
    }

    @Test
    fun `borrower must sign transaction`() {
        ledgerServices.ledger {
            transaction {
                reference(SANCTIONS_CONTRACT_ID, sanctions)
                output(IOU_CONTRACT_ID, SanctionableIOUState(iouValue, miniCorp.party, megaCorp.party))
                command(megaCorp.publicKey, SanctionableIOUContract.Commands.Create(issuer.party))
                `fails with`("All of the participants must be signers.")
            }
        }
    }

    @Test
    fun `lender is not borrower`() {
        ledgerServices.ledger {
            transaction {
                reference(SANCTIONS_CONTRACT_ID, sanctions)
                output(IOU_CONTRACT_ID, SanctionableIOUState(iouValue, megaCorp.party, megaCorp.party))
                command(
                    listOf(megaCorp.publicKey, miniCorp.publicKey),
                    SanctionableIOUContract.Commands.Create(issuer.party)
                )
                `fails with`("The lender and the borrower cannot be the same entity.")
            }
        }
    }

    @Test
    fun `cannot create negative-value IOUs`() {
        ledgerServices.ledger {
            transaction {
                reference(SANCTIONS_CONTRACT_ID, sanctions)
                output(IOU_CONTRACT_ID, SanctionableIOUState(-1, miniCorp.party, megaCorp.party))
                command(
                    listOf(megaCorp.publicKey, miniCorp.publicKey),
                    SanctionableIOUContract.Commands.Create(issuer.party)
                )
                `fails with`("The IOU's value must be non-negative.")
            }
        }
    }

    @Test
    fun `cannot create zero-value IOUs`() {
        ledgerServices.ledger {
            transaction {
                reference(SANCTIONS_CONTRACT_ID, sanctions)
                output(IOU_CONTRACT_ID, SanctionableIOUState(0, miniCorp.party, megaCorp.party))
                command(
                    listOf(megaCorp.publicKey, miniCorp.publicKey),
                    SanctionableIOUContract.Commands.Create(issuer.party)
                )
                `fails with`("The IOU's value must be non-negative.")
            }
        }
    }

    // ========== SETTLE COMMAND TESTS ==========

    @Test
    fun `settle transaction must have one input and no outputs`() {
        val inputIOU = SanctionableIOUState(100, miniCorp.party, megaCorp.party)
        ledgerServices.ledger {
            transaction {
                input(IOU_CONTRACT_ID, inputIOU)
                command(
                    listOf(miniCorp.publicKey, megaCorp.publicKey),
                    SanctionableIOUContract.Commands.Settle(issuer.party)
                )
                verifies()
            }
        }
    }

    @Test
    fun `settle transaction cannot have outputs`() {
        val inputIOU = SanctionableIOUState(100, miniCorp.party, megaCorp.party)
        ledgerServices.ledger {
            transaction {
                input(IOU_CONTRACT_ID, inputIOU)
                output(IOU_CONTRACT_ID, inputIOU)
                command(
                    listOf(miniCorp.publicKey, megaCorp.publicKey),
                    SanctionableIOUContract.Commands.Settle(issuer.party)
                )
                `fails with`("There must be no output IOUs when settling.")
            }
        }
    }

    @Test
    fun `settle transaction must have lender signature`() {
        val inputIOU = SanctionableIOUState(100, miniCorp.party, megaCorp.party)
        ledgerServices.ledger {
            transaction {
                input(IOU_CONTRACT_ID, inputIOU)
                command(
                    listOf(megaCorp.publicKey), // Only borrower signature, missing lender
                    SanctionableIOUContract.Commands.Settle(issuer.party)
                )
                `fails with`("The lender must sign the settlement transaction.")
            }
        }
    }

    @Test
    fun `settle transaction must have borrower signature`() {
        val inputIOU = SanctionableIOUState(100, miniCorp.party, megaCorp.party)
        ledgerServices.ledger {
            transaction {
                input(IOU_CONTRACT_ID, inputIOU)
                command(
                    listOf(miniCorp.publicKey), // Only lender signature, missing borrower
                    SanctionableIOUContract.Commands.Settle(issuer.party)
                )
                `fails with`("The borrower must sign the settlement transaction.")
            }
        }
    }

    @Test
    fun `settle transaction must have exactly one input`() {
        val inputIOU1 = SanctionableIOUState(100, miniCorp.party, megaCorp.party)
        val inputIOU2 = SanctionableIOUState(200, miniCorp.party, megaCorp.party)
        ledgerServices.ledger {
            transaction {
                input(IOU_CONTRACT_ID, inputIOU1)
                input(IOU_CONTRACT_ID, inputIOU2)
                command(
                    listOf(miniCorp.publicKey, megaCorp.publicKey),
                    SanctionableIOUContract.Commands.Settle(issuer.party)
                )
                `fails with`("There must be one input IOU when settling.")
            }
        }
    }

    // ========== TRANSFER COMMAND TESTS ==========

    @Test
    fun `transfer transaction must have one input and one output`() {
        val inputIOU = SanctionableIOUState(100, miniCorp.party, megaCorp.party)
        val outputIOU = inputIOU.copy(lender = issuer.party)
        val emptySanctions = SanctionedEntities(emptyList(), issuer.party)

        ledgerServices.ledger {
            transaction {
                input(IOU_CONTRACT_ID, inputIOU)
                output(IOU_CONTRACT_ID, outputIOU)
                reference(SANCTIONS_CONTRACT_ID, emptySanctions)
                command(
                    listOf(miniCorp.publicKey, issuer.publicKey),
                    SanctionableIOUContract.Commands.Transfer(issuer.party)
                )
                verifies()
            }
        }
    }

    @Test
    fun `transfer transaction must maintain same value`() {
        val inputIOU = SanctionableIOUState(100, miniCorp.party, megaCorp.party)
        val outputIOU = SanctionableIOUState(200, issuer.party, megaCorp.party, inputIOU.linearId)
        val emptySanctions = SanctionedEntities(emptyList(), issuer.party)

        ledgerServices.ledger {
            transaction {
                input(IOU_CONTRACT_ID, inputIOU)
                output(IOU_CONTRACT_ID, outputIOU)
                reference(SANCTIONS_CONTRACT_ID, emptySanctions)
                command(
                    listOf(miniCorp.publicKey, issuer.publicKey),
                    SanctionableIOUContract.Commands.Transfer(issuer.party)
                )
                `fails with`("The IOU value must remain the same during transfer.")
            }
        }
    }

    @Test
    fun `transfer transaction must maintain same linear ID`() {
        val inputIOU = SanctionableIOUState(100, miniCorp.party, megaCorp.party)
        val outputIOU = SanctionableIOUState(100, issuer.party, megaCorp.party, UniqueIdentifier())
        val emptySanctions = SanctionedEntities(emptyList(), issuer.party)

        ledgerServices.ledger {
            transaction {
                input(IOU_CONTRACT_ID, inputIOU)
                output(IOU_CONTRACT_ID, outputIOU)
                reference(SANCTIONS_CONTRACT_ID, emptySanctions)
                command(
                    listOf(miniCorp.publicKey, issuer.publicKey),
                    SanctionableIOUContract.Commands.Transfer(issuer.party)
                )
                `fails with`("The linear ID must remain the same during transfer.")
            }
        }
    }

    @Test
    fun `transfer transaction must maintain same borrower`() {
        val inputIOU = SanctionableIOUState(100, miniCorp.party, megaCorp.party)
        val outputIOU = inputIOU.copy(lender = issuer.party, borrower = issuer.party)
        val emptySanctions = SanctionedEntities(emptyList(), issuer.party)

        ledgerServices.ledger {
            transaction {
                input(IOU_CONTRACT_ID, inputIOU)
                output(IOU_CONTRACT_ID, outputIOU)
                reference(SANCTIONS_CONTRACT_ID, emptySanctions)
                command(
                    listOf(miniCorp.publicKey, issuer.publicKey),
                    SanctionableIOUContract.Commands.Transfer(issuer.party)
                )
                `fails with`("The borrower must remain the same during transfer.")
            }
        }
    }

    @Test
    fun `transfer transaction must change lender`() {
        val inputIOU = SanctionableIOUState(100, miniCorp.party, megaCorp.party)
        val outputIOU = inputIOU.copy() // Same lender
        val emptySanctions = SanctionedEntities(emptyList(), issuer.party)

        ledgerServices.ledger {
            transaction {
                input(IOU_CONTRACT_ID, inputIOU)
                output(IOU_CONTRACT_ID, outputIOU)
                reference(SANCTIONS_CONTRACT_ID, emptySanctions)
                command(
                    listOf(miniCorp.publicKey, issuer.publicKey),
                    SanctionableIOUContract.Commands.Transfer(issuer.party)
                )
                `fails with`("The lender must change during transfer.")
            }
        }
    }

    @Test
    fun `transfer transaction cannot transfer to sanctioned entity`() {
        val inputIOU = SanctionableIOUState(100, miniCorp.party, megaCorp.party)
        val outputIOU = inputIOU.copy(lender = naughtyCorp.party)

        ledgerServices.ledger {
            transaction {
                input(IOU_CONTRACT_ID, inputIOU)
                output(IOU_CONTRACT_ID, outputIOU)
                reference(SANCTIONS_CONTRACT_ID, sanctions)
                command(
                    listOf(miniCorp.publicKey, naughtyCorp.publicKey),
                    SanctionableIOUContract.Commands.Transfer(issuer.party)
                )
                `fails with`("The new lender O=NaughtyCorp, L=Moscow, C=RU is a sanctioned entity")
            }
        }
    }

    @Test
    fun `transfer transaction must have current and new lender signatures`() {
        val inputIOU = SanctionableIOUState(100, miniCorp.party, megaCorp.party)
        val outputIOU = inputIOU.copy(lender = issuer.party)
        val emptySanctions = SanctionedEntities(emptyList(), issuer.party)

        ledgerServices.ledger {
            transaction {
                input(IOU_CONTRACT_ID, inputIOU)
                output(IOU_CONTRACT_ID, outputIOU)
                reference(SANCTIONS_CONTRACT_ID, emptySanctions)
                command(
                    listOf(miniCorp.publicKey), // Missing new lender signature
                    SanctionableIOUContract.Commands.Transfer(issuer.party)
                )
                `fails with`("The new lender must sign the transfer transaction.")
            }
        }
    }

    @Test
    fun `transfer transaction must require reference sanctions list`() {
        val inputIOU = SanctionableIOUState(100, miniCorp.party, megaCorp.party)
        val outputIOU = inputIOU.copy(lender = issuer.party)

        ledgerServices.ledger {
            transaction {
                input(IOU_CONTRACT_ID, inputIOU)
                output(IOU_CONTRACT_ID, outputIOU)
                // Missing reference sanctions list
                command(
                    listOf(miniCorp.publicKey, issuer.publicKey),
                    SanctionableIOUContract.Commands.Transfer(issuer.party)
                )
                `fails with`("All transactions require a list of sanctioned entities")
            }
        }
    }

    // ========== GENERAL TESTS ==========

    @Test
    fun `valid create transaction passes`() {
        ledgerServices.ledger {
            transaction {
                reference(SANCTIONS_CONTRACT_ID, sanctions)
                output(IOU_CONTRACT_ID, SanctionableIOUState(100, miniCorp.party, megaCorp.party))
                command(
                    listOf(megaCorp.publicKey, miniCorp.publicKey),
                    SanctionableIOUContract.Commands.Create(issuer.party)
                )
                verifies()
            }
        }
    }

    @Test
    fun `valid settle transaction passes`() {
        val inputIOU = SanctionableIOUState(100, miniCorp.party, megaCorp.party)
        ledgerServices.ledger {
            transaction {
                input(IOU_CONTRACT_ID, inputIOU)
                command(
                    listOf(miniCorp.publicKey, megaCorp.publicKey),
                    SanctionableIOUContract.Commands.Settle(issuer.party)
                )
                verifies()
            }
        }
    }

    @Test
    fun `valid transfer transaction passes`() {
        val inputIOU = SanctionableIOUState(100, miniCorp.party, megaCorp.party)
        val outputIOU = inputIOU.copy(lender = issuer.party)
        val emptySanctions = SanctionedEntities(emptyList(), issuer.party)

        ledgerServices.ledger {
            transaction {
                input(IOU_CONTRACT_ID, inputIOU)
                output(IOU_CONTRACT_ID, outputIOU)
                reference(SANCTIONS_CONTRACT_ID, emptySanctions)
                command(
                    listOf(miniCorp.publicKey, issuer.publicKey),
                    SanctionableIOUContract.Commands.Transfer(issuer.party)
                )
                verifies()
            }
        }
    }
}