package net.corda.samples.states

import net.corda.core.serialization.CordaSerializable
import javax.persistence.criteria.CriteriaBuilder

@CordaSerializable
data class Claim (val claimNumber:String, val claimDescription:String, val claimAmount:Int)