package com.example.pitwise.domain.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.pitwise.data.local.dao.UnitBrandDao
import com.example.pitwise.data.local.dao.UnitModelDao
import com.example.pitwise.data.local.dao.UnitSpecDao
import com.example.pitwise.data.local.entity.UnitBrand
import com.example.pitwise.data.local.entity.UnitModel
import com.example.pitwise.data.local.entity.UnitSpec
import com.example.pitwise.data.remote.SupabaseAuthService
import com.example.pitwise.data.remote.SupabaseUnitService
import com.example.pitwise.data.remote.dto.GlobalUnitDto
import com.example.pitwise.data.remote.dto.SpecJsonDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages synchronization of global unit data from Supabase to local Room DB.
 *
 * Sync logic:
 * - If user is logged in AND online:
 *   - Compare global version vs local max version
 *   - If global > local: update Room DB with new data
 * - If offline or not logged in: skip sync, app works normally
 * - Never delete or overwrite LOCAL_OVERRIDE entries
 */
@Singleton
class GlobalUnitSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabaseUnitService: SupabaseUnitService,
    private val authService: SupabaseAuthService,
    private val brandDao: UnitBrandDao,
    private val modelDao: UnitModelDao,
    private val specDao: UnitSpecDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Attempt sync on app launch. Non-blocking — failures are silently ignored.
     */
    suspend fun syncIfNeeded() {
        withContext(Dispatchers.IO) {
            try {
                // Skip if not logged in or not online
                if (!authService.isAuthenticated() || !isNetworkAvailable()) {
                    return@withContext
                }

                // Get local max version
                val localMaxVersion = modelDao.getMaxGlobalVersion() ?: 0

                // Get remote max version
                val remoteMaxVersion = supabaseUnitService.getMaxGlobalVersion().getOrNull() ?: 0

                // If remote is newer, fetch and update
                if (remoteMaxVersion > localMaxVersion) {
                    val newUnits = supabaseUnitService.fetchUnitsNewerThan(localMaxVersion)
                        .getOrNull() ?: return@withContext

                    for (globalUnit in newUnits) {
                        applyGlobalUnit(globalUnit)
                    }
                }
            } catch (_: Exception) {
                // Sync failures are non-fatal. App continues with local data.
            }
        }
    }

    /**
     * Apply a single global unit to the local database.
     * Does NOT overwrite LOCAL_OVERRIDE entries.
     */
    private suspend fun applyGlobalUnit(globalUnit: GlobalUnitDto) {
        // Find or create brand
        val brandId = findOrCreateBrand(globalUnit.brand, globalUnit.category)

        // Check if model already exists as LOCAL_OVERRIDE — if so, skip
        val existingModels = modelDao.getByBrand(brandId)
        // We check synchronously to avoid overwriting
        val existingModel = modelDao.getById(
            globalUnit.unitId.removePrefix("unit_").toLongOrNull() ?: 0
        )
        if (existingModel?.source == "LOCAL_OVERRIDE") {
            return // Never overwrite local overrides
        }

        // Insert or update model
        val model = UnitModel(
            id = globalUnit.unitId.removePrefix("unit_").toLongOrNull() ?: 0,
            brandId = brandId,
            modelName = globalUnit.model,
            category = globalUnit.category,
            source = "GLOBAL",
            version = globalUnit.version,
            isActive = true,
            lastUpdated = System.currentTimeMillis()
        )
        modelDao.insert(model)

        // Parse and insert spec
        try {
            val specDto = json.decodeFromString<SpecJsonDto>(globalUnit.specJson)
            val spec = UnitSpec(
                modelId = model.id,
                bucketCapacityM3 = specDto.bucketCapacityM3,
                vesselCapacityM3 = specDto.vesselCapacityM3,
                fillFactorDefault = specDto.fillFactorDefault,
                cycleTimeRefSec = specDto.cycleTimeRefSec,
                speedLoadedKmh = specDto.speedLoadedKmh,
                speedEmptyKmh = specDto.speedEmptyKmh,
                enginePowerHp = specDto.enginePowerHp,
                createdBy = "SYSTEM"
            )
            specDao.insert(spec)
        } catch (_: Exception) {
            // Invalid spec JSON — skip this spec
        }
    }

    private suspend fun findOrCreateBrand(name: String, category: String): Long {
        // Simple approach: check if brand exists by scanning all
        // In production, you'd add a query by name+category
        val brands = brandDao.getById(1) // placeholder - we'd need better lookup
        val newBrand = UnitBrand(name = name, category = category)
        return brandDao.insert(newBrand)
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
