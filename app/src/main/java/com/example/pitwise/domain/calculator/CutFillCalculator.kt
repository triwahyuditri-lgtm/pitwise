package com.example.pitwise.domain.calculator

import com.example.pitwise.domain.model.CutFillInput
import com.example.pitwise.domain.model.CutFillOutput
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Calculates cut and fill volumes for road construction.
 *
 * Δ = Existing Elevation - Target Elevation
 * If Δ > 0: Cut Volume = Δ × Road Width × Segment Length (excess material to remove)
 * If Δ < 0: Fill Volume = |Δ| × Road Width × Segment Length (material needed to add)
 * Net Volume = Cut Volume - Fill Volume (positive = net cut, negative = net fill)
 */
@Singleton
class CutFillCalculator @Inject constructor() {

    fun calculate(input: CutFillInput): CutFillOutput {
        require(input.roadWidthM > 0) { "Road width must be positive" }
        require(input.segmentLengthM > 0) { "Segment length must be positive" }

        val delta = input.existingElevationM - input.targetElevationM
        val crossSectionArea = input.roadWidthM * input.segmentLengthM

        val cutVolume: Double
        val fillVolume: Double

        if (delta > 0) {
            cutVolume = delta * crossSectionArea
            fillVolume = 0.0
        } else if (delta < 0) {
            cutVolume = 0.0
            fillVolume = abs(delta) * crossSectionArea
        } else {
            cutVolume = 0.0
            fillVolume = 0.0
        }

        return CutFillOutput(
            cutVolumeM3 = cutVolume,
            fillVolumeM3 = fillVolume,
            netVolumeM3 = cutVolume - fillVolume
        )
    }
}
