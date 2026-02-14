package com.example.pitwise.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.pitwise.data.local.entity.BaseUnitVersion

@Dao
interface BaseUnitVersionDao {

    @Insert
    suspend fun insert(version: BaseUnitVersion): Long

    @Query("SELECT MAX(version_number) FROM base_unit_version")
    suspend fun getLatestVersion(): Int?

    @Query("SELECT * FROM base_unit_version ORDER BY imported_at DESC LIMIT 1")
    suspend fun getLatest(): BaseUnitVersion?

    @Query("SELECT EXISTS(SELECT 1 FROM base_unit_version WHERE version_number = :version)")
    suspend fun versionExists(version: Int): Boolean
}
