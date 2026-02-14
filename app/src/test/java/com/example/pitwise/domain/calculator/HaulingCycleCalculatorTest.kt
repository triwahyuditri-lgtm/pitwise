package com.example.pitwise.domain.calculator

import com.example.pitwise.domain.model.HaulingCycleInput
import org.junit.Assert.assertEquals
import org.junit.Test

class HaulingCycleCalculatorTest {

    private val calculator = HaulingCycleCalculator()

    @Test
    fun `standard hauling cycle with CAT 777F specs`() {
        // 2km haul, loaded 30kmh, empty 50kmh, 3min load, 2min dump
        val input = HaulingCycleInput(
            distanceM = 2000.0,
            speedLoadedKmh = 30.0,
            speedEmptyKmh = 50.0,
            loadingTimeMin = 3.0,
            dumpingTimeMin = 2.0,
            vesselCapacityM3 = 60.0
        )
        val result = calculator.calculate(input)

        // Travel loaded = (2/30)*60 = 4.0 min
        // Travel empty  = (2/50)*60 = 2.4 min
        // Cycle = 4.0 + 2.4 + 3.0 + 2.0 = 11.4 min
        assertEquals(11.4, result.cycleTimeMin, 0.01)
        assertEquals(60.0 / 11.4, result.tripsPerHour, 0.01)
        assertEquals((60.0 / 11.4) * 60.0, result.productionPerUnit, 0.1)
    }

    @Test
    fun `short distance hauling`() {
        val input = HaulingCycleInput(
            distanceM = 500.0,
            speedLoadedKmh = 25.0,
            speedEmptyKmh = 45.0,
            loadingTimeMin = 4.0,
            dumpingTimeMin = 1.5,
            vesselCapacityM3 = 36.6
        )
        val result = calculator.calculate(input)

        // Travel loaded = (0.5/25)*60 = 1.2 min
        // Travel empty  = (0.5/45)*60 = 0.667 min
        // Cycle = 1.2 + 0.667 + 4.0 + 1.5 = 7.367 min
        assertEquals(7.367, result.cycleTimeMin, 0.01)
    }

    @Test
    fun `no vessel capacity returns zero production`() {
        val input = HaulingCycleInput(
            distanceM = 1000.0,
            speedLoadedKmh = 30.0,
            speedEmptyKmh = 50.0,
            loadingTimeMin = 3.0,
            dumpingTimeMin = 2.0,
            vesselCapacityM3 = 0.0
        )
        val result = calculator.calculate(input)

        assertEquals(0.0, result.productionPerUnit, 0.01)
    }

    @Test
    fun `long haul distance scenario`() {
        val input = HaulingCycleInput(
            distanceM = 5000.0,
            speedLoadedKmh = 28.0,
            speedEmptyKmh = 48.0,
            loadingTimeMin = 3.5,
            dumpingTimeMin = 2.0,
            vesselCapacityM3 = 92.0
        )
        val result = calculator.calculate(input)

        // Travel loaded = (5/28)*60 = 10.714 min
        // Travel empty  = (5/48)*60 = 6.25 min
        // Cycle = 10.714 + 6.25 + 3.5 + 2.0 = 22.464 min
        assertEquals(22.464, result.cycleTimeMin, 0.01)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero distance throws exception`() {
        calculator.calculate(HaulingCycleInput(
            distanceM = 0.0, speedLoadedKmh = 30.0, speedEmptyKmh = 50.0,
            loadingTimeMin = 3.0, dumpingTimeMin = 2.0
        ))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero speed throws exception`() {
        calculator.calculate(HaulingCycleInput(
            distanceM = 1000.0, speedLoadedKmh = 0.0, speedEmptyKmh = 50.0,
            loadingTimeMin = 3.0, dumpingTimeMin = 2.0
        ))
    }
}
