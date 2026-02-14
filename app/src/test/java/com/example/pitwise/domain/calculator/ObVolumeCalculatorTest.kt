package com.example.pitwise.domain.calculator

import com.example.pitwise.domain.model.ObVolumeInput
import org.junit.Assert.assertEquals
import org.junit.Test

class ObVolumeCalculatorTest {

    private val calculator = ObVolumeCalculator()

    @Test
    fun `standard OB calculation with defaults`() {
        val input = ObVolumeInput(
            areaSqM = 5000.0,
            thicknessM = 10.0,
            swellFactor = 0.8,
            densityTonPerM3 = 2.2
        )
        val result = calculator.calculate(input)

        assertEquals(50000.0, result.volumeBcm, 0.01)
        assertEquals(62500.0, result.volumeLcm, 0.01)
        assertEquals(110000.0, result.tonnage, 0.01)
    }

    @Test
    fun `small area thin layer`() {
        val input = ObVolumeInput(
            areaSqM = 100.0,
            thicknessM = 2.0,
            swellFactor = 0.8,
            densityTonPerM3 = 2.2
        )
        val result = calculator.calculate(input)

        assertEquals(200.0, result.volumeBcm, 0.01)
        assertEquals(250.0, result.volumeLcm, 0.01)
        assertEquals(440.0, result.tonnage, 0.01)
    }

    @Test
    fun `custom swell factor and density`() {
        val input = ObVolumeInput(
            areaSqM = 1000.0,
            thicknessM = 5.0,
            swellFactor = 0.75,
            densityTonPerM3 = 1.8
        )
        val result = calculator.calculate(input)

        assertEquals(5000.0, result.volumeBcm, 0.01)
        assertEquals(6666.67, result.volumeLcm, 0.01)
        assertEquals(9000.0, result.tonnage, 0.01)
    }

    @Test
    fun `swell factor 1 means BCM equals LCM`() {
        val input = ObVolumeInput(
            areaSqM = 1000.0,
            thicknessM = 3.0,
            swellFactor = 1.0,
            densityTonPerM3 = 2.0
        )
        val result = calculator.calculate(input)

        assertEquals(result.volumeBcm, result.volumeLcm, 0.01)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero area throws exception`() {
        calculator.calculate(ObVolumeInput(areaSqM = 0.0, thicknessM = 10.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative thickness throws exception`() {
        calculator.calculate(ObVolumeInput(areaSqM = 1000.0, thicknessM = -5.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `swell factor above 1 throws exception`() {
        calculator.calculate(ObVolumeInput(areaSqM = 1000.0, thicknessM = 5.0, swellFactor = 1.5))
    }
}
