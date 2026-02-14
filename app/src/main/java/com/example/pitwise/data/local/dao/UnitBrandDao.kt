package com.example.pitwise.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pitwise.data.local.entity.UnitBrand
import kotlinx.coroutines.flow.Flow

@Dao
interface UnitBrandDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(brand: UnitBrand): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(brands: List<UnitBrand>)

    @Query("SELECT * FROM unit_brand ORDER BY name ASC")
    fun getAll(): Flow<List<UnitBrand>>

    @Query("SELECT * FROM unit_brand WHERE category = :category ORDER BY name ASC")
    fun getByCategory(category: String): Flow<List<UnitBrand>>

    @Query("SELECT * FROM unit_brand WHERE id = :id")
    suspend fun getById(id: Long): UnitBrand?

    @Query("SELECT COUNT(*) FROM unit_brand")
    suspend fun getCount(): Int

    @Query("SELECT * FROM unit_brand WHERE name = :name AND category = :category LIMIT 1")
    suspend fun findByNameAndCategory(name: String, category: String): UnitBrand?
}
