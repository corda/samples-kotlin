package net.corda.samples.states

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class VehicleDetail( val registrationNumber: String,
                          val chasisNumber: String,
                          val make: String,
                          val model: String,
                          val variant: String,
                          val color: String,
                          val fuelType: String)