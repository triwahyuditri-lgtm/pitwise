package com.example.pitwise.domain.ruleengine

import com.example.pitwise.domain.model.AdvisorInput
import com.example.pitwise.domain.model.AdvisorOutput
import com.example.pitwise.domain.model.AdvisorRecommendation
import com.example.pitwise.domain.model.DelayCategory
import com.example.pitwise.domain.model.FishboneCategory
import com.example.pitwise.domain.model.FishboneSeverity
import com.example.pitwise.domain.model.ProductivityStatus
import com.example.pitwise.domain.model.ResistanceInput
import com.example.pitwise.domain.model.ResistanceResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rule-based AI Advisor engine.
 *
 * NOT a chatbot. NOT an LLM.
 * Produces max 3 actionable recommendations based on:
 *   - Productivity deviation
 *   - Fishbone root causes
 *   - Total resistance (grade + rolling)
 *   - Delay data
 *
 * Total Resistance = Road Grade (%) + Rolling Resistance (%)
 * Warning if Total Resistance > 12%
 */
@Singleton
class AdvisorEngine @Inject constructor() {

    companion object {
        private const val MAX_RECOMMENDATIONS = 3
    }

    // ── Total Resistance Calculator ───────────────────
    fun calculateResistance(input: ResistanceInput): ResistanceResult {
        val rollingResistance = input.surfaceType.rollingResistancePercent
        val totalResistance = input.roadGradePercent + rollingResistance
        val hasWarning = totalResistance > EngineeringDefaults.TOTAL_RESISTANCE_WARNING_THRESHOLD

        return ResistanceResult(
            totalResistancePercent = totalResistance,
            roadGradePercent = input.roadGradePercent,
            rollingResistancePercent = rollingResistance,
            hasWarning = hasWarning,
            warningMessage = if (hasWarning) {
                "Total resistance ${totalResistance.toInt()}% melebihi batas ${EngineeringDefaults.TOTAL_RESISTANCE_WARNING_THRESHOLD.toInt()}%. " +
                        "Risiko tinggi: fuel burn berlebih & keausan ban meningkat."
            } else null
        )
    }

    // ── Main Advisor Logic ────────────────────────────
    fun advise(input: AdvisorInput): AdvisorOutput {
        val recommendations = mutableListOf<AdvisorRecommendation>()
        val summaryParts = mutableListOf<String>()

        // ── Rule 1: Productivity Deviation ────────────
        input.productivityResult?.let { prod ->
            if (prod.status != ProductivityStatus.GREEN) {
                val devStr = "%.1f".format(prod.deviationPercent)
                summaryParts.add("Produksi ${devStr}% di bawah target")

                recommendations.add(
                    AdvisorRecommendation(
                        cause = "Produktivitas aktual (${"%.0f".format(prod.totalProduction)}) di bawah target (${"%.0f".format(prod.targetProduction)})",
                        impact = "Kehilangan produksi ${"%.0f".format(prod.targetProduction - prod.totalProduction)} " +
                                if (prod.status == ProductivityStatus.RED) "(KRITIS)" else "(PERHATIAN)",
                        actionItem = when (prod.status) {
                            ProductivityStatus.RED -> "Evaluasi segera: periksa cycle time, fill factor, dan delay. Pertimbangkan tambah unit."
                            ProductivityStatus.YELLOW -> "Monitor ketat: optimasi cycle time dan minimasi delay."
                            else -> ""
                        }
                    )
                )
            }
        }

        // ── Rule 2: Fishbone Dominant Cause ───────────
        input.fishboneResult?.let { fb ->
            val highCauses = fb.causes.filter { it.severity == FishboneSeverity.HIGH }
            if (highCauses.isNotEmpty() && recommendations.size < MAX_RECOMMENDATIONS) {
                val topCause = highCauses.first()
                summaryParts.add("Penyebab utama: ${topCause.category.label}")

                recommendations.add(
                    AdvisorRecommendation(
                        cause = topCause.symptom,
                        impact = topCause.description,
                        actionItem = generateFishboneAction(topCause.category)
                    )
                )
            }
        }

        // ── Rule 3: Total Resistance Warning ──────────
        input.resistanceResult?.let { resistance ->
            if (resistance.hasWarning && recommendations.size < MAX_RECOMMENDATIONS) {
                summaryParts.add("Grade jalan ${resistance.roadGradePercent.toInt()}%, " +
                        "total resistance ${resistance.totalResistancePercent.toInt()}%")

                recommendations.add(
                    AdvisorRecommendation(
                        cause = "Total resistance ${"%.1f".format(resistance.totalResistancePercent)}% " +
                                "(grade ${"%.1f".format(resistance.roadGradePercent)}% + " +
                                "rolling ${"%.1f".format(resistance.rollingResistancePercent)}%)",
                        impact = resistance.warningMessage ?: "Fuel burn & tire wear meningkat.",
                        actionItem = "Perbaiki segmen jalan dengan grade tinggi atau reroute hauling. " +
                                "Pertimbangkan perbaikan surface jalan untuk kurangi rolling resistance."
                    )
                )
            }
        }

        // ── Rule 4: Delay-based (if still have room) ─
        input.delayResult?.let { delay ->
            if (recommendations.size < MAX_RECOMMENDATIONS && delay.totalDelayMinutes > 15) {
                val dominantLabel = delay.dominantFactor.toLabel()
                summaryParts.add("Delay dominan: $dominantLabel (${delay.totalDelayMinutes.toInt()} menit)")

                recommendations.add(
                    AdvisorRecommendation(
                        cause = "Delay total ${delay.totalDelayMinutes.toInt()} menit, dominan pada $dominantLabel",
                        impact = "Kehilangan produksi ${"%.0f".format(delay.productionLoss)} akibat delay",
                        actionItem = generateDelayAction(delay.dominantFactor)
                    )
                )
            }
        }

        // Cap at 3 recommendations
        val finalRecs = recommendations.take(MAX_RECOMMENDATIONS)

        val summary = if (summaryParts.isNotEmpty()) {
            summaryParts.joinToString(". ") + "."
        } else {
            "Semua parameter operasi dalam batas normal. Pertahankan kinerja saat ini."
        }

        return AdvisorOutput(
            summary = summary,
            recommendations = finalRecs
        )
    }

