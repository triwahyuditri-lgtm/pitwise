package com.example.pitwise.data.importer

import android.content.Context
import android.util.Log
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream
import javax.inject.Inject

/**
 * Parses the base_data_unit.xlsx Excel file from app assets.
 * Handles multi-sheet workbooks with different column layouts per sheet.
 * Performs safe parsing with null handling and type validation.
 */
class ExcelBaseDataParser @Inject constructor() {

    companion object {
        private const val TAG = "ExcelBaseDataParser"
        private const val ASSET_FILE = "base_data_unit.xlsx"

        // Known sheet types and their expected categories
        private val SHEET_TYPE_MAP = mapOf(
            "loader" to "EXCA",
            "digger" to "EXCA",
            "hauler" to "DT",
            "dump truck" to "DT",
            "dozer" to "DOZER",
            "bulldozer" to "DOZER"
        )
    }

    /**
     * Parse the Excel file from assets.
     * Returns list of all parsed rows across all sheets.
     */
    fun parseFromAssets(context: Context): List<ParsedUnitRow> {
        val allRows = mutableListOf<ParsedUnitRow>()

        try {
            context.assets.open(ASSET_FILE).use { inputStream ->
                allRows.addAll(parse(inputStream))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open asset file: $ASSET_FILE", e)
        }

        Log.i(TAG, "Total parsed rows: ${allRows.size}")
        return allRows
    }

    /**
     * Parse an Excel file from an InputStream.
     */
    fun parse(inputStream: InputStream): List<ParsedUnitRow> {
        val allRows = mutableListOf<ParsedUnitRow>()

        try {
            val workbook = WorkbookFactory.create(inputStream)

            for (sheetIndex in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheetAt(sheetIndex)
                val sheetName = sheet.sheetName ?: "Sheet$sheetIndex"

                Log.i(TAG, "Processing sheet: $sheetName (${sheet.lastRowNum + 1} rows)")

                val sheetType = detectSheetType(sheetName)
                if (sheetType == null) {
                    Log.w(TAG, "Unknown sheet type for '$sheetName', attempting auto-detect from content")
                }

                val rows = parseSheet(sheet, sheetName, sheetType)
                allRows.addAll(rows)

                Log.i(TAG, "Sheet '$sheetName': ${rows.size} rows parsed")
            }

            workbook.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Excel file", e)
        }

        return allRows
    }

    /**
     * Detect the sheet type from its name.
     */
    private fun detectSheetType(sheetName: String): String? {
        val normalized = sheetName.lowercase().trim()
        return SHEET_TYPE_MAP.entries.firstOrNull { normalized.contains(it.key) }?.value
    }

    /**
     * Parse a single sheet into ParsedUnitRow objects.
     */
    private fun parseSheet(sheet: Sheet, sheetName: String, defaultCategory: String?): List<ParsedUnitRow> {
        val rows = mutableListOf<ParsedUnitRow>()
        val mapper = ExcelColumnMapper()

        // Find header rows (first 1-3 rows typically)
        val headerRows = findHeaderRows(sheet)
        if (headerRows.isEmpty()) {
            Log.w(TAG, "No header rows found in sheet '$sheetName'")
            return rows
        }

        val recognizedFields = mapper.parseHeaders(headerRows)
        Log.d(TAG, "Recognized fields in '$sheetName': $recognizedFields")

        val dataStartRow = headerRows.last().rowNum + 1

        // Parse data rows
        for (rowIndex in dataStartRow..sheet.lastRowNum) {
            val row = sheet.getRow(rowIndex) ?: continue

            // Skip empty rows
            if (isEmptyRow(row)) continue

            val parsed = parseRow(row, mapper, sheetName, rowIndex, defaultCategory)
            if (parsed != null) {
                rows.add(parsed)
            }
        }

        return rows
    }

    /**
     * Find header rows at the top of the sheet.
     * Headers are typically in the first 1-3 rows, containing text like "Category", "Class", etc.
     */
    private fun findHeaderRows(sheet: Sheet): List<Row> {
        val headerRows = mutableListOf<Row>()

        for (rowIndex in 0..minOf(4, sheet.lastRowNum)) {
            val row = sheet.getRow(rowIndex) ?: continue
            var hasHeaderLikeContent = false

            for (cellIndex in 0..minOf(15, row.lastCellNum.toInt())) {
                val cell = row.getCell(cellIndex) ?: continue
                val text = try {
                    cell.stringCellValue?.trim()?.lowercase() ?: ""
                } catch (e: Exception) {
                    ""
                }

                if (text in listOf("category", "class", "model", "material", "brand",
                        "bucket", "vessel", "blade", "lcm", "bcm", "fill", "speed",
                        "forward", "reverse", "cycle", "travel", "return", "spotting",
                        "dumping", "queueing")) {
                    hasHeaderLikeContent = true
                    break
                }
            }

            if (hasHeaderLikeContent) {
                headerRows.add(row)
            }
        }

        return headerRows
    }

    /**
     * Parse a single data row into a ParsedUnitRow.
     */
    private fun parseRow(
        row: Row,
        mapper: ExcelColumnMapper,
        sheetName: String,
        rowIndex: Int,
        defaultCategory: String?
    ): ParsedUnitRow? {
        try {
            // Get category from cell or use default from sheet name
            val rawCategory = mapper.getString(row, "category")
            val className = mapper.getString(row, "class")

            // Skip rows with no class name (model name is required)
            if (className.isNullOrBlank()) return null

            // Skip rows where class name is "0" (empty hauler/dozer slots in the Excel)
            if (className == "0") return null

            val category = rawCategory ?: ""
            val mappedCategory = BaseDataValidator.mapCategory(category)
                ?: defaultCategory
                ?: return null

            val material = mapper.getString(row, "material")

            return ParsedUnitRow(
                category = category,
                mappedCategory = mappedCategory,
                className = className,
                material = material,
                sheetName = sheetName,

                // Loader fields
                bucketCapacityLcm = mapper.getDouble(row, "lcm"),
                bucketCapacityBcm = mapper.getDouble(row, "bcm"),
                fillFactor = mapper.getDouble(row, "fill_factor"),
                swellFactor = mapper.getDouble(row, "swell_factor"),
                jobEfficiency = mapper.getDouble(row, "job_efficiency"),
                specificGravity = mapper.getDouble(row, "specific_gravity"),
                cycleTimeSec = mapper.getDouble(row, "cycle_time"),
                productivity = mapper.getDouble(row, "productivity"),

                // Hauler fields
                vesselLcm = mapper.getDouble(row, "vessel_lcm"),
                vesselBcmOrTon = mapper.getDouble(row, "vessel_bcm_ton"),
                spottingTimeSec = mapper.getDouble(row, "spotting_time"),
                travelSpeedKmh = mapper.getDouble(row, "travel_speed"),
                queueingTimeSec = mapper.getDouble(row, "queueing_time"),
                dumpingTimeSec = mapper.getDouble(row, "dumping_time"),
                returnSpeedKmh = mapper.getDouble(row, "return_speed"),

                // Dozer fields
                bladeWidthM = mapper.getDouble(row, "blade_width"),
                bladeHeightM = mapper.getDouble(row, "blade_height"),
                bladeVolumeM3 = mapper.getDouble(row, "blade_volume"),
                dozingLengthM = mapper.getDouble(row, "dozing_length"),
                forwardSpeedKmh = mapper.getDouble(row, "forward_speed"),
                reverseSpeedKmh = mapper.getDouble(row, "reverse_speed"),
                shiftingTimeMin = mapper.getDouble(row, "shifting_time"),
                positioningTimeMin = mapper.getDouble(row, "positioning_time"),
                bladeFactor = mapper.getDouble(row, "blade_factor")
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing row $rowIndex in '$sheetName': ${e.message}")
            return null
        }
    }

    /**
     * Check if a row is effectively empty (no meaningful data).
     */
    private fun isEmptyRow(row: Row): Boolean {
        for (cellIndex in 0..minOf(5, row.lastCellNum.toInt())) {
            val cell = row.getCell(cellIndex) ?: continue
            val value = try {
                when (cell.cellType) {
                    org.apache.poi.ss.usermodel.CellType.STRING ->
                        cell.stringCellValue?.trim()?.takeIf { it.isNotBlank() }
                    org.apache.poi.ss.usermodel.CellType.NUMERIC ->
                        cell.numericCellValue.toString()
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
            if (value != null) return false
        }
        return true
    }
}
