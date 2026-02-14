package com.example.pitwise.domain.calculator

import com.example.pitwise.domain.model.CoalTonnageInput
import com.example.pitwise.domain.model.CoalTonnageOutput
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calculates coal ROM tonnage and clean coal estimate.
 *
 * ROM Tonnage = Area × Seam Thickness × Density
 * Clean Coal  = ROM × (Recovery% / 100)
 */
@Singleton
class CoalTonnageCalculator @Inject constructor() {

    fun calculate(input: CoalTonnageInput): CoalTonnageOutput {
        require(input.areaSqM > 0) { "Area must be positive" }
        require(input.seamThicknessM > 0) { "Seam thickness must be positive" }
        require(input.densityTonPerM3 > 0) { "Density must be positive" }
        require(input.recoveryPercent in 0.0..100.0) { "Recovery must be 0-100%" }

        val romTonnage = input.areaSqM * input.seamThicknessM * input.densityTonPerM3
        val cleanCoal = romTonnage * (input.recoveryPercent / 100.0)

        return CoalTonnageOutput(
            romTonnage = romTonnage,
            cleanCoalTonnage = cleanCoal
        )
    }
}
