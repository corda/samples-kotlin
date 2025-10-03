package net.corda.bank.solana

import com.lmax.solana4j.Solana
import com.lmax.solana4j.api.PublicKey
import com.lmax.solana4j.client.jsonrpc.SolanaJsonRpcClient
import com.lmax.solana4j.programs.SystemProgram
import com.lmax.solana4j.programs.SystemProgram.MINT_ACCOUNT_LENGTH
import com.lmax.solana4j.programs.Token2022Program
import com.lmax.solana4j.programs.TokenProgram.ACCOUNT_LAYOUT_SPAN
import net.corda.solana.aggregator.common.RpcParams
import net.corda.solana.aggregator.common.Signer
import net.corda.solana.aggregator.common.checkResponse
import net.corda.solana.aggregator.common.sendAndConfirm
import net.corda.solana.aggregator.common.toPublicKey
import net.corda.solana.sdk.internal.Token2022
import java.net.http.HttpClient
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Optional

//TODO use from Corda Enterprise
/**
 * Class to prepare and clean up the tokens setup on devnet
 */
class TokenManagement(rpcUrl: String, val rpcParams: RpcParams = RpcParams()) {

    companion object {
        /**
         * Fee for a transaction with one signature. Each further signature adds the same fee again.
         */
        const val BASE_TRANSACTION_FEE: Long = 5000
        const val LAMPORTS_PER_SOL: Long = 1000000000

        const val TOKEN2022_BURN_DATA_SIZE = 9
        const val TOKEN2022_BURN_ID: Byte = 8

    }

    private val client = SolanaJsonRpcClient(HttpClient.newHttpClient(), rpcUrl)

    /**
     * Create a token mint (a token definition). The public key returned needs to be stored safely as this is required to
     * access the token definition in all other operations.
     *
     * @param payer The account signing and paying for the token creation
     * @param mintAuthority The account who will control minting and burning of the token type. Will
     *  default to the payer.
     * @param tokenMint Optional parameter to specify the key of the token. Defaults to a new random key
     * @param decimals Decimal positions of the token. Defaults to 9
     * @return returns the public key of the token definition.
     */
    fun createToken(
        payer: Signer,
        mintAuthority: PublicKey = payer.account,
        tokenMint: Signer = Signer.random(),
        decimals: Byte = 9,
    ): PublicKey {
        val rentExemption = client.getMinimumBalanceForRentExemption(MINT_ACCOUNT_LENGTH, rpcParams)
            .checkResponse("getMinimumBalanceForRentExemption")!!
        client.sendAndConfirm(
            { txBuilder ->
                SystemProgram.factory(txBuilder).createAccount(
                    payer.account,
                    tokenMint.account,
                    rentExemption,
                    MINT_ACCOUNT_LENGTH.toLong(),
                    Token2022Program.PROGRAM_ACCOUNT
                )
                Token2022Program.factory(txBuilder).initializeMint(
                    tokenMint.account,
                    decimals,
                    mintAuthority,
                    Optional.empty()
                )
            },
            payer,
            listOf(tokenMint),
            rpcParams
        )
        return tokenMint.account
    }

    /**
     * Creates an account that can hold the Token2022 token created for the given token mint.
     *
     * @param payer Account paying for and signing the transaction
     * @param tokenMint The token definition this account will be able to hold.
     * @param accountOwner The owner of the new token account (that will have to sign for transactions moving tokens out of the account)
     * @param tokenAccount Optional parameters specifying the keys of the account. Will default to a random key
     * @return Returns the public key (i.e. address) of the new token account
     */
    fun createTokenAccount(
        payer: Signer,
        tokenMint: PublicKey,
        accountOwner: PublicKey = payer.account,
        tokenAccount: Signer = Signer.random(),
    ): PublicKey {
        val rentExemption = client.getMinimumBalanceForRentExemption(ACCOUNT_LAYOUT_SPAN, rpcParams)
            .checkResponse("getMinimumBalanceForRentExemption")!!
        client.sendAndConfirm(
            { txBuilder ->
                SystemProgram.factory(txBuilder).createAccount(
                    payer.account,
                    tokenAccount.account,
                    rentExemption,
                    ACCOUNT_LAYOUT_SPAN.toLong(),
                    Token2022Program.PROGRAM_ACCOUNT
                )
                Token2022Program.factory(txBuilder).initializeAccount(tokenAccount.account, tokenMint, accountOwner)
            },
            payer,
            listOf(tokenAccount),
            rpcParams
        )
        return tokenAccount.account
    }

