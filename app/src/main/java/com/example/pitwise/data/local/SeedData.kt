package com.example.pitwise.data.local

import com.example.pitwise.data.local.entity.UnitBrand
import com.example.pitwise.data.local.entity.UnitModel
import com.example.pitwise.data.local.entity.UnitSpec

/**
 * Pre-populated seed data for the PITWISE application.
 * Contains default excavator (EXCA) and dump truck (DT) units
 * with real-world specifications.
 *
 * @deprecated This is kept as a fallback. The primary data source is now
 * the Excel-based import system (BaseDataImporter) which reads from
 * assets/base_data_unit.xlsx and supports versioning and material-specific specs.
 */
object SeedData {

    // ── BRANDS ──────────────────────────────────────────
    val brands = listOf(
        UnitBrand(id = 1, name = "Caterpillar", category = "EXCA"),
        UnitBrand(id = 2, name = "Komatsu", category = "EXCA"),
        UnitBrand(id = 3, name = "Hitachi", category = "EXCA"),
        UnitBrand(id = 4, name = "Volvo", category = "EXCA"),
        UnitBrand(id = 5, name = "Caterpillar", category = "DT"),
        UnitBrand(id = 6, name = "Komatsu", category = "DT"),
        UnitBrand(id = 7, name = "Hitachi", category = "DT"),
        UnitBrand(id = 8, name = "Volvo", category = "DT"),
    )

    // ── MODELS ──────────────────────────────────────────
    val models = listOf(
        // Excavators
        UnitModel(id = 1, brandId = 1, modelName = "CAT 320D", category = "EXCA", source = "SYSTEM", version = 1),
        UnitModel(id = 2, brandId = 1, modelName = "CAT 330D", category = "EXCA", source = "SYSTEM", version = 1),
        UnitModel(id = 3, brandId = 1, modelName = "CAT 390F", category = "EXCA", source = "SYSTEM", version = 1),
        UnitModel(id = 4, brandId = 2, modelName = "PC200-8", category = "EXCA", source = "SYSTEM", version = 1),
        UnitModel(id = 5, brandId = 2, modelName = "PC300-8", category = "EXCA", source = "SYSTEM", version = 1),
        UnitModel(id = 6, brandId = 2, modelName = "PC400-8", category = "EXCA", source = "SYSTEM", version = 1),
        UnitModel(id = 7, brandId = 3, modelName = "EX200", category = "EXCA", source = "SYSTEM", version = 1),
        UnitModel(id = 8, brandId = 3, modelName = "ZX350", category = "EXCA", source = "SYSTEM", version = 1),
        UnitModel(id = 9, brandId = 4, modelName = "EC210", category = "EXCA", source = "SYSTEM", version = 1),
        UnitModel(id = 10, brandId = 4, modelName = "EC350", category = "EXCA", source = "SYSTEM", version = 1),

        // Dump Trucks
        UnitModel(id = 11, brandId = 5, modelName = "CAT 773F", category = "DT", source = "SYSTEM", version = 1),
        UnitModel(id = 12, brandId = 5, modelName = "CAT 777F", category = "DT", source = "SYSTEM", version = 1),
        UnitModel(id = 13, brandId = 5, modelName = "CAT 785D", category = "DT", source = "SYSTEM", version = 1),
        UnitModel(id = 14, brandId = 6, modelName = "HD785-7", category = "DT", source = "SYSTEM", version = 1),
        UnitModel(id = 15, brandId = 6, modelName = "HD605-8", category = "DT", source = "SYSTEM", version = 1),
        UnitModel(id = 16, brandId = 7, modelName = "EH1100-5", category = "DT", source = "SYSTEM", version = 1),
        UnitModel(id = 17, brandId = 7, modelName = "EH3500AC-3", category = "DT", source = "SYSTEM", version = 1),
        UnitModel(id = 18, brandId = 8, modelName = "A40G", category = "DT", source = "SYSTEM", version = 1),
    )

