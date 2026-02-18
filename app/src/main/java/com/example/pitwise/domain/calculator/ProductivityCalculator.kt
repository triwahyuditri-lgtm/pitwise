package com.example.pitwise.domain.calculator

import com.example.pitwise.domain.model.ProductivityInput
import com.example.pitwise.domain.model.ProductivityResult
import com.example.pitwise.domain.model.ProductivityStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calculates actual productivity (BCM/hr) and deviation from target.
 *
 * Formula:
 *   Pdt'y (BCM/hr) = (3600 × BucketCap_LCM × FillFactor × JobEff)
 *                     / (CycleTime_sec × SwellFactor)
 *
 * When a database productivity value is available, it takes precedence
 * over the formula calculation.
 *
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
            input.fillFactor <= 0 ||
            input.swellFactor <= 0 ||
            input.jobEfficiency <= 0
        ) return null

        // Use database productivity value if available, otherwise calculate from formula
        val productivityPerHour = if (input.dbProductivityPerHour != null && input.dbProductivityPerHour > 0) {
            input.dbProductivityPerHour
        } else {
            (input.bucketOrVesselM3 * input.fillFactor * input.jobEfficiency * 3600.0) /
                    (input.cycleTimeActualSec * input.swellFactor)
        }

        // Actual total production = productivity/hr × working hours
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
