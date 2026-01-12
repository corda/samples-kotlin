package net.corda.samples.solana.dvp.contracts

import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.samples.solana.dvp.states.SharesPaymentState
import net.corda.solana.sdk.SplToken
import net.corda.solana.sdk.instruction.SolanaInstruction
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test
import net.corda.solana.sdk.instruction.Pubkey
import net.corda.core.crypto.secureRandomBytes

class SharesPaymentContractTests {

    private val ledgerServices = MockServices(listOf("net.corda.samples.solana.dvp"))

    private val sellerIdentity = TestIdentity(CordaX500Name("Seller", "London", "GB"))
    private val buyerIdentity = TestIdentity(CordaX500Name("Buyer", "New York", "US"))

    private val seller = sellerIdentity.party
    private val buyer = buyerIdentity.party

    private val solanaSellerTokenAccount = Pubkey(secureRandomBytes(32))
    private val solanaBuyerTokenAccount = Pubkey(secureRandomBytes(32))
    private val solanaMintAuthority = Pubkey(secureRandomBytes(32))
    private val stablecoinTokenMint =  Pubkey(secureRandomBytes(32))

    private val shareTokenType = TokenType("CORDASHARES", 0)
    private val cordaSharesAmount = Amount(100L, shareTokenType)

    private fun sampleState(
        paymentAmount: Long = 1_000L,
        paymentDecimals: Byte = 6
    ) = SharesPaymentState(
        cordaSharesAmount = cordaSharesAmount,
        cordaSeller = seller,
        cordaBuyer = buyer,
        solanaSellerTokenAccount = solanaSellerTokenAccount,
        solanaBuyerTokenAccount = solanaBuyerTokenAccount,
        solanaBuyerWalletAccount = solanaMintAuthority,
        solanaStablecoin = stablecoinTokenMint,
        stablecoinAmount = paymentAmount,
        stablecoinDecimals = paymentDecimals
    )

    @Test
    fun `Agree verifies with correct output, signers, and Solana instruction`() {
        val output = sampleState()

        val instruction: SolanaInstruction = SplToken.transfer(
            output.solanaBuyerTokenAccount,
            output.solanaStablecoin,
            output.solanaSellerTokenAccount,
            output.solanaBuyerWalletAccount,
            output.stablecoinAmount,
            output.stablecoinDecimals
        )

        ledgerServices.ledger {
            transaction {
                output(SharesPaymentContract.ID, output)

                notaryInstruction(instruction)

                command(
                    listOf(seller.owningKey, buyer.owningKey),
                    SharesPaymentContract.Commands.Agree()
                )

                verifies()
            }
        }
    }

    @Test
    fun `Agree fails if buyer does not sign`() {
        val out = sampleState()

        val instruction: SolanaInstruction = SplToken.transfer(
            out.solanaBuyerTokenAccount,
            out.solanaStablecoin,
            out.solanaSellerTokenAccount,
            out.solanaBuyerWalletAccount,
            out.stablecoinAmount,
            out.stablecoinDecimals
        )

        ledgerServices.ledger {
            transaction {
                output(SharesPaymentContract.ID, out)

                notaryInstruction(instruction)

                // Only seller signs -> should fail
                command(
                    listOf(seller.owningKey),
                    SharesPaymentContract.Commands.Agree()
                )

                failsWith("Seller and buyer must both sign.")
            }
        }
    }
}

