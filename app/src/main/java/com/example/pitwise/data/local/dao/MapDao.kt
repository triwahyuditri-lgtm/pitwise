package com.example.pitwise.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.pitwise.data.local.entity.MapAnnotation
import com.example.pitwise.data.local.entity.MapEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface MapDao {

    @Query("SELECT * FROM maps ORDER BY created_at DESC")
    fun getAllMaps(): Flow<List<MapEntry>>

    @Query("SELECT * FROM maps WHERE is_active = 1 LIMIT 1")
    fun getActiveMap(): Flow<MapEntry?>

    @Query("SELECT * FROM maps WHERE is_active = 1 LIMIT 1")
    suspend fun getActiveMapOnce(): MapEntry?

    @Query("SELECT * FROM maps WHERE id = :mapId LIMIT 1")
    suspend fun getMapById(mapId: Long): MapEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(map: MapEntry): Long

    @Update
    suspend fun update(map: MapEntry)

    @Delete
    suspend fun delete(map: MapEntry)

    @Query("UPDATE maps SET is_active = 0")
    suspend fun deactivateAll()

    @Query("UPDATE maps SET is_active = 1 WHERE id = :mapId")
    suspend fun activate(mapId: Long)

    @Query("UPDATE maps SET name = :name WHERE id = :mapId")
    suspend fun rename(mapId: Long, name: String)

    @Query("UPDATE maps SET last_opened = :timestamp WHERE id = :mapId")
    suspend fun updateLastOpened(mapId: Long, timestamp: Long)

    // ── Annotations ──

    @Query("SELECT * FROM map_annotations WHERE map_id = :mapId ORDER BY created_at DESC")
    fun getAnnotationsForMap(mapId: Long): Flow<List<MapAnnotation>>

    @Query("SELECT * FROM map_annotations WHERE id = :id LIMIT 1")
    suspend fun getAnnotationById(id: Long): MapAnnotation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: MapAnnotation): Long

    @Update
    suspend fun updateAnnotation(annotation: MapAnnotation)

    @Delete
    suspend fun deleteAnnotation(annotation: MapAnnotation)

    @Query("DELETE FROM map_annotations WHERE map_id = :mapId")
    suspend fun deleteAnnotationsForMap(mapId: Long)

    @Query("SELECT COUNT(*) FROM map_annotations WHERE map_id = :mapId AND type = 'POINT'")
    suspend fun countPointAnnotations(mapId: Long): Int
}

