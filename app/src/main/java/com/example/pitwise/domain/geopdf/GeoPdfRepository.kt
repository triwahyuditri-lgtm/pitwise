package com.example.pitwise.domain.geopdf

import android.content.Context
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository wrapping all GeoPDF functionality.
 *
 * - Parses GeoPDF metadata from PDF files (on IO dispatcher)
 * - Validates the parsed data
 * - Provides GPS→pixel and GPS→screen transforms
 * - Caches state so metadata is parsed only once per map
 *
 * Inject this via Hilt in the ViewModel layer.
 */
@Singleton
class GeoPdfRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val parser = GeoPdfParser()
    private val engine = GeoPdfTransformEngine()

    /** Whether valid GeoPDF metadata has been parsed and the engine is ready. */
    val hasValidGeoPdf: Boolean
        get() = engine.isInitialized

    /** The current projection info, if a GeoPDF is loaded. */
    val projectionInfo: ProjectionInfo?
        get() = engine.projectionInfo

    /**
     * Parse GeoPDF metadata from a PDF file URI and initialize the transform engine.
     *
     * This runs on [Dispatchers.IO] and should be called once when a PDF is loaded.
     *
     * @param uri Content URI of the PDF file
     * @return Validation result indicating success or type of failure
     */
    suspend fun parseAndInitialize(uri: Uri): GeoPdfValidationResult = withContext(Dispatchers.IO) {
        try {
            // Reset any previous state
            engine.reset()

            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext GeoPdfValidationResult.ParseError("Cannot open PDF file")

            val metadata = inputStream.use { stream ->
                parser.parse(stream)
            } ?: return@withContext GeoPdfValidationResult.NoGeospatialData

            engine.initialize(metadata)
        } catch (e: Exception) {
            GeoPdfValidationResult.ParseError("GeoPDF parse error: ${e.message}")
        }
    }

    /**
     * Convert GPS position to PDF pixel coordinate.
     *
     * @param lat WGS84 latitude
     * @param lng WGS84 longitude
     * @return Pixel coordinate in PDF space, or null if not initialized
     */
    fun gpsToPixel(lat: Double, lng: Double): PixelPoint? {
        return engine.gpsToPixel(lat, lng)
    }

    /**
     * Convert GPS position to screen coordinate.
     *
     * @param lat WGS84 latitude
     * @param lng WGS84 longitude
     * @param scale Current zoom scale
     * @param offsetX Current pan offset X
     * @param offsetY Current pan offset Y
     * @return Screen coordinate, or null if not initialized
     */
    fun gpsToScreen(
        lat: Double,
        lng: Double,
        scale: Float,
        offsetX: Float,
        offsetY: Float
    ): Offset? {
        return engine.gpsToScreen(lat, lng, scale, offsetX, offsetY)
    }

    /**
     * Convert PDF pixel coordinate to GPS position.
     * Inverse of [gpsToPixel].
     */
    fun pixelToGps(pixelX: Double, pixelY: Double): Pair<Double, Double>? {
        return engine.pixelToGps(pixelX, pixelY)
    }

    /**
     * Convert PDF pixel coordinate to projected CRS coordinate (e.g., UTM meters).
     * Used for accurate distance/area calculations on PDF maps.
     */
    fun pixelToProjected(pixelX: Double, pixelY: Double): Pair<Double, Double>? {
        return engine.pixelToProjected(pixelX, pixelY)
    }

    /**
     * Convert screen coordinate directly to GPS position.
     * Inverse of [gpsToScreen].
     */
    fun screenToGps(
        screenX: Float,
        screenY: Float,
        scale: Float,
        offsetX: Float,
        offsetY: Float
    ): Pair<Double, Double>? {
        return engine.screenToGps(screenX, screenY, scale, offsetX, offsetY)
    }

    /**
     * Get debug info for fishbone failure analysis.
     *
     * @param lat WGS84 latitude
     * @param lng WGS84 longitude
     * @return Debug info with all intermediate transform values
     */
    fun getDebugInfo(lat: Double, lng: Double): GeoPdfDebugInfo? {
        return engine.getDebugInfo(lat, lng)
    }

    /**
     * Set the render DPI to match bitmap output.
     * Must be called after [parseAndInitialize] succeeds.
     */
    fun setRenderDpi(dpi: Int) {
        engine.setRenderDpi(dpi)
    }

    /**
     * Reset the engine (call when switching to a different map).
     */
    fun reset() {
        engine.reset()
    }
}
