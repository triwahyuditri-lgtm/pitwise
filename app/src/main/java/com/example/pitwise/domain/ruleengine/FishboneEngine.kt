package com.example.pitwise.domain.ruleengine

import com.example.pitwise.domain.model.FishboneAnalysisInput
import com.example.pitwise.domain.model.FishboneAnalysisResult
import com.example.pitwise.domain.model.FishboneCategory
import com.example.pitwise.domain.model.FishboneCause
import com.example.pitwise.domain.model.FishboneSeverity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic Fishbone (Ishikawa) root-cause analysis engine.
 *
 * Maps real production data to fishbone categories:
 *   - Cycle time ↑          → METODE / JALAN
 *   - Fill factor ↓         → ALAT / MANUSIA
 *   - Grade > 10%           → JALAN
 *   - Loading delay ↑       → MANUSIA
 *   - Weather delay          → CUACA
 *   - Road condition delay   → JALAN
 *   - Hauling delay ↑       → METODE
 *   - Operator delay ↑      → MANUSIA
 *
 * All mappings are rule-based — no probabilities, no ML.
 */
@Singleton
class FishboneEngine @Inject constructor() {

    companion object {
        /** Thresholds for triggering fishbone causes */
        private const val CYCLE_TIME_DEVIATION_THRESHOLD = 1.10    // 10% over reference
        private const val FILL_FACTOR_DEVIATION_THRESHOLD = 0.90   // 10% below reference
        private const val GRADE_CRITICAL_THRESHOLD = 10.0          // %
        private const val GRADE_WARNING_THRESHOLD = 8.0            // %
        private const val DELAY_MINOR_THRESHOLD = 5.0              // minutes
        private const val DELAY_MAJOR_THRESHOLD = 15.0             // minutes
    }

