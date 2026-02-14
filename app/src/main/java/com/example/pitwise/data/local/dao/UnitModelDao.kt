package com.example.pitwise.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.pitwise.data.local.entity.UnitModel
import kotlinx.coroutines.flow.Flow

@Dao
interface UnitModelDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(model: UnitModel): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(models: List<UnitModel>)

    @Query("SELECT * FROM unit_model WHERE is_active = 1 ORDER BY model_name ASC")
    fun getAll(): Flow<List<UnitModel>>

    @Query("SELECT * FROM unit_model WHERE brand_id = :brandId AND is_active = 1 ORDER BY model_name ASC")
    fun getByBrand(brandId: Long): Flow<List<UnitModel>>

    @Query("SELECT * FROM unit_model WHERE id = :id")
    suspend fun getById(id: Long): UnitModel?

    @Query("SELECT * FROM unit_model WHERE source = :source AND is_active = 1 ORDER BY model_name ASC")
    fun getBySource(source: String): Flow<List<UnitModel>>

    @Update
    suspend fun update(model: UnitModel)

    @Query("SELECT MAX(version) FROM unit_model WHERE source = 'GLOBAL'")
    suspend fun getMaxGlobalVersion(): Int?

    @Query("SELECT COUNT(*) FROM unit_model")
    suspend fun getCount(): Int

    @Query("SELECT * FROM unit_model WHERE model_name = :modelName AND brand_id = :brandId LIMIT 1")
    suspend fun findByNameAndBrand(modelName: String, brandId: Long): UnitModel?

    @Query("SELECT COUNT(*) FROM unit_model WHERE source = :source")
    suspend fun countBySource(source: String): Int

    /**
     * Returns all active models joined with their brand name for picker display.
     * Returns [modelId, brandName, modelName, category] tuples.
     */
    @Query("""
        SELECT m.id AS modelId, b.name AS brandName, m.model_name AS modelName, m.category
        FROM unit_model m
        INNER JOIN unit_brand b ON m.brand_id = b.id
        WHERE m.is_active = 1
        ORDER BY m.category ASC, b.name ASC, m.model_name ASC
    """)
    suspend fun getModelsWithBrandNames(): List<UnitModelWithBrandTuple>
}

/**
 * Room query result tuple for the model+brand join.
 */
data class UnitModelWithBrandTuple(
    val modelId: Long,
    val brandName: String,
    val modelName: String,
    val category: String
)
