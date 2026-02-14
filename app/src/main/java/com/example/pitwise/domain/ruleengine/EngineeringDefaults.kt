package com.example.pitwise.domain.ruleengine

/**
 * Hardcoded engineering defaults for mining operations.
 * These are industry-standard reference values used across calculators and the AI advisor.
 */
object EngineeringDefaults {

    // ── Material Properties ───────────────────────────
    /** Default swell factor for overburden */
    const val SWELL_FACTOR_OB: Double = 0.8

    /** Density of overburden in tonnes per cubic metre */
    const val DENSITY_OB_TON_PER_M3: Double = 2.2

    /** Density of coal in tonnes per cubic metre */
    const val DENSITY_COAL_TON_PER_M3: Double = 1.3

    // ── Rolling Resistance ────────────────────────────
    /** Rolling resistance on well-maintained hard road (%) */
    const val ROLLING_RESISTANCE_HARD_PERCENT: Double = 2.0

    /** Rolling resistance on soft/unmaintained road (%) */
    const val ROLLING_RESISTANCE_SOFT_PERCENT: Double = 5.0

    // ── Resistance Thresholds ─────────────────────────
    /** Total resistance threshold above which fuel burn & tire wear are at risk */
    const val TOTAL_RESISTANCE_WARNING_THRESHOLD: Double = 12.0

    // ── Road Grade Thresholds ─────────────────────────
    /** Maximum safe grade for haul road (%) */
    const val GRADE_SAFE_MAX: Double = 8.0

    /** Grade above which is warning zone (%) */
    const val GRADE_WARNING_MAX: Double = 10.0

    // ── Productivity Thresholds ───────────────────────
    /** Deviation below which status turns YELLOW (%) */
    const val PRODUCTIVITY_YELLOW_THRESHOLD: Double = -10.0

    // ── Match Factor ──────────────────────────────────
    /** MF below this → excavator idle */
    const val MF_LOW: Double = 0.9

    /** MF above this → truck idle */
    const val MF_HIGH: Double = 1.1

    // ── Default Fill Factor ───────────────────────────
    /** Typical fill factor for excavators */
    const val FILL_FACTOR_DEFAULT: Double = 0.85

    // ── Default Recovery ──────────────────────────────
    /** Default coal recovery percentage */
    const val COAL_RECOVERY_DEFAULT: Double = 85.0
}
