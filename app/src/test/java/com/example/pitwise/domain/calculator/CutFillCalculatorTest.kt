package com.example.pitwise.domain.calculator

import com.example.pitwise.domain.model.CutFillInput
import org.junit.Assert.assertEquals
import org.junit.Test

class CutFillCalculatorTest {

    private val calculator = CutFillCalculator()

    @Test
    fun `pure cut scenario`() {
        val input = CutFillInput(
            existingElevationM = 110.0,
            targetElevationM = 100.0,
            roadWidthM = 8.0,
            segmentLengthM = 50.0
        )
        val result = calculator.calculate(input)

        assertEquals(4000.0, result.cutVolumeM3, 0.01)
        assertEquals(0.0, result.fillVolumeM3, 0.01)
        assertEquals(4000.0, result.netVolumeM3, 0.01)
    }

    @Test
    fun `pure fill scenario`() {
        val input = CutFillInput(
            existingElevationM = 95.0,
            targetElevationM = 100.0,
            roadWidthM = 10.0,
            segmentLengthM = 30.0
        )
        val result = calculator.calculate(input)

        assertEquals(0.0, result.cutVolumeM3, 0.01)
        assertEquals(1500.0, result.fillVolumeM3, 0.01)
        assertEquals(-1500.0, result.netVolumeM3, 0.01)
    }

    @Test
    fun `balanced scenario no cut no fill`() {
        val input = CutFillInput(
            existingElevationM = 100.0,
            targetElevationM = 100.0,
            roadWidthM = 8.0,
            segmentLengthM = 100.0
        )
        val result = calculator.calculate(input)

        assertEquals(0.0, result.cutVolumeM3, 0.01)
        assertEquals(0.0, result.fillVolumeM3, 0.01)
        assertEquals(0.0, result.netVolumeM3, 0.01)
    }

    @Test
    fun `small delta produces small volumes`() {
        val input = CutFillInput(
            existingElevationM = 100.5,
            targetElevationM = 100.0,
            roadWidthM = 12.0,
            segmentLengthM = 20.0
        )
        val result = calculator.calculate(input)

        assertEquals(120.0, result.cutVolumeM3, 0.01)
        assertEquals(0.0, result.fillVolumeM3, 0.01)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero width throws exception`() {
        calculator.calculate(CutFillInput(
            existingElevationM = 110.0, targetElevationM = 100.0,
            roadWidthM = 0.0, segmentLengthM = 50.0
        ))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative segment length throws exception`() {
        calculator.calculate(CutFillInput(
            existingElevationM = 110.0, targetElevationM = 100.0,
            roadWidthM = 8.0, segmentLengthM = -10.0
        ))
    }
}
