package com.example.pitwise.domain.ruleengine

import com.example.pitwise.domain.model.FishboneAnalysisInput
import com.example.pitwise.domain.model.FishboneCategory
import com.example.pitwise.domain.model.FishboneSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FishboneEngineTest {

    private val engine = FishboneEngine()

    @Test
    fun `high cycle time triggers METODE and JALAN causes`() {
        val input = FishboneAnalysisInput(
            cycleTimeActualSec = 40.0,
            cycleTimeReferenceSec = 30.0,     // 33% over → HIGH
            fillFactorActual = 0.85,
            fillFactorReference = 0.85,
            roadGradePercent = 5.0,
            loadingDelayMinutes = 0.0,
            haulingDelayMinutes = 0.0,
            weatherDelayMinutes = 0.0,
            roadConditionDelayMinutes = 0.0,
            operatorDelayMinutes = 0.0
        )
        val result = engine.analyze(input)

        val categories = result.causes.map { it.category }
        assertTrue("METODE should be present", FishboneCategory.METODE in categories)
        assertTrue("JALAN should be present", FishboneCategory.JALAN in categories)
    }

    @Test
    fun `low fill factor triggers ALAT and MANUSIA causes`() {
        val input = FishboneAnalysisInput(
            cycleTimeActualSec = 30.0,
            cycleTimeReferenceSec = 30.0,
            fillFactorActual = 0.60,
            fillFactorReference = 0.85,       // 70% of reference → HIGH
            roadGradePercent = 5.0,
            loadingDelayMinutes = 0.0,
            haulingDelayMinutes = 0.0,
            weatherDelayMinutes = 0.0,
            roadConditionDelayMinutes = 0.0,
            operatorDelayMinutes = 0.0
        )
        val result = engine.analyze(input)

        val categories = result.causes.map { it.category }
        assertTrue("ALAT should be present", FishboneCategory.ALAT in categories)
        assertTrue("MANUSIA should be present", FishboneCategory.MANUSIA in categories)
    }

    @Test
    fun `critical road grade triggers JALAN HIGH severity`() {
        val input = FishboneAnalysisInput(
            cycleTimeActualSec = 30.0,
            cycleTimeReferenceSec = 30.0,
            fillFactorActual = 0.85,
            fillFactorReference = 0.85,
            roadGradePercent = 12.0,
            loadingDelayMinutes = 0.0,
            haulingDelayMinutes = 0.0,
            weatherDelayMinutes = 0.0,
            roadConditionDelayMinutes = 0.0,
            operatorDelayMinutes = 0.0
        )
        val result = engine.analyze(input)

        val jalanCause = result.causes.first { it.category == FishboneCategory.JALAN }
        assertEquals(FishboneSeverity.HIGH, jalanCause.severity)
        assertEquals(FishboneCategory.JALAN, result.dominantCategory)
    }

    @Test
    fun `warning road grade triggers JALAN MEDIUM severity`() {
        val input = FishboneAnalysisInput(
            cycleTimeActualSec = 30.0,
            cycleTimeReferenceSec = 30.0,
            fillFactorActual = 0.85,
            fillFactorReference = 0.85,
            roadGradePercent = 9.0,
            loadingDelayMinutes = 0.0,
            haulingDelayMinutes = 0.0,
            weatherDelayMinutes = 0.0,
            roadConditionDelayMinutes = 0.0,
            operatorDelayMinutes = 0.0
        )
        val result = engine.analyze(input)

        val jalanCause = result.causes.first { it.category == FishboneCategory.JALAN }
        assertEquals(FishboneSeverity.MEDIUM, jalanCause.severity)
    }

    @Test
    fun `weather delay triggers CUACA`() {
        val input = FishboneAnalysisInput(
            cycleTimeActualSec = 30.0,
            cycleTimeReferenceSec = 30.0,
            fillFactorActual = 0.85,
            fillFactorReference = 0.85,
            roadGradePercent = 5.0,
            loadingDelayMinutes = 0.0,
            haulingDelayMinutes = 0.0,
            weatherDelayMinutes = 20.0,
            roadConditionDelayMinutes = 0.0,
            operatorDelayMinutes = 0.0
        )
        val result = engine.analyze(input)

        assertTrue(result.causes.any { it.category == FishboneCategory.CUACA })
    }

    @Test
    fun `multiple issues identifies correct dominant category`() {
        val input = FishboneAnalysisInput(
            cycleTimeActualSec = 42.0,     // 40% over → HIGH (METODE + JALAN)
            cycleTimeReferenceSec = 30.0,
            fillFactorActual = 0.60,       // 70% of ref → HIGH (ALAT + MANUSIA)
            fillFactorReference = 0.85,
            roadGradePercent = 12.0,       // 12% → HIGH (JALAN)
            loadingDelayMinutes = 20.0,    // HIGH (MANUSIA)
            haulingDelayMinutes = 10.0,    // MEDIUM (METODE)
            weatherDelayMinutes = 5.0,     // ignored (below threshold)
            roadConditionDelayMinutes = 18.0, // HIGH (JALAN)
            operatorDelayMinutes = 8.0     // MEDIUM (MANUSIA)
        )
        val result = engine.analyze(input)

        assertEquals("Produktivitas tidak tercapai", result.mainProblem)
        assertTrue(result.causes.isNotEmpty())
        // JALAN appears multiple times with high severity, should be dominant
        assertEquals(FishboneCategory.JALAN, result.dominantCategory)
    }

    @Test
    fun `no issues produces empty causes`() {
        val input = FishboneAnalysisInput(
            cycleTimeActualSec = 30.0,
            cycleTimeReferenceSec = 30.0,
            fillFactorActual = 0.85,
            fillFactorReference = 0.85,
            roadGradePercent = 5.0,
            loadingDelayMinutes = 2.0,
            haulingDelayMinutes = 1.0,
            weatherDelayMinutes = 0.0,
            roadConditionDelayMinutes = 0.0,
            operatorDelayMinutes = 0.0
        )
        val result = engine.analyze(input)

        assertTrue("Causes should be empty for normal operations", result.causes.isEmpty())
    }

    @Test
    fun `causes are sorted by severity descending`() {
        val input = FishboneAnalysisInput(
            cycleTimeActualSec = 36.0,     // 20% over → MEDIUM
            cycleTimeReferenceSec = 30.0,
            fillFactorActual = 0.85,
            fillFactorReference = 0.85,
            roadGradePercent = 12.0,       // >10% → HIGH
            loadingDelayMinutes = 0.0,
            haulingDelayMinutes = 0.0,
            weatherDelayMinutes = 0.0,
            roadConditionDelayMinutes = 0.0,
            operatorDelayMinutes = 0.0
        )
        val result = engine.analyze(input)

        // First cause should be HIGH severity
        assertEquals(FishboneSeverity.HIGH, result.causes.first().severity)
    }
}
