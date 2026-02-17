package com.example.pitwise.data.repository

import com.example.pitwise.data.local.dao.UnitBrandDao
import com.example.pitwise.data.local.dao.UnitModelDao
import com.example.pitwise.data.local.dao.UnitModelWithBrandTuple
import com.example.pitwise.data.local.dao.UnitSpecDao
import com.example.pitwise.data.local.entity.UnitBrand
import com.example.pitwise.data.local.entity.UnitModel
import com.example.pitwise.data.local.entity.UnitSpec
import com.example.pitwise.data.remote.SupabaseAuthService
import com.example.pitwise.data.remote.SupabaseUnitService
import com.example.pitwise.data.remote.dto.GlobalUnitDto
import com.example.pitwise.data.remote.dto.SpecJsonDto
import com.example.pitwise.domain.model.UnitModelWithBrand
import com.example.pitwise.domain.repository.UnitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnitRepositoryImpl @Inject constructor(
    private val brandDao: UnitBrandDao,
    private val modelDao: UnitModelDao,
    private val specDao: UnitSpecDao,
    private val supabaseUnitService: SupabaseUnitService,
    private val supabaseDataService: com.example.pitwise.data.remote.SupabaseDataService,
    private val authService: SupabaseAuthService
) : UnitRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun getAllBrands(): Flow<List<UnitBrand>> = brandDao.getAll()

    override fun getBrandsByCategory(category: String): Flow<List<UnitBrand>> =
        brandDao.getByCategory(category)

    override fun getAllModels(): Flow<List<UnitModel>> = modelDao.getAll()

    override fun getModelsByBrand(brandId: Long): Flow<List<UnitModel>> =
        modelDao.getByBrand(brandId)

    override suspend fun getModelsWithBrandNames(): List<UnitModelWithBrand> {
        return modelDao.getModelsWithBrandNames().map { tuple ->
            UnitModelWithBrand(
                modelId = tuple.modelId,
                brandName = tuple.brandName,
                modelName = tuple.modelName,
                category = tuple.category
            )
        }
    }

    override suspend fun getModelsByUnitType(type: com.example.pitwise.domain.model.UnitType): List<UnitModelWithBrand> {
        // Fetch from Supabase directly for the picker, mapped to UnitModelWithBrand
        // Note: The new tables don't have "Brand" column, only "Class".
        // We will use "Class" as both Model and part of display.
        val result = when (type) {
            com.example.pitwise.domain.model.UnitType.LOADER -> supabaseDataService.getLoaders()
            com.example.pitwise.domain.model.UnitType.HAULER -> supabaseDataService.getHaulers()
            com.example.pitwise.domain.model.UnitType.DOZER -> supabaseDataService.getDozers()
        }

        return result.getOrNull()?.map { dto ->
            // Use reflection or common properties to extract data
            // Since DTOs are different classes but share structure, we handle them:
            val (className, category) = when (dto) {
                is com.example.pitwise.data.remote.dto.LoaderDto -> dto.className to "EXCA"
                is com.example.pitwise.data.remote.dto.HaulerDto -> dto.className to "DT"
                is com.example.pitwise.data.remote.dto.DozerDto -> dto.className to "DOZER"
                else -> "" to ""
            }
            // Filter duplicates by class name (since one class has multiple rows for materials)
            UnitModelWithBrand(
                modelId = 0, // No local ID for direct network fetch
                brandName = type.displayName, // Use generic category name as brand
                modelName = className,
                category = category
            )
        }?.distinctBy { it.modelName } ?: emptyList()
    }

    override suspend fun getMaterials(): List<String> {
        return supabaseDataService.getMaterials().getOrNull()?.map { it.material } ?: emptyList()
    }

    override fun getSpecByModel(modelId: Long): Flow<UnitSpec?> =
        specDao.getByModel(modelId)

    override suspend fun getSpecByModelSync(modelId: Long): UnitSpec? =
        specDao.getByModelSync(modelId)

    override suspend fun findUnitSpec(
        type: com.example.pitwise.domain.model.UnitType,
        className: String,
        material: String
    ): UnitSpec? {
        // Fetch specific row from Supabase
        return try {
            when (type) {
                com.example.pitwise.domain.model.UnitType.LOADER -> {
                    val list = supabaseDataService.getLoaders(className).getOrNull()
                    val match = list?.find { it.material.equals(material, ignoreCase = true) }
                    match?.let {
                        UnitSpec(
                            modelId = 0,
                            bucketCapacityM3 = it.bucketCapBcm,
                            bucketCapacityLcm = it.bucketCapLcm,
                            fillFactorDefault = it.fillFactor,
                            cycleTimeRefSec = it.cycleTimeSec,
                            enginePowerHp = 0.0,
                            createdBy = "SUPABASE"
                        )
                    }
                }
                com.example.pitwise.domain.model.UnitType.HAULER -> {
                    val list = supabaseDataService.getHaulers(className).getOrNull()
                    val match = list?.find { it.material.equals(material, ignoreCase = true) }
                    match?.let {
                        // Use cycle_time_sec from table; fallback: compute from component times
                        val cycleTime = if (it.cycleTimeSec > 0) {
                            it.cycleTimeSec
                        } else {
                            it.travelSec + it.spottingSec + it.queueingTimeSec + it.dumpingSec + it.returnKmh
                        }
                        UnitSpec(
                            modelId = 0,
                            vesselCapacityM3 = it.vesselLcm, // Vessel LCM
                            vesselCapacityTon = it.vesselBcmOrTon, // Vessel BCM or Ton
                            specificGravity = it.specificGravity,
                            cycleTimeRefSec = cycleTime,
                            spottingTimeSec = it.spottingSec,
                            queueingTimeSec = it.queueingTimeSec,
                            dumpingTimeSec = it.dumpingSec,
                            returnSpeedKmh = it.returnKmh,
                            productivity = it.productivity,
                            createdBy = "SUPABASE"
                        )
                    }
                }
                com.example.pitwise.domain.model.UnitType.DOZER -> {
                    val list = supabaseDataService.getDozers(className).getOrNull()
                    val match = list?.find { it.material.equals(material, ignoreCase = true) }
                    match?.let {
                        UnitSpec(
                            modelId = 0,
                            bucketCapacityM3 = it.bladeVolumeM3, // Treat blade volume as capacity
                            cycleTimeRefSec = it.cycleTimeMin * 60, // Convert min to sec
                            fillFactorDefault = it.bladeFactor,
                            createdBy = "SUPABASE"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Copy a model's spec as a LOCAL_OVERRIDE.
     * Creates a new model entry with source = LOCAL_OVERRIDE and duplicates the spec.
     */
    override suspend fun copyToLocalOverride(modelId: Long): Long {
        val originalModel = modelDao.getById(modelId) ?: return -1
        val originalSpec = specDao.getByModelSync(modelId)

        // Create local override model
        val localModel = originalModel.copy(
            id = 0, // auto-generate
            source = "LOCAL_OVERRIDE",
            lastUpdated = System.currentTimeMillis()
        )
        val newModelId = modelDao.insert(localModel)

        // Copy spec to override
        if (originalSpec != null) {
            val localSpec = originalSpec.copy(
                id = 0, // auto-generate
                modelId = newModelId,
                createdBy = "USER"
            )
            specDao.insert(localSpec)
        }

        return newModelId
    }

    override suspend fun saveLocalSpec(spec: UnitSpec) {
        if (spec.id == 0L) {
            specDao.insert(spec)
        } else {
            specDao.update(spec)
        }
    }

    override suspend fun deleteSpec(specId: Long) {
        specDao.deleteById(specId)
    }

    /**
     * Publish a local model+spec to the global Supabase table.
     * Only superadmin can do this (enforced both client-side and by RLS).
     */
    override suspend fun publishToGlobal(modelId: Long): Result<Unit> {
        val model = modelDao.getById(modelId) ?: return Result.failure(
            IllegalArgumentException("Model not found")
        )
        val spec = specDao.getByModelSync(modelId)
        val brand = brandDao.getById(model.brandId)

        val specJsonDto = SpecJsonDto(
            bucketCapacityM3 = spec?.bucketCapacityM3,
            vesselCapacityM3 = spec?.vesselCapacityM3,
            fillFactorDefault = spec?.fillFactorDefault,
            cycleTimeRefSec = spec?.cycleTimeRefSec,
            speedLoadedKmh = spec?.speedLoadedKmh,
            speedEmptyKmh = spec?.speedEmptyKmh,
            enginePowerHp = spec?.enginePowerHp
        )

        val globalUnit = GlobalUnitDto(
            unitId = "unit_${model.id}",
            brand = brand?.name ?: "",
            model = model.modelName,
            category = model.category,
            specJson = json.encodeToString(specJsonDto),
            version = model.version + 1,
            updatedBy = authService.getCurrentUserId() ?: ""
        )

        return supabaseUnitService.publishUnit(globalUnit)
    }
}
