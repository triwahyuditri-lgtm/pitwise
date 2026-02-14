package com.example.pitwise.data.importer

import android.util.Log

/**
 * Validates parsed unit rows before database insertion.
 * Enforces data quality rules and returns detailed error info for invalid rows.
 */
class BaseDataValidator {

    companion object {
        private const val TAG = "BaseDataValidator"

        // Valid mapped categories
        val VALID_CATEGORIES = setOf("EXCA", "DT", "DOZER")

        // Category mapping from Excel values to internal values
        val CATEGORY_MAP = mapOf(
            "digger" to "EXCA",
            "loader" to "EXCA",
            "excavator" to "EXCA",
            "exca" to "EXCA",
            "hauler" to "DT",
            "dump truck" to "DT",
            "dt" to "DT",
            "bulldozer" to "DOZER",
            "dozer" to "DOZER"
        )

        /**
         * Map raw category string to internal category.
         * Returns null if unrecognized.
         */
        fun mapCategory(rawCategory: String): String? {
            val normalized = rawCategory.lowercase().trim()
            return CATEGORY_MAP[normalized]
        }
    }

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String> = emptyList()
    )

    /**
     * Validate a parsed unit row.
     * Returns ValidationResult with detailed error messages.
     */
    fun validate(row: ParsedUnitRow, rowIndex: Int, sheet: String): ValidationResult {
        val errors = mutableListOf<String>()

        // Rule 1: Category must be valid
        if (row.mappedCategory !in VALID_CATEGORIES) {
            errors.add("Row $rowIndex [$sheet]: Invalid category '${row.category}' (mapped: '${row.mappedCategory}')")
        }

        // Rule 2: Class/model name must not be empty
        if (row.className.isBlank()) {
            errors.add("Row $rowIndex [$sheet]: Model/class name is empty")
        }

        // Rule 3: At least one capacity field must be present
        val hasCapacity = when (row.mappedCategory) {
            "EXCA" -> (row.bucketCapacityBcm != null || row.bucketCapacityLcm != null)
            "DT" -> (row.vesselLcm != null || row.vesselBcmOrTon != null)
            "DOZER" -> (row.bladeVolumeM3 != null || row.bladeWidthM != null)
            else -> false
        }
        if (!hasCapacity) {
            errors.add("Row $rowIndex [$sheet]: No capacity field found for category '${row.mappedCategory}'")
        }

        if (errors.isNotEmpty()) {
            errors.forEach { Log.w(TAG, it) }
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }
}