    private fun generateFishboneAction(category: FishboneCategory): String {
        return when (category) {
            FishboneCategory.ALAT ->
                "Periksa kondisi bucket/teeth excavator. Lakukan inspeksi mekanis. Pastikan preventive maintenance terjadwal."
            FishboneCategory.MANUSIA ->
                "Evaluasi kinerja operator. Berikan coaching on-the-spot. Rotasi operator jika perlu."
            FishboneCategory.METODE ->
                "Review metode loading dan dumping. Optimasi posisi excavator dan truck spotting."
            FishboneCategory.MATERIAL ->
                "Periksa karakteristik material (hardness, moisture). Sesuaikan bucket dan metode digging."
            FishboneCategory.JALAN ->
                "Perbaiki segmen jalan bermasalah. Grading ulang road surface. Kontrol drainase."
            FishboneCategory.CUACA ->
                "Siapkan contingency plan cuaca buruk. Alokasi waktu tambahan di planning. Pastikan drainage memadai."
        }
    }

    private fun generateDelayAction(category: DelayCategory): String {
        return when (category) {
            DelayCategory.LOADING_DELAY ->
                "Optimalkan front loading: pastikan material ready, kurangi waiting time truck di loading point."
            DelayCategory.HAULING_DELAY ->
                "Review rute hauling, minimasi bottleneck di persimpangan. Atur traffic management."
            DelayCategory.ROAD_CONDITION ->
                "Prioritaskan road maintenance. Grading dan watering sesuai jadwal."
            DelayCategory.OPERATOR ->
                "Review pola kerja operator. Pastikan istirahat cukup, berikan training jika diperlukan."
            DelayCategory.WEATHER ->
                "Faktor cuaca di luar kendali. Siapkan rencana shift alternatif dan drainage darurat."
        }
    }

    private fun DelayCategory.toLabel(): String = when (this) {
        DelayCategory.LOADING_DELAY -> "Loading"
        DelayCategory.HAULING_DELAY -> "Hauling"
        DelayCategory.ROAD_CONDITION -> "Kondisi Jalan"
        DelayCategory.OPERATOR -> "Operator"
        DelayCategory.WEATHER -> "Cuaca"
    }
}
