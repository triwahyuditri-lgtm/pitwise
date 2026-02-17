package com.example.pitwise.domain.calculator

import com.example.pitwise.domain.model.ProductivityInput
import com.example.pitwise.domain.model.ProductivityResult
import com.example.pitwise.domain.model.ProductivityStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calculates actual productivity per shift and deviation from target.
 *
 * Productivity/hr = (Bucket × FillFactor × 3600) / CycleTime_sec
 * Total Production = Productivity/hr × Effective Hours
 * Deviation (%) = ((Actual - Target) / Target) × 100
 *
 * Status:
 *   GREEN  → deviation ≥ 0%
 *   YELLOW → deviation between -10% and 0%
 *   RED    → deviation < -10%
 */
@Singleton
class ProductivityCalculator @Inject constructor() {

    companion object {
        const val YELLOW_THRESHOLD = -10.0
    }

    fun calculate(input: ProductivityInput): ProductivityResult? {
        // Validate inputs — return null instead of crashing
        if (input.bucketOrVesselM3 <= 0 ||
            input.cycleTimeActualSec <= 0 ||
            input.effectiveWorkingHours <= 0 ||
            input.targetProduction <= 0 ||
            input.fillFactor <= 0 || input.fillFactor > 1.0
        ) return null

        val productivityPerHour = (input.bucketOrVesselM3 * input.fillFactor * 3600.0) / input.cycleTimeActualSec
        val totalProduction = productivityPerHour * input.effectiveWorkingHours
        val deviation = ((totalProduction - input.targetProduction) / input.targetProduction) * 100.0

        val status = when {
            deviation >= 0 -> ProductivityStatus.GREEN
            deviation >= YELLOW_THRESHOLD -> ProductivityStatus.YELLOW
            else -> ProductivityStatus.RED
        }

        return ProductivityResult(
            actualProductivityPerHour = productivityPerHour,
            totalProduction = totalProduction,
            targetProduction = input.targetProduction,
            deviationPercent = deviation,
            status = status
        )
    }
}