    // ── SPECS ───────────────────────────────────────────
    val specs = listOf(
        // Excavator Specs
        UnitSpec(id = 1, modelId = 1, bucketCapacityM3 = 1.2, fillFactorDefault = 0.85, cycleTimeRefSec = 28.0, enginePowerHp = 148.0, createdBy = "SYSTEM"),
        UnitSpec(id = 2, modelId = 2, bucketCapacityM3 = 1.8, fillFactorDefault = 0.85, cycleTimeRefSec = 30.0, enginePowerHp = 250.0, createdBy = "SYSTEM"),
        UnitSpec(id = 3, modelId = 3, bucketCapacityM3 = 4.6, fillFactorDefault = 0.80, cycleTimeRefSec = 35.0, enginePowerHp = 523.0, createdBy = "SYSTEM"),
        UnitSpec(id = 4, modelId = 4, bucketCapacityM3 = 0.93, fillFactorDefault = 0.85, cycleTimeRefSec = 26.0, enginePowerHp = 138.0, createdBy = "SYSTEM"),
        UnitSpec(id = 5, modelId = 5, bucketCapacityM3 = 1.6, fillFactorDefault = 0.85, cycleTimeRefSec = 30.0, enginePowerHp = 246.0, createdBy = "SYSTEM"),
        UnitSpec(id = 6, modelId = 6, bucketCapacityM3 = 2.4, fillFactorDefault = 0.80, cycleTimeRefSec = 32.0, enginePowerHp = 321.0, createdBy = "SYSTEM"),
        UnitSpec(id = 7, modelId = 7, bucketCapacityM3 = 0.80, fillFactorDefault = 0.85, cycleTimeRefSec = 25.0, enginePowerHp = 131.0, createdBy = "SYSTEM"),
        UnitSpec(id = 8, modelId = 8, bucketCapacityM3 = 2.1, fillFactorDefault = 0.85, cycleTimeRefSec = 30.0, enginePowerHp = 271.0, createdBy = "SYSTEM"),
        UnitSpec(id = 9, modelId = 9, bucketCapacityM3 = 1.1, fillFactorDefault = 0.85, cycleTimeRefSec = 27.0, enginePowerHp = 155.0, createdBy = "SYSTEM"),
        UnitSpec(id = 10, modelId = 10, bucketCapacityM3 = 1.9, fillFactorDefault = 0.85, cycleTimeRefSec = 29.0, enginePowerHp = 286.0, createdBy = "SYSTEM"),

        // Dump Truck Specs
        UnitSpec(id = 11, modelId = 11, vesselCapacityM3 = 36.6, speedLoadedKmh = 35.0, speedEmptyKmh = 55.0, enginePowerHp = 650.0, createdBy = "SYSTEM"),
        UnitSpec(id = 12, modelId = 12, vesselCapacityM3 = 60.0, speedLoadedKmh = 30.0, speedEmptyKmh = 50.0, enginePowerHp = 938.0, createdBy = "SYSTEM"),
        UnitSpec(id = 13, modelId = 13, vesselCapacityM3 = 92.0, speedLoadedKmh = 28.0, speedEmptyKmh = 48.0, enginePowerHp = 1348.0, createdBy = "SYSTEM"),
        UnitSpec(id = 14, modelId = 14, vesselCapacityM3 = 54.0, speedLoadedKmh = 32.0, speedEmptyKmh = 52.0, enginePowerHp = 1050.0, createdBy = "SYSTEM"),
        UnitSpec(id = 15, modelId = 15, vesselCapacityM3 = 33.0, speedLoadedKmh = 34.0, speedEmptyKmh = 56.0, enginePowerHp = 533.0, createdBy = "SYSTEM"),
        UnitSpec(id = 16, modelId = 16, vesselCapacityM3 = 62.0, speedLoadedKmh = 30.0, speedEmptyKmh = 50.0, enginePowerHp = 1020.0, createdBy = "SYSTEM"),
        UnitSpec(id = 17, modelId = 17, vesselCapacityM3 = 126.0, speedLoadedKmh = 25.0, speedEmptyKmh = 45.0, enginePowerHp = 2000.0, createdBy = "SYSTEM"),
        UnitSpec(id = 18, modelId = 18, vesselCapacityM3 = 24.0, speedLoadedKmh = 38.0, speedEmptyKmh = 55.0, enginePowerHp = 390.0, createdBy = "SYSTEM"),
    )
}
