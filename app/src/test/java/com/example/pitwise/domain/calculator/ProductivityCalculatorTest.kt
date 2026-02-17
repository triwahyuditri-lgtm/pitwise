package com.example.pitwise.domain.calculator

import com.example.pitwise.domain.model.ProductivityInput
import com.example.pitwise.domain.model.ProductivityStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductivityCalculatorTest {

    private val calculator = ProductivityCalculator()

    @Test
    fun `green status when production exceeds target`() {
        // PC400 scenario: bucket 2.4m³, FF 0.80, cycle 32s, 8 effective hours
        val input = ProductivityInput(
            unitName = "PC400-8",
            bucketOrVesselM3 = 2.4,
            fillFactor = 0.80,
            cycleTimeActualSec = 32.0,
            effectiveWorkingHours = 8.0,
            targetProduction = 1500.0
        )
        val result = calculator.calculate(input)!!

        // Prod/hr = (2.4 * 0.80 * 3600) / 32 = 216 BCM/hr
        // Total = 216 * 8 = 1728 BCM
        assertEquals(216.0, result.actualProductivityPerHour, 0.1)
        assertEquals(1728.0, result.totalProduction, 0.1)
        assertEquals(ProductivityStatus.GREEN, result.status)
    }

    @Test
    fun `yellow status deviation between minus 10 and 0`() {
        val input = ProductivityInput(
            unitName = "PC200-8",
            bucketOrVesselM3 = 0.93,
            fillFactor = 0.85,
            cycleTimeActualSec = 30.0, // slightly worse than ref 26s
            effectiveWorkingHours = 7.0,
            targetProduction = 700.0
        )
        val result = calculator.calculate(input)!!

        // Prod/hr = (0.93 * 0.85 * 3600) / 30 = 94.86 BCM/hr
        // Total = 94.86 * 7 = 664.02
        // Deviation = ((664.02 - 700) / 700) * 100 = -5.14%
        assertEquals(94.86, result.actualProductivityPerHour, 0.1)
        assertEquals(664.02, result.totalProduction, 0.1)
        assertEquals(ProductivityStatus.YELLOW, result.status)
    }

    @Test
    fun `red status deviation worse than minus 10 percent`() {
        val input = ProductivityInput(
            unitName = "CAT 320D",
            bucketOrVesselM3 = 1.2,
            fillFactor = 0.70, // poor fill factor
            cycleTimeActualSec = 40.0, // much worse than ref 28s
            effectiveWorkingHours = 6.0,
            targetProduction = 600.0
        )
        val result = calculator.calculate(input)!!

        // Prod/hr = (1.2 * 0.70 * 3600) / 40 = 75.6 BCM/hr
        // Total = 75.6 * 6 = 453.6
        // Deviation = ((453.6 - 600) / 600) * 100 = -24.4%
        assertEquals(75.6, result.actualProductivityPerHour, 0.1)
        assertEquals(453.6, result.totalProduction, 0.1)
        assertEquals(ProductivityStatus.RED, result.status)
    }

    @Test
    fun `exactly meeting target is green`() {
        val input = ProductivityInput(
            unitName = "Test Unit",
            bucketOrVesselM3 = 1.0,
            fillFactor = 1.0,
            cycleTimeActualSec = 36.0,
            effectiveWorkingHours = 10.0,
            targetProduction = 1000.0
        )
        val result = calculator.calculate(input)!!

        // Prod/hr = (1.0 * 1.0 * 3600) / 36 = 100
        // Total = 100 * 10 = 1000
        assertEquals(1000.0, result.totalProduction, 0.1)
        assertEquals(0.0, result.deviationPercent, 0.01)
        assertEquals(ProductivityStatus.GREEN, result.status)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero cycle time throws exception`() {
        calculator.calculate(ProductivityInput(
            unitName = "X", bucketOrVesselM3 = 1.0, cycleTimeActualSec = 0.0,
            effectiveWorkingHours = 8.0, targetProduction = 500.0
        ))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fill factor above 1 throws exception`() {
        calculator.calculate(ProductivityInput(
            unitName = "X", bucketOrVesselM3 = 1.0, fillFactor = 1.1,
            cycleTimeActualSec = 30.0, effectiveWorkingHours = 8.0, targetProduction = 500.0
        ))
    }
}
