package com.example.pitwise.domain.calculator

import com.example.pitwise.domain.model.DelayCategory
import com.example.pitwise.domain.model.DelayEntry
import com.example.pitwise.domain.model.DelayInput
import org.junit.Assert.assertEquals
import org.junit.Test

class DelayLossCalculatorTest {

    private val calculator = DelayLossCalculator()

    @Test
    fun `single delay category`() {
        val input = DelayInput(
            delays = listOf(
                DelayEntry(DelayCategory.WEATHER, 60.0)
            ),
            actualProductivityPerHour = 200.0
        )
        val result = calculator.calculate(input)

        assertEquals(60.0, result.totalDelayMinutes, 0.01)
        assertEquals(200.0, result.productionLoss, 0.01)  // (60/60) * 200
        assertEquals(DelayCategory.WEATHER, result.dominantFactor)
    }

    @Test
    fun `multiple delay categories finds dominant`() {
        val input = DelayInput(
            delays = listOf(
                DelayEntry(DelayCategory.LOADING_DELAY, 15.0),
                DelayEntry(DelayCategory.HAULING_DELAY, 30.0),
                DelayEntry(DelayCategory.ROAD_CONDITION, 10.0),
                DelayEntry(DelayCategory.OPERATOR, 5.0)
            ),
            actualProductivityPerHour = 120.0
        )
        val result = calculator.calculate(input)

        assertEquals(60.0, result.totalDelayMinutes, 0.01)
        assertEquals(120.0, result.productionLoss, 0.01)  // (60/60) * 120
        assertEquals(DelayCategory.HAULING_DELAY, result.dominantFactor)
    }

    @Test
    fun `aggregates same category entries`() {
        val input = DelayInput(
            delays = listOf(
                DelayEntry(DelayCategory.OPERATOR, 10.0),
                DelayEntry(DelayCategory.OPERATOR, 20.0),
                DelayEntry(DelayCategory.WEATHER, 15.0)
            ),
            actualProductivityPerHour = 100.0
        )
        val result = calculator.calculate(input)

        assertEquals(45.0, result.totalDelayMinutes, 0.01)
        assertEquals(DelayCategory.OPERATOR, result.dominantFactor)
        // Breakdown: OPERATOR = (30/60)*100 = 50, WEATHER = (15/60)*100 = 25
        assertEquals(50.0, result.breakdownByCategory[DelayCategory.OPERATOR]!!, 0.01)
        assertEquals(25.0, result.breakdownByCategory[DelayCategory.WEATHER]!!, 0.01)
    }

    @Test
    fun `small delays produce small loss`() {
        val input = DelayInput(
            delays = listOf(
                DelayEntry(DelayCategory.ROAD_CONDITION, 5.0)
            ),
            actualProductivityPerHour = 150.0
        )
        val result = calculator.calculate(input)

        assertEquals(5.0, result.totalDelayMinutes, 0.01)
        assertEquals(12.5, result.productionLoss, 0.01)  // (5/60) * 150 = 12.5
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty delays throws exception`() {
        calculator.calculate(DelayInput(delays = emptyList(), actualProductivityPerHour = 100.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero productivity throws exception`() {
        calculator.calculate(DelayInput(
            delays = listOf(DelayEntry(DelayCategory.WEATHER, 30.0)),
            actualProductivityPerHour = 0.0
        ))
    }
}
