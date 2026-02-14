package com.example.pitwise.domain.calculator

import com.example.pitwise.domain.model.CoalTonnageInput
import org.junit.Assert.assertEquals
import org.junit.Test

class CoalTonnageCalculatorTest {

    private val calculator = CoalTonnageCalculator()

    @Test
    fun `standard coal calculation`() {
        val input = CoalTonnageInput(
            areaSqM = 10000.0,
            seamThicknessM = 3.0,
            densityTonPerM3 = 1.3,
            recoveryPercent = 85.0
        )
        val result = calculator.calculate(input)

        assertEquals(39000.0, result.romTonnage, 0.01)
        assertEquals(33150.0, result.cleanCoalTonnage, 0.01)
    }

    @Test
    fun `100 percent recovery means ROM equals clean coal`() {
        val input = CoalTonnageInput(
            areaSqM = 5000.0,
            seamThicknessM = 2.0,
            densityTonPerM3 = 1.3,
            recoveryPercent = 100.0
        )
        val result = calculator.calculate(input)

        assertEquals(result.romTonnage, result.cleanCoalTonnage, 0.01)
    }

    @Test
    fun `low recovery scenario`() {
        val input = CoalTonnageInput(
            areaSqM = 2000.0,
            seamThicknessM = 1.5,
            densityTonPerM3 = 1.3,
            recoveryPercent = 60.0
        )
        val result = calculator.calculate(input)

        assertEquals(3900.0, result.romTonnage, 0.01)
        assertEquals(2340.0, result.cleanCoalTonnage, 0.01)
    }

    @Test
    fun `custom density`() {
        val input = CoalTonnageInput(
            areaSqM = 1000.0,
            seamThicknessM = 4.0,
            densityTonPerM3 = 1.45,
            recoveryPercent = 90.0
        )
        val result = calculator.calculate(input)

        assertEquals(5800.0, result.romTonnage, 0.01)
        assertEquals(5220.0, result.cleanCoalTonnage, 0.01)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative area throws exception`() {
        calculator.calculate(CoalTonnageInput(areaSqM = -100.0, seamThicknessM = 3.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `recovery above 100 throws exception`() {
        calculator.calculate(CoalTonnageInput(areaSqM = 1000.0, seamThicknessM = 3.0, recoveryPercent = 110.0))
    }
}
