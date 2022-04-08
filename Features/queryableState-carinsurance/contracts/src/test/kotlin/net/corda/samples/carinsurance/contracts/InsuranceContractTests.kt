package net.corda.samples.carinsurance.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.identity.CordaX500Name
import net.corda.samples.carinsurance.states.Claim
import net.corda.samples.carinsurance.states.InsuranceState
import net.corda.samples.carinsurance.states.VehicleDetail
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Assert.assertTrue
import org.junit.Test

class InsuranceContractTests {
    // A pre-defined dummy command.
    interface Commands : CommandData {
        class DummyCommand : TypeOnlyCommandData(), Commands
    }

    private val ledgerServices = MockServices(
            listOf("net.corda.samples.carinsurance.contracts")
    )
    private val a = TestIdentity(CordaX500Name("Alice", "", "GB"))
    private val b = TestIdentity(CordaX500Name("Bob", "", "GB"))
    private val registrationNumber = "registration number: 2ds9Fvk"
    private val chassisNum = "chassis# aedl3sc"
    private val make = "Toyota"
    private val model = "Corolla"
    private val variant = "SE"
    private val color = "hot rod beige"
    private val fuelType = "regular"
    private val vd = VehicleDetail(
            registrationNumber,
            chassisNum,
            make,
            model,
            variant,
            color,
            fuelType)
    private val desc = "claim description: my car was hit by a blockchain"
    private val claimNumber = "B-132022"
    private val claimAmount = 3000
    private val c = Claim(claimNumber, desc, claimAmount)

    // in this test scenario, alice is our insurer.
    private val policyNum = "R3-Policy-A4byCd"
    private val insuredValue = 100000L
    private val duration = 50
    private val premium = 5
    private val insurer = a.party
    private val insuree = b.party
    private val st = InsuranceState(
            policyNum,
            insuredValue,
            duration,
            premium,
            insurer,
            insuree,
            vd,
            listOf(c))

    @Test
    fun contractImplementsContract() {
        assertTrue(InsuranceContract() is Contract)
    }

    @Test
    fun contractRequiresOneCommandInTheTransaction() {
        ledgerServices.ledger {
            transaction {
                output(InsuranceContract.ID, st)
                // Has two commands, will fail.
                command(listOf(a.publicKey, b.publicKey), InsuranceContract.Commands.IssueInsurance())
                command(listOf(a.publicKey, b.publicKey), InsuranceContract.Commands.IssueInsurance())
                fails()
            }

            transaction {
                output(InsuranceContract.ID, st)
                // Has one command, will verify.
                command(listOf(a.publicKey, b.publicKey), InsuranceContract.Commands.IssueInsurance())
                verifies()
            }
        }
    }

    @Test
    fun contractRequiresTheTransactionsCommandToBeAnIssueCommand() {
        ledgerServices.ledger {
            transaction {
                // Has wrong command type, will fail.
                output(InsuranceContract.ID, st)
                command(listOf(a.publicKey), Commands.DummyCommand())
                fails()
            }

            transaction {
                // Has correct command type, will verify.
                output(InsuranceContract.ID, st)
                command(listOf(a.publicKey, b.publicKey), InsuranceContract.Commands.IssueInsurance())
                verifies()
            }
        }
    }
}
