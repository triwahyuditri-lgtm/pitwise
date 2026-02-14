package com.example.pitwise.data.importer

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row

/**
 * Flexible Excel column header mapper.
 * Maps various header name variations to standardized field names.
 * Supports different naming conventions across sheets.
 */
class ExcelColumnMapper {

    private val headerIndices = mutableMapOf<String, Int>()

    companion object {
        // Mapping of possible header names to standardized field keys
        private val HEADER_ALIASES = mapOf(
            // Common
            "category" to "category",
            "class" to "class",
            "model" to "class",
            "material" to "material",

            // Loader/Digger
            "lcm" to "lcm",
            "bucket cap. lcm" to "lcm",
            "bucket cap." to "lcm",
            "bcm" to "bcm",
            "bucket (m3)" to "bcm",
            "bucket cap. bcm" to "bcm",
            "fill f." to "fill_factor",
            "fill factor" to "fill_factor",
            "swell f." to "swell_factor",
            "swell factor" to "swell_factor",
            "job eff." to "job_efficiency",
            "job efficiency" to "job_efficiency",
            "sg" to "specific_gravity",
            "specific gravity" to "specific_gravity",
            "cyc. time" to "cycle_time",
            "cycle time" to "cycle_time",
            "cycle time (sec)" to "cycle_time",
            "pdt'y" to "productivity",
            "productivity" to "productivity",

            // Hauler
            "vessel" to "vessel_lcm",
            "lcm" to "vessel_lcm",
            "bcm or ton" to "vessel_bcm_ton",
            "spotting (sec)" to "spotting_time",
            "spotting" to "spotting_time",
            "travel" to "travel_speed",
            "travel km/hr" to "travel_speed",
            "speed loaded (kmh)" to "travel_speed",
            "queueing time" to "queueing_time",
            "queueing time (sec)" to "queueing_time",
            "dumping (sec)" to "dumping_time",
            "dumping" to "dumping_time",
            "return" to "return_speed",
            "return km/hr" to "return_speed",
            "speed empty (kmh)" to "return_speed",

            // Dozer
            "blade width" to "blade_width",
            "blade height" to "blade_height",
            "blade volume" to "blade_volume",
            "length" to "dozing_length",
            "jarak dozing" to "dozing_length",
            "speed" to "forward_speed",
            "forward" to "forward_speed",
            "reverse" to "reverse_speed",
            "shifting" to "shifting_time",
            "shifting (min)" to "shifting_time",
            "positioning" to "positioning_time",
            "positioning (min)" to "positioning_time",
            "blade factor" to "blade_factor",
            "blade f." to "blade_factor",
            "job f.f." to "job_efficiency",
            "engine power (hp)" to "engine_power"
        )
    }

    /**
     * Parse header row and build column index map.
     * Handles multi-row headers by accumulating header text.
     * Returns the set of recognized field keys.
     */
    fun parseHeaders(headerRows: List<Row>): Set<String> {
        headerIndices.clear()

        // Build combined header names from all header rows
        val combinedHeaders = mutableMapOf<Int, String>()

        for (row in headerRows) {
            for (cellIndex in 0..row.lastCellNum) {
                val cell = row.getCell(cellIndex) ?: continue
                val text = getStringValue(cell)?.trim() ?: continue
                if (text.isBlank()) continue

                val existing = combinedHeaders[cellIndex]
                combinedHeaders[cellIndex] = if (existing != null) {
                    "$existing $text"
                } else {
                    text
                }
            }
        }

        // Map combined headers to field keys
        for ((index, headerText) in combinedHeaders) {
            val normalizedHeader = headerText.lowercase().trim()
            val fieldKey = HEADER_ALIASES[normalizedHeader]
            if (fieldKey != null) {
                headerIndices[fieldKey] = index
            }
        }

        return headerIndices.keys.toSet()
    }

    /**
     * Parse a single header row.
     */
    fun parseHeaders(headerRow: Row): Set<String> = parseHeaders(listOf(headerRow))

    /**
     * Get string value from the row for a given field key.
     */
    fun getString(row: Row, fieldKey: String): String? {
        val index = headerIndices[fieldKey] ?: return null
        val cell = row.getCell(index) ?: return null
        return getStringValue(cell)?.trim()?.takeIf { it.isNotBlank() }
    }

    /**
     * Get double value from the row for a given field key.
     */
    fun getDouble(row: Row, fieldKey: String): Double? {
        val index = headerIndices[fieldKey] ?: return null
        val cell = row.getCell(index) ?: return null
        return getDoubleValue(cell)
    }

    /**
     * Check if a field key was found in the headers.
     */
    fun hasField(fieldKey: String): Boolean = headerIndices.containsKey(fieldKey)

    /**
     * Safely extract a string value from a cell.
     */
    private fun getStringValue(cell: Cell): String? {
        return try {
            when (cell.cellType) {
                CellType.STRING -> cell.stringCellValue
                CellType.NUMERIC -> {
                    val num = cell.numericCellValue
                    if (num == num.toLong().toDouble()) {
                        num.toLong().toString()
                    } else {
                        num.toString()
                    }
                }
                CellType.BOOLEAN -> cell.booleanCellValue.toString()
                CellType.FORMULA -> {
                    try {
                        cell.stringCellValue
                    } catch (e: Exception) {
                        cell.numericCellValue.toString()
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Safely extract a double value from a cell.
     */
    private fun getDoubleValue(cell: Cell): Double? {
        return try {
            when (cell.cellType) {
                CellType.NUMERIC -> cell.numericCellValue
                CellType.STRING -> cell.stringCellValue.trim().toDoubleOrNull()
                CellType.FORMULA -> {
                    try {
                        cell.numericCellValue
                    } catch (e: Exception) {
                        cell.stringCellValue.toDoubleOrNull()
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
