package com.example.pitwise.domain.geopdf

import android.util.Log
import androidx.compose.ui.geometry.Offset

/**
 * Full GPS→Screen transformation pipeline for GeoPDF maps.
 *
 * Pipeline:
 * ```
 * WGS84 (lat, lng)
 *   → CrsConverter → WorldPoint (projected, e.g. UTM)
 *   → GeoAffineTransform → PixelPoint (PDF page space)
 *   → apply zoom/pan → Screen coordinate
 * ```
 *
 * This engine is initialized once with parsed [GeoPdfMetadata],
 * then all subsequent transforms are fast cached lookups.
 * No per-frame allocations, no re-parsing.
 */
class GeoPdfTransformEngine {

    private var metadata: GeoPdfMetadata? = null
    private var crsConverter: CrsConverter? = null
    private var affineTransform: GeoAffineTransform? = null
    private var dpiScale: Double = 1.0  // Ratio of render DPI to PDF's native 72 DPI

    /** Whether the engine is fully initialized and ready for transforms. */
    val isInitialized: Boolean
        get() = metadata != null && crsConverter != null && affineTransform != null

    /** The parsed projection info, if available. */
    val projectionInfo: ProjectionInfo?
        get() = metadata?.projection

    /**
     * Initialize the engine with parsed GeoPDF metadata.
     *
     * Builds the CRS converter and affine transform matrix.
     * This must be called once before any transform operations.
     *
     * @param geoPdfMetadata Parsed metadata from [GeoPdfParser]
     * @return Validation result indicating success or the type of failure
     */
    fun initialize(geoPdfMetadata: GeoPdfMetadata): GeoPdfValidationResult {
        this.metadata = geoPdfMetadata

        // Validate control point count
        if (geoPdfMetadata.gpts.size < 3) {
            return GeoPdfValidationResult.InsufficientControlPoints(geoPdfMetadata.gpts.size)
        }

        // Build CRS converter
        val converter = CrsConverter.create(geoPdfMetadata.projection)
            ?: return GeoPdfValidationResult.NoCrsDetected

        this.crsConverter = converter

        // Build affine transform from control points
        // Source: projected coordinates (convert GPTS from lat/lng → projected)
        // Destination: pixel coordinates (LPTS, denormalized if needed)
        val srcPoints = mutableListOf<Pair<Double, Double>>()
        val dstPoints = mutableListOf<Pair<Double, Double>>()

        for (i in geoPdfMetadata.gpts.indices) {
            val gpt = geoPdfMetadata.gpts[i]
            val lpt = geoPdfMetadata.lpts[i]

            // GPTS contains lat/lng — convert to projected CRS
            val projected = converter.convert(gpt.x, gpt.y)
            srcPoints.add(Pair(projected.x, projected.y))

            // LPTS: denormalize using viewport BBox coordinates
            // In OGC format, LPTS are relative to the viewport BBox, not the full page.
            // For normalized LPTS (0-1): map to BBox area
            // For non-normalized LPTS: already in PDF user-space coordinates
            val pixelX: Double
            val pixelY: Double
            if (geoPdfMetadata.lptsNormalized) {
                // Map normalized LPTS (0-1) to viewport BBox area
                pixelX = geoPdfMetadata.bboxX + lpt.x * geoPdfMetadata.bboxWidth
                pixelY = geoPdfMetadata.bboxY + lpt.y * geoPdfMetadata.bboxHeight
            } else {
                pixelX = lpt.x
                pixelY = lpt.y
            }
            dstPoints.add(Pair(pixelX, pixelY))
        }

        val affine = GeoAffineTransform.fromControlPoints(srcPoints, dstPoints)
            ?: return GeoPdfValidationResult.ParseError("Failed to solve affine transform from control points")

        this.affineTransform = affine

        // Debug: Dump initialization data to logcat for GPS accuracy diagnosis
        Log.d("GeoPdfEngine", "═══ GeoPDF Transform Engine Initialized ═══")
        Log.d("GeoPdfEngine", "Page: ${geoPdfMetadata.pageWidth} x ${geoPdfMetadata.pageHeight} pts")
        Log.d("GeoPdfEngine", "BBox: origin=(${geoPdfMetadata.bboxX}, ${geoPdfMetadata.bboxY}), size=${geoPdfMetadata.bboxWidth} x ${geoPdfMetadata.bboxHeight}")
        Log.d("GeoPdfEngine", "LPTS normalized: ${geoPdfMetadata.lptsNormalized}")
        Log.d("GeoPdfEngine", "DPI scale: $dpiScale")
        Log.d("GeoPdfEngine", "Control points (${geoPdfMetadata.gpts.size}):")
        for (i in geoPdfMetadata.gpts.indices) {
            val gpt = geoPdfMetadata.gpts[i]
            val lpt = geoPdfMetadata.lpts[i]
            val src = srcPoints[i]
            val dst = dstPoints[i]
            Log.d("GeoPdfEngine", "  [$i] GPTS(lat=${gpt.x}, lng=${gpt.y}) → Projected(${src.first}, ${src.second}) | LPTS(${lpt.x}, ${lpt.y}) → Dst(${dst.first}, ${dst.second})")
        }
        Log.d("GeoPdfEngine", "Affine: ${affine.toDebugString()}")

        return GeoPdfValidationResult.Valid(geoPdfMetadata)
    }

    /**
     * Set the render DPI to match the bitmap output from PdfRendererEngine.
     *
     * PDF coordinates are natively in points (1/72 inch). When the PDF is rendered
     * to a bitmap at a higher DPI (e.g. 150), the bitmap pixels are scaled by
     * dpi/72. This method stores that scale factor so gpsToPixel() returns
     * coordinates in bitmap pixel space (matching the world coordinate system).
     *
     * @param dpi The render DPI used by PdfRendererEngine (default 150)
     */
    fun setRenderDpi(dpi: Int) {
        dpiScale = dpi / 72.0
    }

