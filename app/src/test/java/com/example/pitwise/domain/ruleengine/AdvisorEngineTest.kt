package com.example.pitwise.domain.ruleengine

import com.example.pitwise.domain.model.AdvisorInput
import com.example.pitwise.domain.model.DelayCategory
import com.example.pitwise.domain.model.DelayResult
import com.example.pitwise.domain.model.FishboneAnalysisResult
import com.example.pitwise.domain.model.FishboneCategory
import com.example.pitwise.domain.model.FishboneCause
import com.example.pitwise.domain.model.FishboneSeverity
import com.example.pitwise.domain.model.ProductivityResult
import com.example.pitwise.domain.model.ProductivityStatus
import com.example.pitwise.domain.model.ResistanceInput
import com.example.pitwise.domain.model.ResistanceResult
import com.example.pitwise.domain.model.RoadSurfaceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorEngineTest {

    private val engine = AdvisorEngine()

    // ── Resistance Tests ──────────────────────────────

    @Test
    fun `hard road below threshold no warning`() {
        val result = engine.calculateResistance(
            ResistanceInput(roadGradePercent = 8.0, surfaceType = RoadSurfaceType.HARD)
        )
        assertEquals(10.0, result.totalResistancePercent, 0.01)
        assertFalse(result.hasWarning)
        assertNull(result.warningMessage)
    }

    @Test
    fun `soft road above threshold has warning`() {
        val result = engine.calculateResistance(
            ResistanceInput(roadGradePercent = 9.0, surfaceType = RoadSurfaceType.SOFT)
        )
        assertEquals(14.0, result.totalResistancePercent, 0.01)
        assertTrue(result.hasWarning)
        assertTrue(result.warningMessage!!.contains("fuel burn"))
    }

    @Test
    fun `exactly 12 percent no warning`() {
        val result = engine.calculateResistance(
            ResistanceInput(roadGradePercent = 10.0, surfaceType = RoadSurfaceType.HARD)
        )
        assertEquals(12.0, result.totalResistancePercent, 0.01)
        assertFalse(result.hasWarning)
    }

    @Test
    fun `just above 12 percent has warning`() {
        val result = engine.calculateResistance(
            ResistanceInput(roadGradePercent = 10.5, surfaceType = RoadSurfaceType.HARD)
        )
        assertEquals(12.5, result.totalResistancePercent, 0.01)
        assertTrue(result.hasWarning)
    }

    // ── Advisor Tests ─────────────────────────────────

    @Test
    fun `all green produces no recommendations`() {
        val result = engine.advise(
            AdvisorInput(
                productivityResult = ProductivityResult(
                    actualProductivityPerHour = 200.0,
                    totalProduction = 1600.0,
                    targetProduction = 1500.0,
                    deviationPercent = 6.67,
                    status = ProductivityStatus.GREEN
                ),
                fishboneResult = FishboneAnalysisResult(
                    mainProblem = "Produktivitas tidak tercapai",
                    causes = emptyList(),
                    dominantCategory = FishboneCategory.METODE
                ),
                resistanceResult = ResistanceResult(
                    totalResistancePercent = 8.0,
                    roadGradePercent = 6.0,
                    rollingResistancePercent = 2.0,
                    hasWarning = false,
                    warningMessage = null
                ),
                delayResult = null
            )
        )

        assertTrue(result.recommendations.isEmpty())
        assertTrue(result.summary.contains("normal"))
    }

    @Test
    fun `red productivity triggers recommendation`() {
        val result = engine.advise(
            AdvisorInput(
                productivityResult = ProductivityResult(
                    actualProductivityPerHour = 75.0,
                    totalProduction = 450.0,
                    targetProduction = 600.0,
                    deviationPercent = -25.0,
                    status = ProductivityStatus.RED
                ),
                fishboneResult = null,
                resistanceResult = null,
                delayResult = null
            )
        )

        assertTrue(result.recommendations.isNotEmpty())
        assertTrue(result.recommendations.first().actionItem.contains("Evaluasi"))
    }

    @Test
    fun `max 3 recommendations even with many issues`() {
        val result = engine.advise(
            AdvisorInput(
                productivityResult = ProductivityResult(
                    actualProductivityPerHour = 75.0,
                    totalProduction = 450.0,
                    targetProduction = 600.0,
                    deviationPercent = -25.0,
                    status = ProductivityStatus.RED
                ),
                fishboneResult = FishboneAnalysisResult(
                    mainProblem = "Produktivitas tidak tercapai",
                    causes = listOf(
                        FishboneCause(FishboneCategory.JALAN, "Grade 12%", "Terlalu curam", FishboneSeverity.HIGH),
                        FishboneCause(FishboneCategory.ALAT, "Bucket rusak", "Perlu ganti", FishboneSeverity.HIGH)
                    ),
                    dominantCategory = FishboneCategory.JALAN
                ),
                resistanceResult = ResistanceResult(
                    totalResistancePercent = 15.0,
                    roadGradePercent = 10.0,
                    rollingResistancePercent = 5.0,
                    hasWarning = true,
                    warningMessage = "High fuel burn"
                ),
                delayResult = DelayResult(
                    totalDelayMinutes = 45.0,
                    productionLoss = 100.0,
                    dominantFactor = DelayCategory.ROAD_CONDITION,
                    breakdownByCategory = mapOf(DelayCategory.ROAD_CONDITION to 100.0)
                )
            )
        )

        assertTrue(result.recommendations.size <= 3)
    }

    @Test
    fun `fishbone cause generates actionable recommendation`() {
        val result = engine.advise(
            AdvisorInput(
                productivityResult = ProductivityResult(
                    actualProductivityPerHour = 100.0,
                    totalProduction = 700.0,
                    targetProduction = 800.0,
                    deviationPercent = -12.5,
                    status = ProductivityStatus.RED
                ),
                fishboneResult = FishboneAnalysisResult(
                    mainProblem = "Produktivitas tidak tercapai",
                    causes = listOf(
                        FishboneCause(FishboneCategory.JALAN, "Grade 11%", "Curam", FishboneSeverity.HIGH)
                    ),
                    dominantCategory = FishboneCategory.JALAN
                ),
                resistanceResult = null,
                delayResult = null
            )
        )

        assertEquals(2, result.recommendations.size)
        assertTrue(result.summary.contains("JALAN") || result.summary.contains("Jalan"))
    }

    @Test
    fun `delay recommendation when significant delay`() {
        val result = engine.advise(
            AdvisorInput(
                productivityResult = null,
                fishboneResult = null,
                resistanceResult = null,
                delayResult = DelayResult(
                    totalDelayMinutes = 30.0,
                    productionLoss = 60.0,
                    dominantFactor = DelayCategory.WEATHER,
                    breakdownByCategory = mapOf(DelayCategory.WEATHER to 60.0)
                )
            )
        )

        assertTrue(result.recommendations.isNotEmpty())
        assertTrue(result.recommendations.first().cause.contains("Cuaca"))
    }

    @Test
    fun `null inputs produces safe result`() {
        val result = engine.advise(
            AdvisorInput(
                productivityResult = null,
                fishboneResult = null,
                resistanceResult = null,
                delayResult = null
            )
        )

        assertTrue(result.recommendations.isEmpty())
        assertTrue(result.summary.contains("normal"))
    }
}
