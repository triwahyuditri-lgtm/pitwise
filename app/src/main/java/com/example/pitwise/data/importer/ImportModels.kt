package com.example.pitwise.data.importer

/**
 * Results and data classes for the base data import system.
 */

/**
 * Represents a single parsed row from the Excel file.
 * Contains all possible fields across all equipment types.
 */
data class ParsedUnitRow(
    val category: String,       // Original category from Excel (Digger, Hauler, Bulldozer)
    val mappedCategory: String,  // Mapped category (EXCA, DT, DOZER)
    val className: String,       // Model/class name (e.g., PC-200, DT 10 Roda, D85)
    val material: String?,       // Material type
    val sheetName: String,       // Which sheet it came from

    // Loader fields
    val bucketCapacityLcm: Double? = null,
    val bucketCapacityBcm: Double? = null,
    val fillFactor: Double? = null,
    val swellFactor: Double? = null,
    val jobEfficiency: Double? = null,
    val specificGravity: Double? = null,
    val cycleTimeSec: Double? = null,
    val productivity: Double? = null,

    // Hauler fields
    val vesselLcm: Double? = null,
    val vesselBcmOrTon: Double? = null,
    val spottingTimeSec: Double? = null,
    val travelSpeedKmh: Double? = null,
    val queueingTimeSec: Double? = null,
    val dumpingTimeSec: Double? = null,
    val returnSpeedKmh: Double? = null,

    // Dozer fields
    val bladeWidthM: Double? = null,
    val bladeHeightM: Double? = null,
    val bladeVolumeM3: Double? = null,
    val dozingLengthM: Double? = null,
    val forwardSpeedKmh: Double? = null,
    val reverseSpeedKmh: Double? = null,
    val shiftingTimeMin: Double? = null,
    val positioningTimeMin: Double? = null,
    val bladeFactor: Double? = null
)

/**
 * Result of an import operation.
 */
data class ImportResult(
    val success: Boolean,
    val totalRows: Int,
    val importedRows: Int,
    val skippedRows: Int,
    val brandsCreated: Int,
    val modelsCreated: Int,
    val specsCreated: Int,
    val errors: List<ImportError>
) {
    val summary: String
        get() = buildString {
            append("Import ${if (success) "SUCCESS" else "FAILED"}: ")
            append("$importedRows/$totalRows rows imported, ")
            append("$skippedRows skipped, ")
            append("$brandsCreated brands, $modelsCreated models, $specsCreated specs created")
            if (errors.isNotEmpty()) {
                append(", ${errors.size} errors")
            }
        }
}

/**
 * Represents an error that occurred during import.
 */
data class ImportError(
    val row: Int,
    val sheet: String,
    val message: String
)
