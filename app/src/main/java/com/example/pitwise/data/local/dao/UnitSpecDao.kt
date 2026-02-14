package com.example.pitwise.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.pitwise.data.local.entity.UnitSpec
import kotlinx.coroutines.flow.Flow

@Dao
interface UnitSpecDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(spec: UnitSpec): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(specs: List<UnitSpec>)

    @Query("SELECT * FROM unit_spec WHERE model_id = :modelId")
    fun getByModel(modelId: Long): Flow<UnitSpec?>

    @Query("SELECT * FROM unit_spec WHERE model_id = :modelId")
    suspend fun getByModelSync(modelId: Long): UnitSpec?

    @Query("SELECT * FROM unit_spec WHERE id = :id")
    suspend fun getById(id: Long): UnitSpec?

    @Update
    suspend fun update(spec: UnitSpec)

    @Query("DELETE FROM unit_spec WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM unit_spec WHERE model_id = :modelId AND material = :material LIMIT 1")
    suspend fun findByModelAndMaterial(modelId: Long, material: String): UnitSpec?

    @Query("SELECT * FROM unit_spec WHERE model_id = :modelId")
    suspend fun getAllByModelSync(modelId: Long): List<UnitSpec>
}
