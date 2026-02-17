package com.example.pitwise.data.sync

import android.util.Log
import com.example.pitwise.data.local.dao.EquipmentDao
import com.example.pitwise.data.local.entity.EquipmentDozer
import com.example.pitwise.data.local.entity.EquipmentHauler
import com.example.pitwise.data.local.entity.EquipmentLoader
import com.example.pitwise.data.local.entity.MaterialProperty
import com.example.pitwise.data.local.entity.SyncMetadata
import com.example.pitwise.data.remote.SupabaseDataService
import com.example.pitwise.data.remote.dto.DozerDto
import com.example.pitwise.data.remote.dto.HaulerDto
import com.example.pitwise.data.remote.dto.LoaderDto
import com.example.pitwise.data.remote.dto.MaterialDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages offline-first sync of equipment data from Supabase to Room.
 *
 * Strategy:
 * - initialSync(): Full download on first launch (all 4 tables)
 * - deltaSync(): Re-fetch all data if >1 hour since last sync
 * - All data stored locally for offline use
 * - Auto-triggered when network is restored (via NetworkMonitor)
 */
@Singleton
class EquipmentSyncManager @Inject constructor(
    private val equipmentDao: EquipmentDao,
    private val supabaseDataService: SupabaseDataService,
    private val networkMonitor: NetworkMonitor
) {
    companion object {
        private const val TAG = "EquipmentSync"
        private const val SYNC_INTERVAL_MS = 60 * 60 * 1000L // 1 hour
        private const val TABLE_LOADERS = "loaders"
        private const val TABLE_HAULERS = "haulers"
        private const val TABLE_DOZERS = "dozers"
        private const val TABLE_MATERIALS = "material_properties"
    }

    /**
     * Check if initial sync is needed (no local data).
     */
    suspend fun isSyncNeeded(): Boolean = withContext(Dispatchers.IO) {
        val loaderCount = equipmentDao.countLoaders()
        val haulerCount = equipmentDao.countHaulers()
        val dozerCount = equipmentDao.countDozers()
        val materialCount = equipmentDao.countMaterials()

        val needed = loaderCount == 0 || haulerCount == 0 || dozerCount == 0 || materialCount == 0
        Log.i(TAG, "Sync needed: $needed (L=$loaderCount H=$haulerCount D=$dozerCount M=$materialCount)")
        return@withContext needed
    }

    /**
     * Full sync: download all equipment data from Supabase and store locally.
     * Returns true if sync completed successfully.
     */
    suspend fun initialSync(): Boolean = withContext(Dispatchers.IO) {
        if (!networkMonitor.isCurrentlyOnline()) {
            Log.w(TAG, "No network — skipping initial sync")
            return@withContext false
        }

        Log.i(TAG, "Starting initial sync...")
        var success = true

        // Sync Loaders
        success = syncLoaders() && success

        // Sync Haulers
        success = syncHaulers() && success

        // Sync Dozers
        success = syncDozers() && success

        // Sync Materials
        success = syncMaterials() && success

        Log.i(TAG, "Initial sync ${if (success) "COMPLETE" else "PARTIAL"}")
        return@withContext success
    }

    /**
     * Delta sync: re-fetch data if enough time has passed since last sync.
     * Called when network connectivity is restored.
     */
    suspend fun deltaSync(): Boolean = withContext(Dispatchers.IO) {
        if (!networkMonitor.isCurrentlyOnline()) {
            return@withContext false
        }

        val now = System.currentTimeMillis()
        var synced = false

        // Check each table
        for (table in listOf(TABLE_LOADERS, TABLE_HAULERS, TABLE_DOZERS, TABLE_MATERIALS)) {
            val meta = equipmentDao.getSyncMetadata(table)
            val lastSync = meta?.lastSyncedAt ?: 0L
            if (now - lastSync > SYNC_INTERVAL_MS) {
                Log.i(TAG, "Delta sync needed for $table (last: ${(now - lastSync) / 1000}s ago)")
                when (table) {
                    TABLE_LOADERS -> syncLoaders()
                    TABLE_HAULERS -> syncHaulers()
                    TABLE_DOZERS -> syncDozers()
                    TABLE_MATERIALS -> syncMaterials()
                }
                synced = true
            }
        }

        if (!synced) {
            Log.d(TAG, "All tables up-to-date, skipping delta sync")
        }
        return@withContext synced
    }

    // ── Individual table sync methods ──

    private suspend fun syncLoaders(): Boolean {
        return try {
            val result = supabaseDataService.getLoaders()
            result.onSuccess { dtos ->
                val entities = dtos.map { it.toEntity() }
                equipmentDao.insertLoaders(entities)
                equipmentDao.upsertSyncMetadata(
                    SyncMetadata(TABLE_LOADERS, System.currentTimeMillis(), entities.size)
                )
                Log.i(TAG, "Synced ${entities.size} loaders")
            }
            result.isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync loaders: ${e.message}", e)
            false
        }
    }

    private suspend fun syncHaulers(): Boolean {
        return try {
            val result = supabaseDataService.getHaulers()
            result.onSuccess { dtos ->
                val entities = dtos.map { it.toEntity() }
                equipmentDao.insertHaulers(entities)
                equipmentDao.upsertSyncMetadata(
                    SyncMetadata(TABLE_HAULERS, System.currentTimeMillis(), entities.size)
                )
                Log.i(TAG, "Synced ${entities.size} haulers")
            }
            result.isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync haulers: ${e.message}", e)
            false
        }
    }

    private suspend fun syncDozers(): Boolean {
        return try {
            val result = supabaseDataService.getDozers()
            result.onSuccess { dtos ->
                val entities = dtos.map { it.toEntity() }
                equipmentDao.insertDozers(entities)
                equipmentDao.upsertSyncMetadata(
                    SyncMetadata(TABLE_DOZERS, System.currentTimeMillis(), entities.size)
                )
                Log.i(TAG, "Synced ${entities.size} dozers")
            }
            result.isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync dozers: ${e.message}", e)
            false
        }
    }

    private suspend fun syncMaterials(): Boolean {
        return try {
            val result = supabaseDataService.getMaterials()
            result.onSuccess { dtos ->
                val entities = dtos.map { it.toEntity() }
                equipmentDao.insertMaterials(entities)
                equipmentDao.upsertSyncMetadata(
                    SyncMetadata(TABLE_MATERIALS, System.currentTimeMillis(), entities.size)
                )
                Log.i(TAG, "Synced ${entities.size} materials")
            }
            result.isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync materials: ${e.message}", e)
            false
        }
    }

    // ── DTO → Entity mappers ──

    private fun LoaderDto.toEntity() = EquipmentLoader(
        remoteId = id,
        category = category,
        className = className,
        material = material,
        bucketCapLcm = bucketCapLcm,
        bucketCapBcm = bucketCapBcm,
        fillFactor = fillFactor,
        swellFactor = swellFactor,
        jobEff = jobEff,
        sg = sg,
        cycleTimeSec = cycleTimeSec,
        productivity = productivity
    )

    private fun HaulerDto.toEntity() = EquipmentHauler(
        remoteId = id,
        category = category,
        className = className,
        material = material,
        vesselLcm = vesselLcm,
        vesselBcmOrTon = vesselBcmOrTon,
        spottingSec = spottingSec,
        travelSec = travelSec,
        queueingTimeSec = queueingTimeSec,
        dumpingSec = dumpingSec,
        returnKmh = returnKmh,
        cycleTimeSec = cycleTimeSec,
        cycleTimeMin = cycleTimeMin,
        specificGravity = specificGravity,
        productivity = productivity
    )

    private fun DozerDto.toEntity() = EquipmentDozer(
        remoteId = id,
        category = category,
        className = className,
        material = material,
        bladeWidthM = bladeWidthM,
        bladeHeightM = bladeHeightM,
        bladeVolumeM3 = bladeVolumeM3,
        lengthM = lengthM,
        jarakDozingM = jarakDozingM,
        speedForwardKmh = speedForwardKmh,
        speedReverseKmh = speedReverseKmh,
        shiftingMin = shiftingMin,
        positioningMin = positioningMin,
        cycleTimeMin = cycleTimeMin,
        jobEff = jobEff,
        bladeFactor = bladeFactor,
        productivity = productivity
    )

    private fun MaterialDto.toEntity() = MaterialProperty(
        remoteId = id,
        material = material,
        insitu = insitu,
        fillFactor = fillFactor,
        swellFactor = swellFactor,
        sg = sg,
        jobEff = jobEff
    )
}
