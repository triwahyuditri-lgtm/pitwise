package com.example.pitwise.domain.calculator

import com.example.pitwise.domain.model.HaulingCycleInput
import com.example.pitwise.domain.model.HaulingCycleOutput
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calculates hauling cycle time, trips per hour, and production per unit.
 *
 * Travel Loaded (min) = (Distance_m / 1000) / Speed_loaded_kmh × 60
 * Travel Empty  (min) = (Distance_m / 1000) / Speed_empty_kmh × 60
 * Cycle Time    (min) = Travel Loaded + Travel Empty + Loading Time + Dumping Time
 * Trips/Hour          = 60 / Cycle Time
 * Production/Unit     = Trips/Hour × Vessel Capacity
 */
@Singleton
class HaulingCycleCalculator @Inject constructor() {

    fun calculate(input: HaulingCycleInput): HaulingCycleOutput {
        require(input.distanceM > 0) { "Distance must be positive" }
        require(input.speedLoadedKmh > 0) { "Speed loaded must be positive" }
        require(input.speedEmptyKmh > 0) { "Speed empty must be positive" }
        require(input.loadingTimeMin >= 0) { "Loading time must be non-negative" }
        require(input.dumpingTimeMin >= 0) { "Dumping time must be non-negative" }

        val distanceKm = input.distanceM / 1000.0
        val travelLoadedMin = (distanceKm / input.speedLoadedKmh) * 60.0
        val travelEmptyMin = (distanceKm / input.speedEmptyKmh) * 60.0
        val cycleTimeMin = travelLoadedMin + travelEmptyMin + input.loadingTimeMin + input.dumpingTimeMin

        val tripsPerHour = if (cycleTimeMin > 0) 60.0 / cycleTimeMin else 0.0
        val productionPerUnit = if (input.vesselCapacityM3 > 0) {
            tripsPerHour * input.vesselCapacityM3
        } else {
            0.0
        }

        return HaulingCycleOutput(
            cycleTimeMin = cycleTimeMin,
            tripsPerHour = tripsPerHour,
            productionPerUnit = productionPerUnit
        )
    }
}
