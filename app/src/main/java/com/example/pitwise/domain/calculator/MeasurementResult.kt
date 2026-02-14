package com.example.pitwise.domain.calculator

import com.example.pitwise.domain.map.MapVertex
import com.example.pitwise.ui.screen.calculate.CalculatorType

/**
 * Measurement data produced by Map's MEASURE mode.
 * Passed to Calculator as context for auto-fill and filtering.
 */
data class MeasurementResult(
    val type: MeasurementType,
    val value: Double,
    val unit: String = "m",
    val points: List<MapVertex> = emptyList()
)

enum class MeasurementType {
    DISTANCE,
    AREA
}

/**
 * Router logic: determines which calculators are relevant
 * for a given measurement type.
 */
object CalculatorRouter {

    /**
     * Returns the calculators relevant to the given measurement type.
     * If null (no context), returns the full list.
     */
    fun getAvailableCalculators(type: MeasurementType?): List<CalculatorType> {
        return when (type) {
            MeasurementType.DISTANCE -> listOf(
                CalculatorType.HAULING_CYCLE,
                CalculatorType.ROAD_GRADE
            )
            MeasurementType.AREA -> listOf(
                CalculatorType.OB_VOLUME,
                CalculatorType.COAL_TONNAGE
            )
            null -> listOf(
                CalculatorType.OB_VOLUME,
                CalculatorType.COAL_TONNAGE,
                CalculatorType.HAULING_CYCLE,
                CalculatorType.ROAD_GRADE,
                CalculatorType.CUT_FILL
            )
        }
    }
}
