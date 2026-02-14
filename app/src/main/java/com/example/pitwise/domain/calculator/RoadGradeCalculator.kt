package com.example.pitwise.domain.calculator

import com.example.pitwise.domain.model.RoadGradeInput
import com.example.pitwise.domain.model.RoadGradeOutput
import com.example.pitwise.domain.model.RoadGradeStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Calculates road grade percentage and safety status.
 *
 * ΔH    = |Elevation End - Elevation Start|
 * Grade = (ΔH / Horizontal Distance) × 100
 *
 * Status thresholds:
 *   ≤ 8%  → SAFE
 *   8-10% → WARNING
 *   > 10% → CRITICAL
 */
@Singleton
class RoadGradeCalculator @Inject constructor() {

    companion object {
        const val GRADE_SAFE_THRESHOLD = 8.0
        const val GRADE_WARNING_THRESHOLD = 10.0
    }

    fun calculate(input: RoadGradeInput): RoadGradeOutput {
        require(input.horizontalDistanceM > 0) { "Horizontal distance must be positive" }

        val deltaH = abs(input.elevationEndM - input.elevationStartM)
        val gradePercent = (deltaH / input.horizontalDistanceM) * 100.0

        val status = when {
            gradePercent <= GRADE_SAFE_THRESHOLD -> RoadGradeStatus.SAFE
            gradePercent <= GRADE_WARNING_THRESHOLD -> RoadGradeStatus.WARNING
            else -> RoadGradeStatus.CRITICAL
        }

        return RoadGradeOutput(
            gradePercent = gradePercent,
            deltaH = deltaH,
            status = status
        )
    }
}
