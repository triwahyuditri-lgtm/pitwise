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
        // We use the new AffineMatrix class
        // But need to solve for coefficients first. 

        // Populate srcPoints (projected coordinates) once
        for (i in geoPdfMetadata.gpts.indices) {
            val gpt = geoPdfMetadata.gpts[i]
            val projected = converter.convert(gpt.x, gpt.y)
            srcPoints.add(Pair(projected.x, projected.y))
        }
        
        // 1. Prepare Destination Candidates
        // If LPTS is normalized, we don't know if Y is Top-Down (0=Top) or Bottom-Up (0=Bottom).
        // We try BOTH hypotheses and pick the one with better RMSE.
        
        var selectedTransform: GeoAffineTransform? = null
        var selectedRmse = Double.MAX_VALUE
        
        // Hypothesis A: Standard / Top-Down (Y=0 is Top)
        // This is standard for PDF and Screen coordinates.
        // If normalized: pixelY = lpt.y * height
        // If raw: pixelY = lpt.y
        val dstPointsA = mutableListOf<Pair<Double, Double>>()
        for (i in geoPdfMetadata.gpts.indices) {
             val lpt = geoPdfMetadata.lpts[i]
             val px = if (geoPdfMetadata.lptsNormalized) lpt.x * geoPdfMetadata.pageWidth else lpt.x
             val py = if (geoPdfMetadata.lptsNormalized) lpt.y * geoPdfMetadata.pageHeight else lpt.y
             dstPointsA.add(px to py)
        }
        
        val transformA = GeoAffineTransform.fromControlPoints(srcPoints, dstPointsA)
        if (transformA != null) {
            val rmseA = transformA.calculateRMSE(srcPoints, dstPointsA)
            selectedTransform = transformA
            selectedRmse = rmseA
        }

        // Hypothesis B: Flipped / Bottom-Up (Y=0 is Bottom) - ONLY for normalized
        // Some formats (like old OGC or specific exporters) use Cartesian math where Y increases UP.
        // pixelY = (1.0 - lpt.y) * height
        if (geoPdfMetadata.lptsNormalized) {
            val dstPointsB = mutableListOf<Pair<Double, Double>>()
            for (i in geoPdfMetadata.gpts.indices) {
                val lpt = geoPdfMetadata.lpts[i]
                val px = lpt.x * geoPdfMetadata.pageWidth
                val py = (1.0 - lpt.y) * geoPdfMetadata.pageHeight
                dstPointsB.add(px to py)
            }
            
            val transformB = GeoAffineTransform.fromControlPoints(srcPoints, dstPointsB)
            if (transformB != null) {
                val rmseB = transformB.calculateRMSE(srcPoints, dstPointsB)
                
                // transformA might be null, or we compare RMSE (lower is better)
                if (transformA == null) {
                    selectedTransform = transformB
                    selectedRmse = rmseB
                } else {
                    // Ambiguity check: if both have similar RMSE (e.g. perfect fit),
                    // prefer the one that results in North-Up orientation (d < 0).
                    // In standard map projection (Y increases North) vs Screen (Y increases South),
                    // we expect d (scaleY) to be NEGATIVE.
                    // If d is positive, the map is upside-down (South-Up).
                    
                    val diff = rmseB - selectedRmse // (rmseA)
                    if (kotlin.math.abs(diff) < 1.0) { // arbitrary small tolerance, e.g. 1 pixel
                        val dA = transformA.d
                        val dB = transformB.d
                        
                        // If A is South-Up (>0) and B is North-Up (<0), prefer B
                        if (dA > 0 && dB < 0) {
                             selectedTransform = transformB
                             selectedRmse = rmseB
                        }
                        // If A is North-Up (<0) and B is South-Up (>0), stick with A (already selected)
                    } else if (rmseB < selectedRmse) {
                         selectedTransform = transformB
                         selectedRmse = rmseB
                    }
                }
            }
        }

        if (selectedTransform == null) {
             return GeoPdfValidationResult.ParseError("Failed to solve affine transform (Singular Matrix or Collinear Points)")
        }
        
        // Final Singular Check
        // If RMSE is huge, maybe warn? For now we accept it but log it.
        // But check invertibility.
        
        val matrix = com.example.pitwise.domain.transform.AffineMatrix(
            selectedTransform.a, selectedTransform.b, selectedTransform.tx,
            selectedTransform.c, selectedTransform.d, selectedTransform.ty
        )
        
        val inverse = matrix.invert()
        if (inverse == null) {
             val debug = "| A=%.4f B=%.4f TX=%.2f |\n| C=%.4f D=%.4f TY=%.2f |".format(
                 matrix.a, matrix.b, matrix.tx, matrix.c, matrix.d, matrix.ty
             )
             return GeoPdfValidationResult.ParseError("Transform is valid but not invertible (Singular). $debug")
        }

        this.affineMatrix = matrix
        this.inverseAffineMatrix = inverse

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
