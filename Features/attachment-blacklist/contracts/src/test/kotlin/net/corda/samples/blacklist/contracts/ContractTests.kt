package net.corda.samples.blacklist.contracts

import net.corda.core.crypto.generateKeyPair
import net.corda.core.identity.CordaX500Name
import net.corda.samples.blacklist.BLACKLIST_JAR_NAME
import net.corda.samples.blacklist.BLACKLISTED_PARTIES
import net.corda.samples.blacklist.contracts.AgreementContract.Companion.AGREEMENT_CONTRACT_ID
import net.corda.samples.blacklist.states.AgreementState
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.getTestPartyAndCertificate
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import net.corda.testing.node.makeTestIdentityService
import org.junit.Test
import java.io.FileNotFoundException
import java.io.InputStream

class ContractTests {
    private val ledgerServices = MockServices(listOf("net.corda.samples.blacklist.contracts"), identityService = makeTestIdentityService(), initialIdentity = TestIdentity(CordaX500Name("TestIdentity", "", "GB")))
    private val megaCorpName = CordaX500Name("MegaCorp", "London", "GB")
    private val miniCorpName = CordaX500Name("MiniCorp", "London", "GB")
    private val megaCorp = TestIdentity(megaCorpName)
    private val miniCorp = TestIdentity(miniCorpName)

    private val agreementTxt = "$megaCorpName agrees with $miniCorpName that..."
    private val blacklistedPartyKeyPair = generateKeyPair()
    private val blacklistedPartyPubKey = blacklistedPartyKeyPair.public
    private val blacklistedPartyName = CordaX500Name(organisation = BLACKLISTED_PARTIES[0], locality = "London", country = "GB")
    private val blacklistedParty = getTestPartyAndCertificate(blacklistedPartyName, blacklistedPartyPubKey).party

    private fun getValidAttachment(): InputStream {
        return javaClass.classLoader.getResourceAsStream(BLACKLIST_JAR_NAME) ?: throw FileNotFoundException("blacklist.jar not found")
    }

    @Test
    fun `agreement transaction contains one non-contract attachment`() {
        ledgerServices.ledger {
            // We upload a test attachment to the ledger.
            val attachmentHash = attachment(getValidAttachment())

            transaction {
                output(AGREEMENT_CONTRACT_ID, AgreementState(megaCorp.party, miniCorp.party, agreementTxt))
                command(listOf(megaCorp.publicKey, miniCorp.publicKey), AgreementContract.Commands.Agree())
                fails()
                attachment(attachmentHash)
                verifies()
            }
        }
    }

    @Test
    fun `the non-contract attachment must not blacklist any of the participants`() {
        ledgerServices.ledger {
            // We upload a test attachment to the ledger.
            val attachmentHash = attachment(getValidAttachment())

            transaction {
                output(AGREEMENT_CONTRACT_ID, AgreementState(megaCorp.party, blacklistedParty, agreementTxt))
                command(listOf(megaCorp.publicKey, blacklistedPartyPubKey), AgreementContract.Commands.Agree())
                attachment(attachmentHash)
                fails()
            }

            transaction {
                output(AGREEMENT_CONTRACT_ID, AgreementState(megaCorp.party, miniCorp.party, agreementTxt))
                command(listOf(megaCorp.publicKey, miniCorp.publicKey), AgreementContract.Commands.Agree())
                attachment(attachmentHash)
                verifies()
            }
        }
    }
}
