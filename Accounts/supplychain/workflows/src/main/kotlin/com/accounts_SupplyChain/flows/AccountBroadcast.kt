package com.accounts_SupplyChain.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.internal.FlowAsyncOperation
import com.accounts_SupplyChain.schemas.AccountBroadcastInfo
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

class BroadcastToCarbonCopyReceiversFlow(
        private val owningAccount: AccountInfo,
        private val stateToBroadcast: StateAndRef<*>,
        private val carbonCopyReceivers: Collection<AccountInfo>? = null
) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        owningAccount.identifier.id.let { accountThatOwnedStateId ->
            val accountsToBroadCastTo = carbonCopyReceivers
                    ?: subFlow(GetAllInterestedAccountsFlow(accountThatOwnedStateId))
            for (accountToBroadcastTo in accountsToBroadCastTo) {
                accountService.shareStateWithAccount(accountToBroadcastTo.identifier.id, stateToBroadcast)
            }
        }
    }
}


@StartableByRPC
@StartableByService
class AddBroadcastAccountToAccountFlow(val accountUUID: UUID, val accountToPermission: UUID, val add: Boolean = true) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        serviceHub.withEntityManager {
            val existingEntry = find(AccountBroadcastInfo::class.java, accountUUID)
                    ?: AccountBroadcastInfo(accountUUID = accountUUID, broadcastAccounts = listOf())
            val modifiedEntry = if (add) {
                existingEntry.broadcastAccounts = existingEntry.broadcastAccounts!! + listOf(accountToPermission)
                existingEntry
            } else {
                existingEntry.broadcastAccounts = existingEntry.broadcastAccounts!! - listOf(accountToPermission)
                existingEntry
            }
            persist(modifiedEntry)
        }
    }
}

@StartableByRPC
class GetAllInterestedAccountsFlow(val accountId: UUID) : FlowLogic<List<AccountInfo>>() {
    @Suspendable
    override fun call(): List<AccountInfo> {
        val refHolder = AtomicReference<AccountBroadcastInfo?>()
        serviceHub.withEntityManager(Consumer { em ->
            val foundAccount = em.find(AccountBroadcastInfo::class.java, accountId)
            val loadedAccount = foundAccount?.copy(broadcastAccounts = foundAccount.broadcastAccounts?.map { it }
                    ?: listOf())
            loadedAccount?.broadcastAccounts?.size
            refHolder.set(loadedAccount)
        })
        return refHolder.get()?.let { it.broadcastAccounts?.mapNotNull(getAccountFromAccountId(serviceHub.cordaService(KeyManagementBackedAccountService::class.java))) }
                ?: listOf()
    }

    private fun getAccountFromAccountId(accountService: KeyManagementBackedAccountService) = { accountId: UUID ->
        accountService.accountInfo(accountId)?.state?.data
    }
}

class BroadcastOperation(
        private val accountService: KeyManagementBackedAccountService,
        private val accountToBroadcastTo: AccountInfo,
        private val stateToBroadcast: StateAndRef<*>
) : FlowAsyncOperation<Unit> {

    @Suspendable
    override fun execute(deduplicationId: String): CordaFuture<Unit> {
        return accountService.shareStateWithAccount(accountToBroadcastTo.identifier.id, stateToBroadcast)
    }
}