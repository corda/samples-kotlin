package net.corda.samples.carinsurance.states

import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleDetailTests {
    @Test
    fun constructorTest() {
        val registrationNumber = "registration number: 2ds9Fvk"
        val chassisNum = "chassis# aedl3sc"
        val make = "Toyota"
        val model = "Corolla"
        val variant = "SE"
        val color = "hot rod beige"
        val fuelType = "regular"

        val (registrationNumber1, chasisNumber, make1, model1, variant1, color1, fuelType1) = VehicleDetail(
                registrationNumber,
                chassisNum,
                make,
                model,
                variant,
                color,
                fuelType)

        assertEquals(registrationNumber, registrationNumber1)
        assertEquals(chassisNum, chasisNumber)
        assertEquals(make, make1)
        assertEquals(model, model1)
        assertEquals(variant, variant1)
        assertEquals(color, color1)
        assertEquals(fuelType, fuelType1)
    }
}
