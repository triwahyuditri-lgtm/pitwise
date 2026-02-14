package com.example.pitwise.domain.map

import com.example.pitwise.data.local.dao.MapDao
import com.example.pitwise.data.local.entity.MapAnnotation
import com.example.pitwise.data.local.entity.MapEntry
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing saved maps and map annotations.
 */
@Singleton
class MapRepository @Inject constructor(
    private val mapDao: MapDao
) {
    val allMaps: Flow<List<MapEntry>> = mapDao.getAllMaps()
    val activeMap: Flow<MapEntry?> = mapDao.getActiveMap()

    suspend fun getMapById(mapId: Long): MapEntry? = mapDao.getMapById(mapId)

    suspend fun addMap(name: String, type: String, uri: String): Long {
        val entry = MapEntry(
            name = name,
            type = type,
            uri = uri
        )
        return mapDao.insert(entry)
    }

    suspend fun deleteMap(map: MapEntry) {
        mapDao.delete(map)
    }

    suspend fun renameMap(mapId: Long, name: String) {
        mapDao.rename(mapId, name)
    }

    suspend fun setActive(mapId: Long) {
        mapDao.deactivateAll()
        mapDao.activate(mapId)
    }

    suspend fun getActiveMapOnce(): MapEntry? {
        return mapDao.getActiveMapOnce()
    }

    suspend fun updateLastOpened(mapId: Long) {
        mapDao.updateLastOpened(mapId, System.currentTimeMillis())
    }

    // ── Annotations ──

    fun getAnnotationsForMap(mapId: Long): Flow<List<MapAnnotation>> =
        mapDao.getAnnotationsForMap(mapId)

    suspend fun getAnnotationById(id: Long): MapAnnotation? =
        mapDao.getAnnotationById(id)

    suspend fun insertAnnotation(annotation: MapAnnotation): Long =
        mapDao.insertAnnotation(annotation)

    suspend fun updateAnnotation(annotation: MapAnnotation) =
        mapDao.updateAnnotation(annotation)

    suspend fun deleteAnnotation(annotation: MapAnnotation) =
        mapDao.deleteAnnotation(annotation)

    suspend fun countPointAnnotations(mapId: Long): Int =
        mapDao.countPointAnnotations(mapId)
}

