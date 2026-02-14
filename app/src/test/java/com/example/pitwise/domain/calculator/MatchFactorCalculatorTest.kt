package com.example.pitwise.domain.calculator

import com.example.pitwise.domain.model.MatchFactorInput
import com.example.pitwise.domain.model.MatchFactorStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchFactorCalculatorTest {

    private val calculator = MatchFactorCalculator()

    @Test
    fun `ideal match factor`() {
        // 5 trucks, exca cycle 30s, truck cycle 150s → MF = (5*30)/150 = 1.0
        val input = MatchFactorInput(
            numTrucks = 5,
            excaCycleTimeSec = 30.0,
            truckCycleTimeSec = 150.0
        )
        val result = calculator.calculate(input)

        assertEquals(1.0, result.matchFactor, 0.01)
        assertEquals(MatchFactorStatus.IDEAL, result.status)
    }

    @Test
    fun `excavator idle when MF below 0_9`() {
        // 3 trucks, exca cycle 30s, truck cycle 120s → MF = (3*30)/120 = 0.75
        val input = MatchFactorInput(
            numTrucks = 3,
            excaCycleTimeSec = 30.0,
            truckCycleTimeSec = 120.0
        )
        val result = calculator.calculate(input)

        assertEquals(0.75, result.matchFactor, 0.01)
        assertEquals(MatchFactorStatus.EXCAVATOR_IDLE, result.status)
        assertTrue(result.recommendation.contains("Excavator"))
    }

    @Test
    fun `truck idle when MF above 1_1`() {
        // 8 trucks, exca cycle 30s, truck cycle 180s → MF = (8*30)/180 = 1.33
        val input = MatchFactorInput(
            numTrucks = 8,
            excaCycleTimeSec = 30.0,
            truckCycleTimeSec = 180.0
        )
        val result = calculator.calculate(input)

        assertEquals(1.33, result.matchFactor, 0.01)
        assertEquals(MatchFactorStatus.TRUCK_IDLE, result.status)
        assertTrue(result.recommendation.contains("Truck"))
    }

    @Test
    fun `boundary MF 0_9 is ideal`() {
        // MF = (9 * 30) / 300 = 0.9
        val input = MatchFactorInput(
            numTrucks = 9,
            excaCycleTimeSec = 30.0,
            truckCycleTimeSec = 300.0
        )
        val result = calculator.calculate(input)

        assertEquals(0.9, result.matchFactor, 0.01)
        assertEquals(MatchFactorStatus.IDEAL, result.status)
    }

    @Test
    fun `boundary MF 1_1 is ideal`() {
        // MF = (11 * 30) / 300 = 1.1
        val input = MatchFactorInput(
            numTrucks = 11,
            excaCycleTimeSec = 30.0,
            truckCycleTimeSec = 300.0
        )
        val result = calculator.calculate(input)

        assertEquals(1.1, result.matchFactor, 0.01)
        assertEquals(MatchFactorStatus.IDEAL, result.status)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero trucks throws exception`() {
        calculator.calculate(MatchFactorInput(numTrucks = 0, excaCycleTimeSec = 30.0, truckCycleTimeSec = 150.0))
    }
}
