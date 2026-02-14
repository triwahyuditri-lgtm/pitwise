package com.example.pitwise.domain.model

/**
 * Lightweight data class for the unit picker UI.
 * Combines brand name + model name for display.
 */
data class UnitModelWithBrand(
    val modelId: Long,
    val brandName: String,
    val modelName: String,
    val category: String // "EXCA" or "DT"
) {
    /** Display label for the picker: "Caterpillar — CAT 320D" */
    val displayName: String get() = "$brandName — $modelName"
}
