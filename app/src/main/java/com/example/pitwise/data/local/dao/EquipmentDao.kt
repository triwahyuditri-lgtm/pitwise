package com.example.pitwise.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pitwise.data.local.entity.EquipmentDozer
import com.example.pitwise.data.local.entity.EquipmentHauler
import com.example.pitwise.data.local.entity.EquipmentLoader
import com.example.pitwise.data.local.entity.MaterialProperty
import com.example.pitwise.data.local.entity.SyncMetadata
import kotlinx.coroutines.flow.Flow

@Dao
interface EquipmentDao {

    // ── Loaders ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoaders(loaders: List<EquipmentLoader>)

    @Query("SELECT * FROM equipment_loader ORDER BY class_name ASC, material ASC")
    fun getAllLoaders(): Flow<List<EquipmentLoader>>

    @Query("SELECT * FROM equipment_loader WHERE class_name = :className ORDER BY material ASC")
    fun getLoadersByClass(className: String): Flow<List<EquipmentLoader>>

    @Query("SELECT DISTINCT class_name FROM equipment_loader ORDER BY class_name ASC")
    fun getLoaderClasses(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM equipment_loader")
    suspend fun countLoaders(): Int

    @Query("DELETE FROM equipment_loader")
    suspend fun clearLoaders()

    // ── Haulers ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHaulers(haulers: List<EquipmentHauler>)

    @Query("SELECT * FROM equipment_hauler ORDER BY class_name ASC, material ASC")
    fun getAllHaulers(): Flow<List<EquipmentHauler>>

    @Query("SELECT * FROM equipment_hauler WHERE class_name = :className ORDER BY material ASC")
    fun getHaulersByClass(className: String): Flow<List<EquipmentHauler>>

    @Query("SELECT DISTINCT class_name FROM equipment_hauler ORDER BY class_name ASC")
    fun getHaulerClasses(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM equipment_hauler")
    suspend fun countHaulers(): Int

    @Query("DELETE FROM equipment_hauler")
    suspend fun clearHaulers()

    // ── Dozers ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDozers(dozers: List<EquipmentDozer>)

    @Query("SELECT * FROM equipment_dozer ORDER BY class_name ASC, material ASC")
    fun getAllDozers(): Flow<List<EquipmentDozer>>

    @Query("SELECT * FROM equipment_dozer WHERE class_name = :className ORDER BY material ASC")
    fun getDozersByClass(className: String): Flow<List<EquipmentDozer>>

    @Query("SELECT DISTINCT class_name FROM equipment_dozer ORDER BY class_name ASC")
    fun getDozerClasses(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM equipment_dozer")
    suspend fun countDozers(): Int

    @Query("DELETE FROM equipment_dozer")
    suspend fun clearDozers()

    // ── Material Properties ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterials(materials: List<MaterialProperty>)

    @Query("SELECT * FROM material_property ORDER BY material ASC")
    fun getAllMaterials(): Flow<List<MaterialProperty>>

    @Query("SELECT * FROM material_property WHERE material = :name LIMIT 1")
    suspend fun getMaterialByName(name: String): MaterialProperty?

    @Query("SELECT COUNT(*) FROM material_property")
    suspend fun countMaterials(): Int

    @Query("DELETE FROM material_property")
    suspend fun clearMaterials()

    // ── Sync Metadata ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncMetadata(metadata: SyncMetadata)

    @Query("SELECT * FROM sync_metadata WHERE table_name = :tableName")
    suspend fun getSyncMetadata(tableName: String): SyncMetadata?

    @Query("SELECT * FROM sync_metadata")
    fun getAllSyncMetadata(): Flow<List<SyncMetadata>>
}
