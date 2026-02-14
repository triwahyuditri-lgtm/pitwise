package com.example.pitwise.domain.calculator

import com.example.pitwise.domain.model.DelayCategory
import com.example.pitwise.domain.model.DelayInput
import com.example.pitwise.domain.model.DelayResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calculates production loss from operational delays.
 *
 * Production Loss = (Total Delay Minutes / 60) × Actual Productivity Per Hour
 * Dominant Factor = delay category with the highest total duration
 */
@Singleton
class DelayLossCalculator @Inject constructor() {

    fun calculate(input: DelayInput): DelayResult {
        require(input.delays.isNotEmpty()) { "At least one delay entry is required" }
        require(input.actualProductivityPerHour > 0) { "Actual productivity must be positive" }

        // Aggregate duration by category
        val byCategory = input.delays
            .groupBy { it.category }
            .mapValues { (_, entries) -> entries.sumOf { it.durationMinutes } }

        val totalDelayMinutes = byCategory.values.sum()
        val totalLoss = (totalDelayMinutes / 60.0) * input.actualProductivityPerHour

        // Loss breakdown per category
        val breakdownByCategory = byCategory.mapValues { (_, minutes) ->
            (minutes / 60.0) * input.actualProductivityPerHour
        }

        // Dominant factor = category with most delay minutes
        val dominantFactor = byCategory.maxByOrNull { it.value }?.key
            ?: input.delays.first().category

        return DelayResult(
            totalDelayMinutes = totalDelayMinutes,
            productionLoss = totalLoss,
            dominantFactor = dominantFactor,
            breakdownByCategory = breakdownByCategory
        )
    }
}
