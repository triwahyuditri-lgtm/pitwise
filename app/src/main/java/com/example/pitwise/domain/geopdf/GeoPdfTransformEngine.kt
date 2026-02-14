package com.example.pitwise.domain.geopdf

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
    private var affineMatrix: com.example.pitwise.domain.transform.AffineMatrix? = null
    private var inverseAffineMatrix: com.example.pitwise.domain.transform.AffineMatrix? = null

    /** Whether the engine is fully initialized and ready for transforms. */
    val isInitialized: Boolean
        get() = metadata != null && crsConverter != null && affineMatrix != null

    /** The parsed projection info, if available. */
    val projectionInfo: ProjectionInfo?
        get() = metadata?.projection

    /**
     * The precomputed inverse affine matrix (Screen/Pixel -> World).
     * Used for tap inversion.
     */
    val inverseMatrix: com.example.pitwise.domain.transform.AffineMatrix?
        get() = inverseAffineMatrix

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

            // LPTS: denormalize if needed (convert 0-1 → PDF page coordinates)
            val pixelX: Double
            val pixelY: Double
            if (geoPdfMetadata.lptsNormalized) {
                pixelX = lpt.x * geoPdfMetadata.pageWidth
                // In normalized LPTS, Y=0 means top of page, Y=1 means bottom
                // This matches screen coordinates (origin at top-left)
                pixelY = lpt.y * geoPdfMetadata.pageHeight
            } else {
                pixelX = lpt.x
                pixelY = lpt.y
            }
            dstPoints.add(Pair(pixelX, pixelY))
        }

        // We use the new AffineMatrix class
        // But need to solve for coefficients first. 
        // We can reuse the solver logic from GeoAffineTransform manually or port it to AffineMatrix companion?
        // Let's assume we update AffineMatrix companion to have a solver or use a util.
        // Actually, let's keep GeoAffineTransform for solving (since it's already there) 
        // and map it to AffineMatrix.
        val solved = GeoAffineTransform.fromControlPoints(srcPoints, dstPoints)
            ?: return GeoPdfValidationResult.ParseError("Failed to solve affine transform from control points")

        val matrix = com.example.pitwise.domain.transform.AffineMatrix(
            solved.a, solved.b, solved.tx,
            solved.c, solved.d, solved.ty
        )

        this.affineMatrix = matrix
        this.inverseAffineMatrix = matrix.invert()

        return GeoPdfValidationResult.Valid(geoPdfMetadata)
    }

    /**
     * Convert GPS position to PDF pixel coordinate.
     *
     * @param lat WGS84 latitude
     * @param lng WGS84 longitude
     * @return Pixel coordinate in PDF page space, or null if engine not initialized
     */
    fun gpsToPixel(lat: Double, lng: Double): PixelPoint? {
        val converter = crsConverter ?: return null
        val affine = affineMatrix ?: return null

        // Step 1: WGS84 → Projected CRS
        val projected = converter.convert(lat, lng)

        // Step 2: Projected → PDF pixel
        val (pixelX, pixelY) = affine.map(projected.x, projected.y)

        return PixelPoint(pixelX, pixelY)
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
        val affine = affineMatrix ?: return null
        val meta = metadata ?: return null

        val projected = converter.convert(lat, lng)
        val (pixelX, pixelY) = affine.map(projected.x, projected.y)
        return GeoPdfDebugInfo(
            rawLat = lat,
            rawLng = lng,
            projectedX = projected.x,
            projectedY = projected.y,
            pixelX = pixelX,
            pixelY = pixelY,
            crsType = meta.projection.name,
            utmZone = meta.projection.utmZone,
            datum = meta.projection.datum,
            affineMatrix = affine.toString()
        )
    }

    /**
     * Reset engine state (call when switching maps).
     */
    fun reset() {
        metadata = null
        crsConverter = null
        affineMatrix = null
        inverseAffineMatrix = null
    }
}