    fun analyze(input: FishboneAnalysisInput): FishboneAnalysisResult {
        val causes = mutableListOf<FishboneCause>()

        // ── Cycle Time Analysis ───────────────────────
        val cycleTimeRatio = if (input.cycleTimeReferenceSec > 0) {
            input.cycleTimeActualSec / input.cycleTimeReferenceSec
        } else 1.0

        if (cycleTimeRatio > CYCLE_TIME_DEVIATION_THRESHOLD) {
            val severity = if (cycleTimeRatio > 1.25) FishboneSeverity.HIGH else FishboneSeverity.MEDIUM
            causes.add(
                FishboneCause(
                    category = FishboneCategory.METODE,
                    symptom = "Cycle time +${((cycleTimeRatio - 1.0) * 100).toInt()}% dari referensi",
                    description = "Cycle time aktual (${input.cycleTimeActualSec.toInt()}s) melebihi referensi " +
                            "(${input.cycleTimeReferenceSec.toInt()}s). Periksa metode loading/dumping.",
                    severity = severity
                )
            )
            causes.add(
                FishboneCause(
                    category = FishboneCategory.JALAN,
                    symptom = "Cycle time tinggi, kemungkinan kondisi jalan",
                    description = "Kecepatan hauling menurun akibat kondisi jalan suboptimal.",
                    severity = if (severity == FishboneSeverity.HIGH) FishboneSeverity.MEDIUM else FishboneSeverity.LOW
                )
            )
        }

        // ── Fill Factor Analysis ──────────────────────
        val fillFactorRatio = if (input.fillFactorReference > 0) {
            input.fillFactorActual / input.fillFactorReference
        } else 1.0

        if (fillFactorRatio < FILL_FACTOR_DEVIATION_THRESHOLD) {
            val severity = if (fillFactorRatio < 0.75) FishboneSeverity.HIGH else FishboneSeverity.MEDIUM
            causes.add(
                FishboneCause(
                    category = FishboneCategory.ALAT,
                    symptom = "Fill factor ${(input.fillFactorActual * 100).toInt()}% (target: ${(input.fillFactorReference * 100).toInt()}%)",
                    description = "Bucket tidak terisi optimal. Periksa kondisi bucket, teeth, atau material properties.",
                    severity = severity
                )
            )
            causes.add(
                FishboneCause(
                    category = FishboneCategory.MANUSIA,
                    symptom = "Fill factor rendah, kemungkinan skill operator",
                    description = "Operator mungkin kurang berpengalaman dalam teknik loading optimal.",
                    severity = FishboneSeverity.MEDIUM
                )
            )
        }

        // ── Road Grade Analysis ───────────────────────
        if (input.roadGradePercent > GRADE_CRITICAL_THRESHOLD) {
            causes.add(
                FishboneCause(
                    category = FishboneCategory.JALAN,
                    symptom = "Grade jalan ${input.roadGradePercent.toInt()}% (> ${GRADE_CRITICAL_THRESHOLD.toInt()}%)",
                    description = "Grade jalan melebihi batas aman. Kecepatan hauling turun drastis, " +
                            "fuel burn dan tire wear meningkat.",
                    severity = FishboneSeverity.HIGH
                )
            )
        } else if (input.roadGradePercent > GRADE_WARNING_THRESHOLD) {
            causes.add(
                FishboneCause(
                    category = FishboneCategory.JALAN,
                    symptom = "Grade jalan ${input.roadGradePercent.toInt()}% (warning zone)",
                    description = "Grade jalan di zona warning. Monitor kecepatan dan fuel consumption.",
                    severity = FishboneSeverity.MEDIUM
                )
            )
        }

        // ── Loading Delay Analysis ────────────────────
        analyzeDelay(
            causes = causes,
            delayMinutes = input.loadingDelayMinutes,
            category = FishboneCategory.MANUSIA,
            symptomPrefix = "Loading delay",
            description = "Delay pada proses loading. Periksa ketersediaan front, " +
                    "kesiapan operator, atau kondisi material."
        )

        // ── Hauling Delay Analysis ────────────────────
        analyzeDelay(
            causes = causes,
            delayMinutes = input.haulingDelayMinutes,
            category = FishboneCategory.METODE,
            symptomPrefix = "Hauling delay",
            description = "Delay pada proses hauling. Periksa traffic management, " +
                    "rute hauling, atau bottleneck di disposal."
        )

        // ── Weather Delay Analysis ────────────────────
        analyzeDelay(
            causes = causes,
            delayMinutes = input.weatherDelayMinutes,
            category = FishboneCategory.CUACA,
            symptomPrefix = "Weather delay",
            description = "Operasi terganggu cuaca. Faktor eksternal di luar kendali operasional."
        )

        // ── Road Condition Delay Analysis ─────────────
        analyzeDelay(
            causes = causes,
            delayMinutes = input.roadConditionDelayMinutes,
            category = FishboneCategory.JALAN,
            symptomPrefix = "Road condition delay",
            description = "Delay akibat kondisi jalan (lumpur, retak, permukaan buruk). " +
                    "Perlu maintenance road."
        )

        // ── Operator Delay Analysis ───────────────────
        analyzeDelay(
            causes = causes,
            delayMinutes = input.operatorDelayMinutes,
            category = FishboneCategory.MANUSIA,
            symptomPrefix = "Operator delay",
            description = "Delay terkait operator (fatigue, kurang terampil, break berlebih)."
        )

        // ── Determine Dominant Category ───────────────
        val dominantCategory = causes
            .groupBy { it.category }
            .mapValues { entry ->
                entry.value.sumOf { cause: FishboneCause ->
                    when (cause.severity) {
                        FishboneSeverity.HIGH -> 3L
                        FishboneSeverity.MEDIUM -> 2L
                        FishboneSeverity.LOW -> 1L
                    }
                }
            }
            .maxByOrNull { it.value }?.key
            ?: FishboneCategory.METODE

        return FishboneAnalysisResult(
            mainProblem = "Produktivitas tidak tercapai",
            causes = causes.sortedByDescending { it.severity.ordinal },
            dominantCategory = dominantCategory
        )
    }

    private fun analyzeDelay(
        causes: MutableList<FishboneCause>,
        delayMinutes: Double,
        category: FishboneCategory,
        symptomPrefix: String,
        description: String
    ) {
        if (delayMinutes > DELAY_MAJOR_THRESHOLD) {
            causes.add(
                FishboneCause(
                    category = category,
                    symptom = "$symptomPrefix ${delayMinutes.toInt()} menit (HIGH)",
                    description = description,
                    severity = FishboneSeverity.HIGH
                )
            )
        } else if (delayMinutes > DELAY_MINOR_THRESHOLD) {
            causes.add(
                FishboneCause(
                    category = category,
                    symptom = "$symptomPrefix ${delayMinutes.toInt()} menit",
                    description = description,
                    severity = FishboneSeverity.MEDIUM
                )
            )
        }
    }
}
