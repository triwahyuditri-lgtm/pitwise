package com.example.pitwise.domain.calculator

import com.example.pitwise.domain.model.ObVolumeInput
import com.example.pitwise.domain.model.ObVolumeOutput
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calculates Overburden (OB) volume and tonnage.
 *
 * BCM  = Area × Thickness
 * LCM  = BCM / Swell Factor
 * Tonnage = BCM × Density
 */
@Singleton
class ObVolumeCalculator @Inject constructor() {

    fun calculate(input: ObVolumeInput): ObVolumeOutput {
        require(input.areaSqM > 0) { "Area must be positive" }
        require(input.thicknessM > 0) { "Thickness must be positive" }
        require(input.swellFactor > 0 && input.swellFactor <= 1.0) {
            "Swell factor must be between 0 (exclusive) and 1.0 (inclusive)"
        }
        require(input.densityTonPerM3 > 0) { "Density must be positive" }

        val volumeBcm = input.areaSqM * input.thicknessM
        val volumeLcm = volumeBcm / input.swellFactor
        val tonnage = volumeBcm * input.densityTonPerM3

        return ObVolumeOutput(
            volumeBcm = volumeBcm,
            volumeLcm = volumeLcm,
            tonnage = tonnage
        )
    }
}