    /**
     * Mints tokens of the given type to the given token account. Will fail with an exception if the token mint does not match
     * the account, or the account cannot hold tokens.
     *
     * @param mint tokenMint (token definition) to be minted
     * @param destination token account that will hold the newly minted tokens. Must be able to hold tokens defined by mint
     * @param mintAuthority the mint authority that can sign for minting tokens of the given type
     * @param amount The amount of tokens to be minted
     */
    fun mintTo(
        mint: PublicKey,
        destination: PublicKey,
        mintAuthority: Signer,
        amount: Long,
    ) {
        client.sendAndConfirm(
            { txBuilder ->
                Token2022Program.factory(txBuilder).mintTo(
                    mint,
                    mintAuthority.account,
                    listOf(Solana.destination(destination, amount))
                )
            },
            mintAuthority,
            listOf(mintAuthority)
        )
    }

    /**
     * Burn tokens of a given mint type
     *
     * @param tokenAccount the account holding the tokens to be burned
     * @param owner The owner of the token account who can sign for getting rid of tokens
     * @param tokenMint The token definition address
     * @param amount The amount of tokens to be burned
     */
    fun burnTokens(
        tokenAccount: PublicKey,
        owner: Signer,
        tokenMint: PublicKey,
        amount: Long,
    ) {
        client.sendAndConfirm(
            { txBuilder ->
                val data = ByteBuffer.allocate(TOKEN2022_BURN_DATA_SIZE)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .put(TOKEN2022_BURN_ID)
                    .putLong(amount).array()
                txBuilder.append { ixBuilder ->
                    ixBuilder.program(Token2022.PROGRAM_ID.toPublicKey())
                    ixBuilder.account(tokenAccount, false, true)
                    ixBuilder.account(tokenMint, false, true)
                    ixBuilder.account(owner.account, true, false)
                    ixBuilder.data(TOKEN2022_BURN_DATA_SIZE) { buffer -> buffer.put(data) }
                }
            },
            owner,
            emptyList()
        )
    }

    /**
     * Transfers tokens between two token accounts
     *
     * @param source Account that will provide the tokens to be transferred
     * @param destination Account that will receive the tokens
     * @param owner Owner of the source account that needs to sign the transaction
     * @param amount The amount of tokens to be transferred
     */
    fun transferTokens(
        source: PublicKey,
        destination: PublicKey,
        owner: Signer,
        amount: Long,
    ) {
        client.sendAndConfirm(
            { txBuilder ->
                Token2022Program.factory(txBuilder).transfer(
                    source,
                    destination,
                    owner.account,
                    amount,
                    listOf(owner.account)
                )
            },
            owner,
            emptyList()
        )
    }

    /**
     * Helper function to transfer SOL from a funded account to a new account for testing
     * @param funder Funded account that can fund the test account
     * @param account Address of a test account to be funded
     * @param lamports Number of lamports to be transferred.
     */
    fun fundAccount(
        funder: Signer,
        account: PublicKey,
        lamports: Long,
    ) {
        client.sendAndConfirm(
            { txBuilder ->
                SystemProgram.factory(txBuilder).transfer(
                    funder.account,
                    account,
                    lamports
                )
            },
            funder
        )
    }

    /**
     * Helper function to defund an account when the tests are done. This will send all remaining lamports in an account back to
     * a specified address (and thus mark the account for deletion)
     *
     * @param toBeDefunded Account to be defunded and deleted. This needs to sign the transaction
     * @param destination Account where the funds should go
     */
    fun defundAccount(
        toBeDefunded: Signer,
        destination: PublicKey,
    ) {
        val lamports = client.getBalance(toBeDefunded.account.base58(), RpcParams()).checkResponse("defundAccount")!!
        client.sendAndConfirm(
            { txBuilder ->
                SystemProgram.factory(txBuilder).transfer(
                    toBeDefunded.account,
                    destination,
                    lamports - BASE_TRANSACTION_FEE
                )
            },
            toBeDefunded
        )
    }
}