package net.corda.samples.tictacthor.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import net.corda.samples.tictacthor.accountsUtilities.BroadcastToCarbonCopyReceiversFlow
import net.corda.samples.tictacthor.accountsUtilities.NewKeyForAccount
import net.corda.samples.tictacthor.contracts.BoardContract
import net.corda.samples.tictacthor.states.BoardState
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.util.concurrent.atomic.AtomicReference

/*
This flow starts a game with another node by creating an new BoardState.
The responding node cannot decline the request to start a game.
The request is only denied if the responding node is already participating in a game.
*/

@InitiatingFlow
@StartableByRPC
class StartGameFlow(val whoAmI: String,
                    val whereTo: String) : FlowLogic<UniqueIdentifier>() {

    companion object {
        object GENERATING_KEYS : ProgressTracker.Step("Generating Keys for transactions.")
        object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction for between accounts")
        object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
        object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
        object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }

        object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
                GENERATING_KEYS,
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): UniqueIdentifier {

        //Generate key for transaction
        progressTracker.currentStep = GENERATING_KEYS
        val myAccount = accountService.accountInfo(whoAmI).single().state.data
        val myKey = subFlow(NewKeyForAccount(myAccount.identifier.id)).owningKey

        val targetAccount = accountService.accountInfo(whereTo).single().state.data
        val targetAcctAnonymousParty = subFlow(RequestKeyForAccount(targetAccount))

        // If this node is already participating in an active game, decline the request to start a new one
        val criteria = QueryCriteria.VaultQueryCriteria(
                externalIds = listOf(myAccount.identifier.id)
        )
        val results = serviceHub.vaultService.queryBy(
                contractStateType = BoardState::class.java,
                criteria = criteria
        ).states

        progressTracker.currentStep = GENERATING_TRANSACTION
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val command = Command(
                BoardContract.Commands.StartGame(),
                listOf(myKey,targetAcctAnonymousParty.owningKey))

        val initialBoardState = BoardState(
                myAccount.identifier,
                targetAccount.identifier,
                AnonymousParty(myKey),
                targetAcctAnonymousParty)
        val stateAndContract = StateAndContract(initialBoardState, BoardContract.ID)
        val txBuilder = TransactionBuilder(notary).withItems(stateAndContract, command)

        //Pass along Transaction
        progressTracker.currentStep = SIGNING_TRANSACTION
        txBuilder.verify(serviceHub)
        val locallySignedTx = serviceHub.signInitialTransaction(txBuilder, listOfNotNull(ourIdentity.owningKey,myKey))

        //Collect sigs
        progressTracker.currentStep =GATHERING_SIGS
        val sessionForAccountToSendTo = initiateFlow(targetAccount.host)



        val accountToMoveToSignature = subFlow(CollectSignatureFlow(locallySignedTx, sessionForAccountToSendTo,
                listOf(targetAcctAnonymousParty.owningKey)))
        val signedByCounterParty = locallySignedTx.withAdditionalSignatures(accountToMoveToSignature)


        progressTracker.currentStep =FINALISING_TRANSACTION
        val stx = subFlow(FinalityFlow(signedByCounterParty, listOf(sessionForAccountToSendTo).filter { it.counterparty != ourIdentity }))
        //return "Game created! Game Id: ${initialBoardState.linearId}, Players: $whoAmI, and ${whereTo}" + "\ntxId: ${stx.id}"
        return initialBoardState.linearId
    }
}


@InitiatedBy(StartGameFlow::class)
class StartGameFlowResponder(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call(){
        val accountMovedTo = AtomicReference<AccountInfo>()
        val transactionSigner = object : SignTransactionFlow(otherSession) {
            override fun checkTransaction(stx: SignedTransaction) {
                val keyStateMovedTo = stx.coreTransaction.outRefsOfType(BoardState::class.java).first().state.data.competitor
                keyStateMovedTo.let {
                    accountMovedTo.set(accountService.accountInfo(keyStateMovedTo.owningKey)?.state?.data)
                }

                if (accountMovedTo.get() == null) {
                    throw IllegalStateException("Account to move to was not found on this node")
                }

            }
        }
        val transaction = subFlow(transactionSigner)
        if (otherSession.counterparty != serviceHub.myInfo.legalIdentities.first()) {
            val recievedTx = subFlow(
                    ReceiveFinalityFlow(
                            otherSession,
                            expectedTxId = transaction.id,
                            statesToRecord = StatesToRecord.ALL_VISIBLE
                    )
            )
            val accountInfo = accountMovedTo.get()
            if (accountInfo != null) {
                subFlow(BroadcastToCarbonCopyReceiversFlow(accountInfo, recievedTx.coreTransaction.outRefsOfType(BoardState::class.java).first()))
            }
        }
//        //placeholder to record account information for later use
//        val accountMovedTo = AtomicReference<AccountInfo>()
//
//        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
//            override fun checkTransaction(tx: SignedTransaction) = requireThat {
//                val keyStateMovedTo = tx.coreTransaction.outRefsOfType(BoardState::class.java).first().state.data.next
//
//                keyStateMovedTo?.let {
//                    accountMovedTo.set(accountService.accountInfo(keyStateMovedTo)?.state?.data)
//                }
//                if (accountMovedTo.get() == null) {
//                    throw IllegalStateException("Account to move to was not found on this node")
//                }
//
//                // If this node is already participating in an active game, decline the request to start a new one
//                val myAccount = keyStateMovedTo?.let { accountService.accountInfo(it)?.state!!.data }
//                val criteria = QueryCriteria.VaultQueryCriteria(
//                        externalIds = listOf(myAccount?.identifier?.id) as List<UUID>
//                )
//                val results = serviceHub.vaultService.queryBy(
//                        contractStateType = BoardState::class.java,
//                        criteria = criteria
//                ).states
//                if (results.isNotEmpty()) throw FlowException("A node can only play one game at a time!")
//            }
//        }
//        //record and finalize transaction
//        val transaction = subFlow(signedTransactionFlow)
//        if (counterpartySession.counterparty != serviceHub.myInfo.legalIdentities.first()) {
//            val recievedTx = subFlow(ReceiveFinalityFlow(counterpartySession, expectedTxId = transaction.id, statesToRecord = StatesToRecord.ALL_VISIBLE))
//            val accountInfo = accountMovedTo.get()
//            if (accountInfo != null) {
//                subFlow(BroadcastToCarbonCopyReceiversFlow(accountInfo, recievedTx.coreTransaction.outRefsOfType(BoardState::class.java).first()))
//            }
//        }
    }
}
