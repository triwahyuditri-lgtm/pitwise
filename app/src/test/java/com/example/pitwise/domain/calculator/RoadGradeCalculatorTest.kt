package com.example.pitwise.domain.calculator

import com.example.pitwise.domain.model.RoadGradeInput
import com.example.pitwise.domain.model.RoadGradeStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class RoadGradeCalculatorTest {

    private val calculator = RoadGradeCalculator()

    @Test
    fun `safe grade below 8 percent`() {
        val input = RoadGradeInput(
            horizontalDistanceM = 200.0,
            elevationStartM = 100.0,
            elevationEndM = 110.0
        )
        val result = calculator.calculate(input)

        assertEquals(5.0, result.gradePercent, 0.01)
        assertEquals(10.0, result.deltaH, 0.01)
        assertEquals(RoadGradeStatus.SAFE, result.status)
    }

    @Test
    fun `warning grade 8 to 10 percent`() {
        val input = RoadGradeInput(
            horizontalDistanceM = 100.0,
            elevationStartM = 50.0,
            elevationEndM = 59.0
        )
        val result = calculator.calculate(input)

        assertEquals(9.0, result.gradePercent, 0.01)
        assertEquals(9.0, result.deltaH, 0.01)
        assertEquals(RoadGradeStatus.WARNING, result.status)
    }

    @Test
    fun `critical grade above 10 percent`() {
        val input = RoadGradeInput(
            horizontalDistanceM = 120.0,
            elevationStartM = 200.0,
            elevationEndM = 215.0
        )
        val result = calculator.calculate(input)

        assertEquals(12.5, result.gradePercent, 0.01)
        assertEquals(15.0, result.deltaH, 0.01)
        assertEquals(RoadGradeStatus.CRITICAL, result.status)
    }

    @Test
    fun `downhill grade uses absolute value`() {
        val input = RoadGradeInput(
            horizontalDistanceM = 200.0,
            elevationStartM = 150.0,
            elevationEndM = 130.0
        )
        val result = calculator.calculate(input)

        assertEquals(10.0, result.gradePercent, 0.01)
        assertEquals(20.0, result.deltaH, 0.01)
    }

    @Test
    fun `flat road zero grade`() {
        val input = RoadGradeInput(
            horizontalDistanceM = 500.0,
            elevationStartM = 100.0,
            elevationEndM = 100.0
        )
        val result = calculator.calculate(input)

        assertEquals(0.0, result.gradePercent, 0.01)
        assertEquals(0.0, result.deltaH, 0.01)
        assertEquals(RoadGradeStatus.SAFE, result.status)
    }

    @Test
    fun `exactly 8 percent is still safe`() {
        val input = RoadGradeInput(
            horizontalDistanceM = 100.0,
            elevationStartM = 0.0,
            elevationEndM = 8.0
        )
        val result = calculator.calculate(input)

        assertEquals(8.0, result.gradePercent, 0.01)
        assertEquals(RoadGradeStatus.SAFE, result.status)
    }

    @Test
    fun `exactly 10 percent is warning`() {
        val input = RoadGradeInput(
            horizontalDistanceM = 100.0,
            elevationStartM = 0.0,
            elevationEndM = 10.0
        )
        val result = calculator.calculate(input)

        assertEquals(10.0, result.gradePercent, 0.01)
        assertEquals(RoadGradeStatus.WARNING, result.status)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero distance throws exception`() {
        calculator.calculate(RoadGradeInput(
            horizontalDistanceM = 0.0, elevationStartM = 0.0, elevationEndM = 10.0
        ))
    }
}