    /**
     * Convert GPS position to PDF pixel coordinate.
     *
     * Returns coordinates in bitmap pixel space (scaled by render DPI).
     *
     * @param lat WGS84 latitude
     * @param lng WGS84 longitude
     * @return Pixel coordinate in bitmap space, or null if engine not initialized
     */
    fun gpsToPixel(lat: Double, lng: Double): PixelPoint? {
        val converter = crsConverter ?: return null
        val affine = affineTransform ?: return null
        val meta = metadata ?: return null

        // Step 1: WGS84 → Projected CRS
        val projected = converter.convert(lat, lng)

        // Step 2: Projected → PDF points (Y=0 at bottom of page)
        val (pdfX, pdfY) = affine.transform(projected.x, projected.y)

        // Step 3: PDF points → Bitmap pixels
        // PDF has Y=0 at bottom, bitmap has Y=0 at top → flip Y
        return PixelPoint(pdfX * dpiScale, (meta.pageHeight - pdfY) * dpiScale)
    }

    /**
     * Convert GPS position directly to screen coordinate.
     *
     * Applies the full pipeline including the current zoom/pan state.
     *
     * @param lat WGS84 latitude
     * @param lng WGS84 longitude
     * @param scale Current map scale (zoom level)
     * @param offsetX Current map X offset (pan)
     * @param offsetY Current map Y offset (pan)
     * @return Screen coordinate as Compose [Offset], or null if engine not initialized
     */
    fun gpsToScreen(
        lat: Double,
        lng: Double,
        scale: Float,
        offsetX: Float,
        offsetY: Float
    ): Offset? {
        val pixel = gpsToPixel(lat, lng) ?: return null

        // Step 3: PDF pixel → Screen (apply zoom/pan transform)
        val screenX = (pixel.x.toFloat() * scale) + offsetX
        val screenY = (pixel.y.toFloat() * scale) + offsetY

        return Offset(screenX, screenY)
    }

    /**
     * Convert PDF pixel coordinate to projected CRS coordinate (e.g., UTM meters).
     *
     * Used for accurate distance/area calculations on PDF maps.
     * Returns coordinates in the map's native projected CRS (typically UTM easting/northing
     * in meters), which allows Euclidean distance and Shoelace area formulas to produce
     * results directly in meters and square meters.
     *
     * @param pixelX Bitmap pixel X coordinate
     * @param pixelY Bitmap pixel Y coordinate
     * @return Projected coordinate (e.g., UTM easting, northing) in meters, or null if not initialized
     */
    fun pixelToProjected(pixelX: Double, pixelY: Double): Pair<Double, Double>? {
        val affine = affineTransform ?: return null
        val meta = metadata ?: return null

        // Step 1: Bitmap pixels → PDF points
        // Bitmap has Y=0 at top, PDF has Y=0 at bottom → flip Y
        val pdfX = pixelX / dpiScale
        val pdfY = meta.pageHeight - (pixelY / dpiScale)

        // Step 2: PDF points → Projected CRS (e.g., UTM easting/northing in meters)
        // Stop here — do NOT convert to WGS84 lat/lng
        return affine.inverse(pdfX, pdfY)
    }

    /**
     * Convert PDF pixel coordinate to GPS position.
     * Inverse of [gpsToPixel].
     */
    fun pixelToGps(pixelX: Double, pixelY: Double): Pair<Double, Double>? {
        val converter = crsConverter ?: return null
        val affine = affineTransform ?: return null
        val meta = metadata ?: return null

        // Step 1: Bitmap pixels → PDF points
        // Bitmap has Y=0 at top, PDF has Y=0 at bottom → flip Y
        val pdfX = pixelX / dpiScale
        val pdfY = meta.pageHeight - (pixelY / dpiScale)

        // Step 2: PDF points → Projected CRS
        val (projX, projY) = affine.inverse(pdfX, pdfY) ?: return null

        // Step 3: Projected CRS → WGS84
        return converter.convertInverse(projX, projY)
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
        // Step 1: Screen → PDF pixel
        val pixelX = (screenX - offsetX) / scale
        val pixelY = (screenY - offsetY) / scale

        // Step 2: Pixel → GPS
        return pixelToGps(pixelX.toDouble(), pixelY.toDouble())
    }

    /**
     * Get debug information for the full transform pipeline.
     *
     * Used for fishbone failure analysis — shows all intermediate coordinates
     * so misalignment issues can be traced to a specific stage.
     *
     * @param lat WGS84 latitude
     * @param lng WGS84 longitude
     * @return Debug info with all intermediate values, or null if not initialized
     */
    fun getDebugInfo(lat: Double, lng: Double): GeoPdfDebugInfo? {
        val converter = crsConverter ?: return null
        val affine = affineTransform ?: return null
        val meta = metadata ?: return null

        val projected = converter.convert(lat, lng)
        val (pixelX, pixelY) = affine.transform(projected.x, projected.y)

        return GeoPdfDebugInfo(
            rawLat = lat,
            rawLng = lng,
            projectedX = projected.x,
            projectedY = projected.y,
            pixelX = pixelX,
            pixelY = pixelY,
            crsType = meta.projection.type.name,
            utmZone = meta.projection.utmZone,
            datum = meta.projection.datum,
            affineMatrix = affine.toDebugString()
        )
    }

    /**
     * Reset engine state (call when switching maps).
     */
    fun reset() {
        metadata = null
        crsConverter = null
        affineTransform = null
        dpiScale = 1.0
    }
}
