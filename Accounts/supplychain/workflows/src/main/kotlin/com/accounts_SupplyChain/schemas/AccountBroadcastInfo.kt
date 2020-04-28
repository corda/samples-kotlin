package com.accounts_SupplyChain.schemas

import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.CordaSerializable
import org.hibernate.annotations.Type
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "account_broadcast", indexes = [Index(name = "account_pk_idx", columnList = "account_uuid")])
@CordaSerializable
data class AccountBroadcastInfo(

        @Id
        @Column(name = "account_uuid", unique = true, nullable = false)
        @Type(type = "uuid-char")
        var accountUUID: UUID?,

        @Column(name = "broadcast_accounts", nullable = false)
        @ElementCollection(fetch = FetchType.EAGER)
        @Type(type = "uuid-char")
        var broadcastAccounts: List<UUID>?


) : MappedSchema(AccountBroadcastInfo::class.java, 1, listOf(AccountBroadcastInfo::class.java)) {
    constructor() : this(null, null)
}