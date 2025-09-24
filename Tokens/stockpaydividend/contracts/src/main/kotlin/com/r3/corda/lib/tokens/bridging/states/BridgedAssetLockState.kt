package com.r3.corda.lib.tokens.bridging.states

import com.r3.corda.lib.tokens.bridging.contracts.BridgingContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty

@BelongsToContract(BridgingContract::class)
class BridgedAssetLockState(override val participants: List<AbstractParty>) : ContractState