package com.example.pitwise.domain.model

// ── Productivity Engine ───────────────────────────────
data class ProductivityInput(
    val unitName: String,
    val bucketOrVesselM3: Double,
    val fillFactor: Double = 0.85,
    val cycleTimeActualSec: Double,
    val effectiveWorkingHours: Double,
    val targetProduction: Double  // BCM or ton
)

enum class ProductivityStatus { GREEN, YELLOW, RED }

data class ProductivityResult(
    val actualProductivityPerHour: Double,
    val totalProduction: Double,
    val targetProduction: Double,
    val deviationPercent: Double,
    val status: ProductivityStatus
)

// ── Match Factor ──────────────────────────────────────
data class MatchFactorInput(
    val numTrucks: Int,
    val excaCycleTimeSec: Double,
    val truckCycleTimeSec: Double
)

enum class MatchFactorStatus { EXCAVATOR_IDLE, IDEAL, TRUCK_IDLE }

data class MatchFactorResult(
    val matchFactor: Double,
    val status: MatchFactorStatus,
    val recommendation: String
)

// ── Delay & Loss ──────────────────────────────────────
enum class DelayCategory {
    LOADING_DELAY,
    HAULING_DELAY,
    ROAD_CONDITION,
    OPERATOR,
    WEATHER
}

data class DelayEntry(
    val category: DelayCategory,
    val durationMinutes: Double
)

data class DelayInput(
    val delays: List<DelayEntry>,
    val actualProductivityPerHour: Double  // BCM/hr or ton/hr
)

data class DelayResult(
    val totalDelayMinutes: Double,
    val productionLoss: Double,       // BCM or ton
    val dominantFactor: DelayCategory,
    val breakdownByCategory: Map<DelayCategory, Double>  // category → loss
)
