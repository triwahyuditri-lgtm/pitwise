package com.example.pitwise.domain.model

// ── OB Volume Calculator ──────────────────────────────
data class ObVolumeInput(
    val areaSqM: Double,
    val thicknessM: Double,
    val swellFactor: Double = 0.8,
    val densityTonPerM3: Double = 2.2
)

data class ObVolumeOutput(
    val volumeBcm: Double,
    val volumeLcm: Double,
    val tonnage: Double
)

// ── Coal Tonnage Calculator ───────────────────────────
data class CoalTonnageInput(
    val areaSqM: Double,
    val seamThicknessM: Double,
    val densityTonPerM3: Double = 1.3,
    val recoveryPercent: Double = 100.0
)

data class CoalTonnageOutput(
    val romTonnage: Double,
    val cleanCoalTonnage: Double
)

// ── Hauling Cycle Time Calculator ─────────────────────
data class HaulingCycleInput(
    val distanceM: Double,
    val speedLoadedKmh: Double,
    val speedEmptyKmh: Double,
    val loadingTimeMin: Double,
    val dumpingTimeMin: Double,
    val vesselCapacityM3: Double = 0.0
)

data class HaulingCycleOutput(
    val cycleTimeMin: Double,
    val tripsPerHour: Double,
    val productionPerUnit: Double  // m³/hr (if vesselCapacity > 0)
)

// ── Road Grade Calculator ─────────────────────────────
data class RoadGradeInput(
    val horizontalDistanceM: Double,
    val elevationStartM: Double,
    val elevationEndM: Double
)

enum class RoadGradeStatus { SAFE, WARNING, CRITICAL }

data class RoadGradeOutput(
    val gradePercent: Double,
    val deltaH: Double,
    val status: RoadGradeStatus
)

// ── Cut & Fill Calculator ─────────────────────────────
data class CutFillInput(
    val existingElevationM: Double,
    val targetElevationM: Double,
    val roadWidthM: Double,
    val segmentLengthM: Double
)

data class CutFillOutput(
    val cutVolumeM3: Double,
    val fillVolumeM3: Double,
    val netVolumeM3: Double  // positive = net cut, negative = net fill
)
