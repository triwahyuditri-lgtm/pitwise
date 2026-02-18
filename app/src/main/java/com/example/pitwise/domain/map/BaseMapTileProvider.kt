package com.example.pitwise.domain.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches and caches Esri ArcGIS Online XYZ map tiles.
 *
 * Two-level cache:
 *  1. In-memory LRU (fast, ~100 tiles → ~25 MB)
 *  2. Disk cache in app cache directory (survives process death)
 *
 * Tile URL pattern:
 *   https://services.arcgisonline.com/ArcGIS/rest/services/{service}/MapServer/tile/{z}/{y}/{x}
 */
@Singleton
class BaseMapTileProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TILE_SIZE = 256
        private const val MEMORY_CACHE_SIZE = 100
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 10_000
        private const val BASE_URL = "https://services.arcgisonline.com/ArcGIS/rest/services"
    }

    /** In-memory tile cache. Key = "service/z/y/x" */
    private val memoryCache = object : LruCache<String, Bitmap>(MEMORY_CACHE_SIZE) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1 // count-based
    }

    /** Disk cache root directory. */
    private val diskCacheDir: File by lazy {
        File(context.cacheDir, "esri_tiles").also { it.mkdirs() }
    }

    /**
     * Get a tile bitmap. Checks memory → disk → network, caching along the way.
     * Returns null if loading fails (no network, invalid tile, etc.).
     */
    suspend fun getTile(
        mapType: BaseMapType,
        zoom: Int,
        x: Int,
        y: Int
    ): Bitmap? {
        val key = "${mapType.serviceName}/$zoom/$y/$x"

        // 1. Memory cache
        memoryCache.get(key)?.let { return it }

        // 2. Disk cache
        val diskFile = File(diskCacheDir, key.replace("/", File.separator))
        if (diskFile.exists()) {
            val bmp = withContext(Dispatchers.IO) {
                try {
                    BitmapFactory.decodeFile(diskFile.absolutePath)
                } catch (_: Exception) { null }
            }
            if (bmp != null) {
                memoryCache.put(key, bmp)
                return bmp
            }
        }

        // 3. Network fetch
        return withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/${mapType.serviceName}/MapServer/tile/$zoom/$y/$x"
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.setRequestProperty("User-Agent", "Pitwise/1.0")

                if (connection.responseCode == 200) {
                    val bytes = connection.inputStream.use { it.readBytes() }
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null && !isPlaceholderTile(bmp)) {
                        // Save to disk
                        diskFile.parentFile?.mkdirs()
                        FileOutputStream(diskFile).use { fos ->
                            fos.write(bytes)
                        }
                        memoryCache.put(key, bmp)
                        bmp
                    } else {
                        bmp?.recycle()
                        null
                    }
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Get the label overlay tile (for HYBRID mode).
     */
    suspend fun getLabelTile(zoom: Int, x: Int, y: Int): Bitmap? {
        val service = BaseMapType.LABELS_SERVICE
        val key = "$service/$zoom/$y/$x"

        memoryCache.get(key)?.let { return it }

        val diskFile = File(diskCacheDir, key.replace("/", File.separator))
        if (diskFile.exists()) {
            val bmp = withContext(Dispatchers.IO) {
                try { BitmapFactory.decodeFile(diskFile.absolutePath) } catch (_: Exception) { null }
            }
            if (bmp != null) {
                memoryCache.put(key, bmp)
                return bmp
            }
        }

        return withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/$service/MapServer/tile/$zoom/$y/$x"
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.setRequestProperty("User-Agent", "Pitwise/1.0")

                if (connection.responseCode == 200) {
                    val bytes = connection.inputStream.use { it.readBytes() }
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        diskFile.parentFile?.mkdirs()
                        FileOutputStream(diskFile).use { it.write(bytes) }
                        memoryCache.put(key, bmp)
                    }
                    bmp
                } else null
            } catch (_: Exception) { null }
        }
    }

    /**
     * Detect Esri placeholder "Map data not yet available" tiles.
     * These tiles are mostly a single color (light gray #F2EFEB or similar).
     * Sample a grid of pixels — if >90% are near the same color, it's a placeholder.
     */
    private fun isPlaceholderTile(bitmap: Bitmap): Boolean {
        if (bitmap.width < 16 || bitmap.height < 16) return false
        val step = bitmap.width / 8
        val sampleCount = 64 // 8x8 grid
        var matchCount = 0
        val refPixel = bitmap.getPixel(step, step)
        val refR = (refPixel shr 16) and 0xFF
        val refG = (refPixel shr 8) and 0xFF
        val refB = refPixel and 0xFF

        // Esri placeholder tiles are around #F2EFEB (242, 239, 235)
        // Only check if reference is in the light gray range
        if (refR < 200 || refG < 200 || refB < 200) return false

        for (i in 0 until 8) {
            for (j in 0 until 8) {
                val px = bitmap.getPixel(step * i + step / 2, step * j + step / 2)
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                if (kotlin.math.abs(r - refR) < 20 &&
                    kotlin.math.abs(g - refG) < 20 &&
                    kotlin.math.abs(b - refB) < 20) {
                    matchCount++
                }
            }
        }
        // If >90% of sampled pixels are near-identical light gray → placeholder
        return matchCount > (sampleCount * 0.90)
    }

    /** Tile size in pixels. */
    fun tileSize(): Int = TILE_SIZE

    /** Clear all caches. */
    fun clearCache() {
        memoryCache.evictAll()
        diskCacheDir.deleteRecursively()
        diskCacheDir.mkdirs()
    }
}
