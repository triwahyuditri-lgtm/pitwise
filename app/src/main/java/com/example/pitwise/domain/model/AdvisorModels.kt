package com.example.pitwise.domain.model

// ── Road Surface Type ─────────────────────────────────
enum class RoadSurfaceType(val rollingResistancePercent: Double) {
    HARD(2.0),
    SOFT(5.0)
}

// ── Total Resistance ──────────────────────────────────
data class ResistanceInput(
    val roadGradePercent: Double,
    val surfaceType: RoadSurfaceType
)

data class ResistanceResult(
    val totalResistancePercent: Double,
    val roadGradePercent: Double,
    val rollingResistancePercent: Double,
    val hasWarning: Boolean,
    val warningMessage: String?
)

// ── Advisor Recommendation ────────────────────────────
data class AdvisorRecommendation(
    val cause: String,
    val impact: String,
    val actionItem: String
)

data class AdvisorInput(
    val productivityResult: ProductivityResult?,
    val fishboneResult: FishboneAnalysisResult?,
    val resistanceResult: ResistanceResult?,
    val delayResult: DelayResult?,
    val unitName: String = "",
    val shift: String = ""
)

data class AdvisorOutput(
    val summary: String,
    val recommendations: List<AdvisorRecommendation>  // max 3
)

// ── Share Card Data moved to separate file

