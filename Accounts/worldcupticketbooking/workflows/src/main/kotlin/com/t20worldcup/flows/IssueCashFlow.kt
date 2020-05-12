package com.t20worldcup.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC

// *********
// * Flows *
// *********
@StartableByRPC
class IssueCashFlow(val accountName: String,
                    val currency: String,
                    val amount:Long) : FlowLogic<String>() {
    @Suspendable
    override fun call():String {

        /* Dealer node has already shared accountinfo with the bank when we ran the CreateAndShareAccountFlow.
         * So this bank node will have access to AccountInfo of the buyer. Retrieve it using the AccountService.
         * AccountService has certain helper methods, take a look at them. */
        val targetAccount = accountService.accountInfo(accountName)[0].state.data

        /* To transact with any account, we have to request for a Key from the node hosting the account.
         * For this we use RequestKeyForAccount inbuilt flow.
         * This will return a Public key wrapped in an AnonymousParty class. */
        val targetAcctAnonymousParty = subFlow(RequestKeyForAccount(targetAccount))

        //Get the base token type for issuing fungible tokens

        val token = TokenType(currency, 2)

        //issuer will be the bank. Keep in mind the issuer will always be an known legal Party class and not an AnonymousParty. This is by design
        val issuedTokenType = token issuedBy ourIdentity

        //Create a fungible token for issuing cash to account
        val fungibleToken = FungibleToken(Amount(amount, issuedTokenType), targetAcctAnonymousParty)

        val stx = subFlow(IssueTokens(listOf(fungibleToken)))
        return "Issued $amount $currency token(s) to $accountName" +
                "\ntxId: ${stx.id}"
    }
}