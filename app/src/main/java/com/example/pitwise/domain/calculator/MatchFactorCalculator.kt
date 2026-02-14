package com.example.pitwise.domain.calculator

import com.example.pitwise.domain.model.MatchFactorInput
import com.example.pitwise.domain.model.MatchFactorResult
import com.example.pitwise.domain.model.MatchFactorStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calculates the Match Factor between excavator and truck fleet.
 *
 * MF = (Number of Trucks × Excavator Cycle Time) / Truck Cycle Time
 *
 * Interpretation:
 *   MF < 0.9  → Excavator is idle (not enough trucks)
 *   MF 0.9–1.1 → Ideal balance
 *   MF > 1.1  → Trucks are idle (too many trucks or slow excavator)
 */
@Singleton
class MatchFactorCalculator @Inject constructor() {

    companion object {
        const val MF_LOW_THRESHOLD = 0.9
        const val MF_HIGH_THRESHOLD = 1.1
    }

    fun calculate(input: MatchFactorInput): MatchFactorResult {
        require(input.numTrucks > 0) { "Number of trucks must be positive" }
        require(input.excaCycleTimeSec > 0) { "Excavator cycle time must be positive" }
        require(input.truckCycleTimeSec > 0) { "Truck cycle time must be positive" }

        val mf = (input.numTrucks.toDouble() * input.excaCycleTimeSec) / input.truckCycleTimeSec

        val status: MatchFactorStatus
        val recommendation: String

        when {
            mf < MF_LOW_THRESHOLD -> {
                status = MatchFactorStatus.EXCAVATOR_IDLE
                recommendation = "Excavator menganggur. Tambah ${calculateTrucksNeeded(input)} DT " +
                        "atau kurangi idle time excavator. MF saat ini: ${"%.2f".format(mf)}"
            }
            mf > MF_HIGH_THRESHOLD -> {
                status = MatchFactorStatus.TRUCK_IDLE
                recommendation = "Truck menganggur. Kurangi ${calculateExcessTrucks(input)} DT " +
                        "atau percepat loading. MF saat ini: ${"%.2f".format(mf)}"
            }
            else -> {
                status = MatchFactorStatus.IDEAL
                recommendation = "Fleet balance ideal. MF: ${"%.2f".format(mf)}. Pertahankan konfigurasi saat ini."
            }
        }

        return MatchFactorResult(
            matchFactor = mf,
            status = status,
            recommendation = recommendation
        )
    }

    /**
     * How many more trucks needed to reach MF = 1.0
     */
    private fun calculateTrucksNeeded(input: MatchFactorInput): Int {
        val idealTrucks = input.truckCycleTimeSec / input.excaCycleTimeSec
        val deficit = idealTrucks - input.numTrucks
        return if (deficit > 0) kotlin.math.ceil(deficit).toInt() else 0
    }

    /**
     * How many excess trucks beyond MF = 1.0
     */
    private fun calculateExcessTrucks(input: MatchFactorInput): Int {
        val idealTrucks = input.truckCycleTimeSec / input.excaCycleTimeSec
        val excess = input.numTrucks - idealTrucks
        return if (excess > 0) kotlin.math.floor(excess).toInt() else 0
    }
}
