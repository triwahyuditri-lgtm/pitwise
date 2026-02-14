package com.example.pitwise.data.importer

import android.content.Context
import android.util.Log
import com.example.pitwise.data.local.dao.BaseUnitVersionDao
import com.example.pitwise.data.local.dao.UnitBrandDao
import com.example.pitwise.data.local.dao.UnitModelDao
import com.example.pitwise.data.local.dao.UnitSpecDao
import com.example.pitwise.data.local.entity.BaseUnitVersion
import com.example.pitwise.data.local.entity.UnitBrand
import com.example.pitwise.data.local.entity.UnitModel
import com.example.pitwise.data.local.entity.UnitSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the import of base data from Excel into Room database.
 *
 * Features:
 * - Version control: prevents duplicate imports
 * - Duplicate handling: find-or-create brands/models
 * - LOCAL_OVERRIDE protection: never overwrites user-modified data
 * - Runs on Dispatchers.IO to avoid blocking UI thread
 */
@Singleton
class BaseDataImporter @Inject constructor(
    private val parser: ExcelBaseDataParser,
    private val validator: BaseDataValidator,
    private val brandDao: UnitBrandDao,
    private val modelDao: UnitModelDao,
    private val specDao: UnitSpecDao,
    private val versionDao: BaseUnitVersionDao
) {

    companion object {
        private const val TAG = "BaseDataImporter"
        private const val CURRENT_VERSION = 1
    }

    /**
     * Check if base data import is needed.
     * Returns true if no SYSTEM units exist or if a newer version is available.
     */
    suspend fun isImportNeeded(): Boolean = withContext(Dispatchers.IO) {
        val systemCount = modelDao.countBySource("SYSTEM")
        if (systemCount == 0) {
            Log.i(TAG, "No SYSTEM units found — import needed")
            return@withContext true
        }

        val latestVersion = versionDao.getLatestVersion() ?: 0
        if (CURRENT_VERSION > latestVersion) {
            Log.i(TAG, "Newer version available ($CURRENT_VERSION > $latestVersion) — import needed")
            return@withContext true
        }

        Log.i(TAG, "Import not needed (SYSTEM count: $systemCount, version: $latestVersion)")
        return@withContext false
    }

    /**
     * Execute the full import process.
     * Runs on Dispatchers.IO.
     */
    suspend fun importBaseData(context: Context): ImportResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting base data import (version $CURRENT_VERSION)...")

        // Check if this version was already imported
        if (versionDao.versionExists(CURRENT_VERSION)) {
            Log.i(TAG, "Version $CURRENT_VERSION already imported — skipping")
            return@withContext ImportResult(
                success = true,
                totalRows = 0,
                importedRows = 0,
                skippedRows = 0,
                brandsCreated = 0,
                modelsCreated = 0,
                specsCreated = 0,
                errors = emptyList()
            )
        }

        // Step 1: Parse Excel file
        val parsedRows = parser.parseFromAssets(context)
        if (parsedRows.isEmpty()) {
            Log.e(TAG, "No rows parsed from Excel file")
            return@withContext ImportResult(
                success = false,
                totalRows = 0,
                importedRows = 0,
                skippedRows = 0,
                brandsCreated = 0,
                modelsCreated = 0,
                specsCreated = 0,
                errors = listOf(ImportError(0, "", "No rows parsed from Excel file"))
            )
        }

        Log.i(TAG, "Parsed ${parsedRows.size} rows from Excel")

        // Step 2: Validate and insert
        val errors = mutableListOf<ImportError>()
        var importedCount = 0
        var skippedCount = 0
        var brandsCreated = 0
        var modelsCreated = 0
        var specsCreated = 0

        // Cache for brand lookups (name+category -> brandId)
        val brandCache = mutableMapOf<String, Long>()
        // Cache for model lookups (brandId+modelName -> modelId)
        val modelCache = mutableMapOf<String, Long>()

        for ((index, row) in parsedRows.withIndex()) {
            val rowNum = index + 1

            // Validate
            val validation = validator.validate(row, rowNum, row.sheetName)
            if (!validation.isValid) {
                errors.addAll(validation.errors.map { ImportError(rowNum, row.sheetName, it) })
                skippedCount++
                continue
            }

            try {
                // Step 2a: Find or create brand
                // For the base data, we use the category as the brand name
                // since the Excel doesn't have a separate "Brand" column
                val brandKey = "${row.mappedCategory}_brand"
                val brandId = brandCache.getOrPut(brandKey) {
                    val existingBrand = brandDao.findByNameAndCategory(
                        row.mappedCategory, row.mappedCategory
                    )
                    if (existingBrand != null) {
                        existingBrand.id
                    } else {
                        val brandName = when (row.mappedCategory) {
                            "EXCA" -> "Excavator"
                            "DT" -> "Dump Truck"
                            "DOZER" -> "Bulldozer"
                            else -> row.mappedCategory
                        }
                        val newBrand = UnitBrand(
                            name = brandName,
                            category = row.mappedCategory
                        )
                        val id = brandDao.insert(newBrand)
                        brandsCreated++
                        Log.d(TAG, "Created brand: $brandName (${row.mappedCategory}) -> id=$id")
                        id
                    }
                }

                // Step 2b: Find or create model
                val modelKey = "${brandId}_${row.className}"
                val modelId = modelCache.getOrPut(modelKey) {
                    val existingModel = modelDao.findByNameAndBrand(row.className, brandId)
                    if (existingModel != null) {
                        // Check if it's a LOCAL_OVERRIDE — don't touch those
                        if (existingModel.source == "LOCAL_OVERRIDE") {
                            Log.d(TAG, "Skipping LOCAL_OVERRIDE model: ${row.className}")
                            existingModel.id
                        } else {
                            existingModel.id
                        }
                    } else {
                        val newModel = UnitModel(
                            brandId = brandId,
                            modelName = row.className,
                            category = row.mappedCategory,
                            source = "SYSTEM",
                            version = CURRENT_VERSION
                        )
                        val id = modelDao.insert(newModel)
                        modelsCreated++
                        Log.d(TAG, "Created model: ${row.className} -> id=$id")
                        id
                    }
                }

                // Step 2c: Check if model is LOCAL_OVERRIDE — skip spec insert
                val existingModel = modelDao.getById(modelId)
                if (existingModel?.source == "LOCAL_OVERRIDE") {
                    skippedCount++
                    continue
                }

                // Step 2d: Upsert spec (per material)
                val materialKey = row.material ?: "default"
                val existingSpec = if (row.material != null) {
                    specDao.findByModelAndMaterial(modelId, row.material)
                } else {
                    specDao.getByModelSync(modelId)
                }

                val spec = createSpec(modelId, row, existingSpec?.id ?: 0L)

                if (existingSpec != null) {
                    // Update existing spec if SYSTEM version is newer
                    if (existingSpec.createdBy == "SYSTEM") {
                        specDao.update(spec.copy(id = existingSpec.id))
                    }
                    // Don't overwrite user-created specs
                } else {
                    specDao.insert(spec)
                    specsCreated++
                }

                importedCount++
            } catch (e: Exception) {
                Log.e(TAG, "Error importing row $rowNum: ${e.message}", e)
                errors.add(ImportError(rowNum, row.sheetName, "Import error: ${e.message}"))
                skippedCount++
            }
        }

        // Step 3: Record version
        versionDao.insert(
            BaseUnitVersion(
                versionNumber = CURRENT_VERSION,
                recordCount = importedCount
            )
        )

        val result = ImportResult(
            success = errors.isEmpty() || importedCount > 0,
            totalRows = parsedRows.size,
            importedRows = importedCount,
            skippedRows = skippedCount,
            brandsCreated = brandsCreated,
            modelsCreated = modelsCreated,
            specsCreated = specsCreated,
            errors = errors
        )

        Log.i(TAG, result.summary)
        return@withContext result
    }

    /**
     * Create a UnitSpec from a parsed row.
     */
    private fun createSpec(modelId: Long, row: ParsedUnitRow, existingId: Long): UnitSpec {
        return UnitSpec(
            id = existingId,
            modelId = modelId,
            material = row.material,

            // Common
            bucketCapacityM3 = row.bucketCapacityBcm,
            bucketCapacityLcm = row.bucketCapacityLcm,
            vesselCapacityM3 = row.vesselLcm,
            vesselCapacityTon = row.vesselBcmOrTon,
            fillFactorDefault = row.fillFactor,
            swellFactor = row.swellFactor,
            specificGravity = row.specificGravity,
            jobEfficiency = row.jobEfficiency,
            cycleTimeRefSec = row.cycleTimeSec,
            productivity = row.productivity,

            // Hauler
            speedLoadedKmh = row.travelSpeedKmh,
            speedEmptyKmh = row.returnSpeedKmh,
            spottingTimeSec = row.spottingTimeSec,
            queueingTimeSec = row.queueingTimeSec,
            dumpingTimeSec = row.dumpingTimeSec,
            returnSpeedKmh = row.returnSpeedKmh,

            // Dozer
            bladeWidthM = row.bladeWidthM,
            bladeHeightM = row.bladeHeightM,
            bladeVolumeM3 = row.bladeVolumeM3,
            dozingLengthM = row.dozingLengthM,
            forwardSpeedKmh = row.forwardSpeedKmh,
            reverseSpeedKmh = row.reverseSpeedKmh,
            shiftingTimeMin = row.shiftingTimeMin,
            positioningTimeMin = row.positioningTimeMin,
            bladeFactor = row.bladeFactor,

            createdBy = "SYSTEM"
        )
    }
}
