package com.example.pitwise.domain.geopdf

/**
 * A point in projected CRS space (e.g. UTM easting/northing in meters).
 */
data class WorldPoint(val x: Double, val y: Double)

/**
 * A point in PDF page coordinate space.
 * For normalized LPTS: values in [0,1] representing fraction of page width/height.
 * For absolute pixel coordinates: PDF user-space units (1/72 inch).
 */
data class PixelPoint(val x: Double, val y: Double)

/**
 * Geographic bounding box in WGS84 degrees.
 */
data class BoundingBox(
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double
)

/**
 * Projection parameters extracted from GeoPDF metadata or WKT string.
 */
data class ProjectionInfo(
    /** Projection name, e.g. "WGS_1984_UTM_Zone_50N", "Transverse_Mercator" */
    val name: String,
    /** Projection type */
    val type: ProjectionType,
    /** Datum name, e.g. "D_WGS_1984", "D_NAD_1983" */
    val datum: String,
    /** UTM zone number (1–60), null if not UTM */
    val utmZone: Int? = null,
    /** True = northern hemisphere, false = southern. Null if not UTM. */
    val isNorthernHemisphere: Boolean? = null,
    /** Central meridian in degrees */
    val centralMeridian: Double? = null,
    /** Scale factor at central meridian (typically 0.9996 for UTM) */
    val scaleFactor: Double? = null,
    /** False easting in projection units (meters) */
    val falseEasting: Double? = null,
    /** False northing in projection units (meters) */
    val falseNorthing: Double? = null,
    /** Latitude of origin in degrees */
    val latitudeOfOrigin: Double? = null,
    /** Raw WKT string for proj4j usage */
    val rawWkt: String? = null
)

/**
 * Supported projection types.
 */
enum class ProjectionType {
    UTM,
    TRANSVERSE_MERCATOR,
    LAMBERT_CONFORMAL_CONIC,
    MERCATOR,
    GEOGRAPHIC,  // lat/lon directly, no projection
    UNKNOWN
}

/**
 * Ground control point: a matched pair of geographic and pixel coordinates.
 */
data class ControlPointPair(
    /** Geographic coordinate (WGS84 lat/lng or projected) */
    val world: WorldPoint,
    /** Corresponding pixel coordinate in PDF space */
    val pixel: PixelPoint
)

/**
 * Complete geospatial metadata extracted from a GeoPDF file.
 */
data class GeoPdfMetadata(
    /** Projection information */
    val projection: ProjectionInfo,
    /** Ground control points in WGS84 (lat/lng) — from GPTS array */
    val gpts: List<WorldPoint>,
    /** Local/pixel control points — from LPTS array (often normalized 0–1) */
    val lpts: List<PixelPoint>,
    /** Whether LPTS values are normalized (0–1 range) */
    val lptsNormalized: Boolean,
    /** Geographic bounding box derived from GPTS */
    val bounds: BoundingBox,
    /** PDF page width in user-space units (points) */
    val pageWidth: Double,
    /** PDF page height in user-space units (points) */
    val pageHeight: Double
)

/**
 * Result of GeoPDF metadata validation.
 */
sealed class GeoPdfValidationResult {
    data class Valid(val metadata: GeoPdfMetadata) : GeoPdfValidationResult()
    data class InsufficientControlPoints(val count: Int) : GeoPdfValidationResult()
    data object NoCrsDetected : GeoPdfValidationResult()
    data object NoGeospatialData : GeoPdfValidationResult()
    data class ParseError(val message: String) : GeoPdfValidationResult()
}

/**
 * Debug information for the full GPS→Screen transformation pipeline.
 * Used for fishbone failure analysis.
 */
data class GeoPdfDebugInfo(
    val rawLat: Double,
    val rawLng: Double,
    val projectedX: Double,
    val projectedY: Double,
    val pixelX: Double,
    val pixelY: Double,
    val crsType: String,
    val utmZone: Int?,
    val datum: String,
    val affineMatrix: String
)
