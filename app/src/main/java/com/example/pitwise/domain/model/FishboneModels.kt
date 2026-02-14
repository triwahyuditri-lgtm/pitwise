package com.example.pitwise.domain.model

// ── Fishbone Categories ───────────────────────────────
enum class FishboneCategory(val label: String) {
    ALAT("Alat / Equipment"),
    MANUSIA("Manusia / Operator"),
    METODE("Metode / Method"),
    MATERIAL("Material"),
    JALAN("Jalan / Road"),
    CUACA("Cuaca / Weather")
}

enum class FishboneSeverity { LOW, MEDIUM, HIGH }

data class FishboneCause(
    val category: FishboneCategory,
    val symptom: String,
    val description: String,
    val severity: FishboneSeverity
)

// ── Fishbone Analysis Input ───────────────────────────
data class FishboneAnalysisInput(
    val cycleTimeActualSec: Double,
    val cycleTimeReferenceSec: Double,
    val fillFactorActual: Double,
    val fillFactorReference: Double,
    val roadGradePercent: Double,
    val loadingDelayMinutes: Double,
    val haulingDelayMinutes: Double,
    val weatherDelayMinutes: Double,
    val roadConditionDelayMinutes: Double,
    val operatorDelayMinutes: Double
)

data class FishboneAnalysisResult(
    val mainProblem: String,
    val causes: List<FishboneCause>,
    val dominantCategory: FishboneCategory
)
