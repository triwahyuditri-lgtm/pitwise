package com.example.pitwise.domain.geopdf

import com.example.pitwise.domain.map.CoordinateUtils
import org.locationtech.proj4j.CRSFactory
import org.locationtech.proj4j.CoordinateTransform
import org.locationtech.proj4j.CoordinateTransformFactory
import org.locationtech.proj4j.ProjCoordinate

/**
 * Coordinate Reference System converter.
 *
 * Converts WGS84 lat/lng to the projected CRS specified in GeoPDF metadata.
 * Uses the existing [CoordinateUtils] for UTM (proven code, no external dependencies)
 * and proj4j only for non-UTM projections (Transverse Mercator, Lambert, Mercator).
 *
 * Thread-safe: all state is created at initialization and immutable after.
 */
class CrsConverter private constructor(
    private val projectionInfo: ProjectionInfo,
    private val proj4Transform: CoordinateTransform?,
    private val proj4InverseTransform: CoordinateTransform?
) {

    /**
     * Convert WGS84 latitude/longitude to projected coordinates.
     *
     * @param lat WGS84 latitude in degrees
     * @param lng WGS84 longitude in degrees
     * @return Projected coordinate as [WorldPoint] (e.g. UTM easting/northing)
     */
    fun convert(lat: Double, lng: Double): WorldPoint {
        return when (projectionInfo.type) {
            ProjectionType.UTM -> convertUtm(lat, lng)
            ProjectionType.GEOGRAPHIC -> WorldPoint(lng, lat)
            else -> convertWithProj4(lat, lng)
        }
    }

    /**
     * Convert projected coordinates to WGS84 latitude/longitude.
     * Inverse of [convert].
     */
    fun convertInverse(x: Double, y: Double): Pair<Double, Double> {
        return when (projectionInfo.type) {
            ProjectionType.UTM -> {
                val zone = projectionInfo.utmZone ?: 1
                val isNorth = projectionInfo.isNorthernHemisphere ?: true
                CoordinateUtils.utmToLatLng(zone, x, y, !isNorth)
            }
            ProjectionType.GEOGRAPHIC -> Pair(y, x) // y=lat, x=lng
            else -> convertInverseWithProj4(x, y)
        }
    }

    /**
     * UTM conversion using existing CoordinateUtils (pure math, no proj4j dependency).
     */
    private fun convertUtm(lat: Double, lng: Double): WorldPoint {
        val utm = CoordinateUtils.latLngToUtm(lat, lng)
        return WorldPoint(utm.easting, utm.northing)
    }

    /**
     * Generic projection conversion using proj4j.
     */
    private fun convertWithProj4(lat: Double, lng: Double): WorldPoint {
        val transform = proj4Transform
            ?: throw IllegalStateException("proj4j transform not initialized for ${projectionInfo.type}")

        val srcCoord = ProjCoordinate(lng, lat) // proj4j uses (x=lng, y=lat)
        val dstCoord = ProjCoordinate()
        transform.transform(srcCoord, dstCoord)

        return WorldPoint(dstCoord.x, dstCoord.y)
    }

    private fun convertInverseWithProj4(x: Double, y: Double): Pair<Double, Double> {
        val transform = proj4InverseTransform
            ?: throw IllegalStateException("proj4j inverse transform not initialized for ${projectionInfo.type}")

        val srcCoord = ProjCoordinate(x, y)
        val dstCoord = ProjCoordinate()
        transform.transform(srcCoord, dstCoord)

        // dstCoord is (lng, lat)
        return Pair(dstCoord.y, dstCoord.x)
    }

    companion object {

        /**
         * Create a CRS converter from GeoPDF projection info.
         *
         * For UTM and GEOGRAPHIC types, no proj4j initialization is needed
         * (uses pure-math CoordinateUtils). For other projections, proj4j
         * is initialized with the appropriate parameters.
         *
         * @param projection Projection parameters from GeoPDF metadata
         * @return Configured converter, or null if projection is unsupported
         */
        fun create(projection: ProjectionInfo): CrsConverter? {
            return when (projection.type) {
                // UTM and GEOGRAPHIC use pure math — no proj4j needed
                ProjectionType.UTM,
                ProjectionType.GEOGRAPHIC -> CrsConverter(projection, null, null)

                // For other projections, build proj4j transform
                ProjectionType.TRANSVERSE_MERCATOR,
                ProjectionType.LAMBERT_CONFORMAL_CONIC,
                ProjectionType.MERCATOR -> {
                    try {
                        val (forward, inverse) = buildProj4Transforms(projection) ?: return null
                        CrsConverter(projection, forward, inverse)
                    } catch (e: Exception) {
                        null
                    }
                }

                ProjectionType.UNKNOWN -> null
            }
        }

        /**
         * Build proj4j transforms (forward and inverse) from projection parameters.
         * Only called for non-UTM, non-GEOGRAPHIC projections.
         */
        private fun buildProj4Transforms(projection: ProjectionInfo): Pair<CoordinateTransform, CoordinateTransform>? {
            val proj4String = buildProj4String(projection) ?: return null

            val crsFactory = CRSFactory()
            val transformFactory = CoordinateTransformFactory()

            val wgs84 = crsFactory.createFromParameters("WGS84", "+proj=longlat +datum=WGS84 +no_defs")
            val targetCrs = crsFactory.createFromParameters(projection.name, proj4String)

            val forward = transformFactory.createTransform(wgs84, targetCrs)
            val inverse = transformFactory.createTransform(targetCrs, wgs84)

            return Pair(forward, inverse)
        }

        /**
         * Build a proj4 parameter string from projection info.
         */
        private fun buildProj4String(projection: ProjectionInfo): String? {
            val parts = mutableListOf<String>()

            when (projection.type) {
                ProjectionType.TRANSVERSE_MERCATOR -> {
                    parts.add("+proj=tmerc")
                    projection.centralMeridian?.let { parts.add("+lon_0=$it") }
                    projection.latitudeOfOrigin?.let { parts.add("+lat_0=$it") }
                    projection.scaleFactor?.let { parts.add("+k=$it") }
                    projection.falseEasting?.let { parts.add("+x_0=$it") }
                    projection.falseNorthing?.let { parts.add("+y_0=$it") }
                }
                ProjectionType.LAMBERT_CONFORMAL_CONIC -> {
                    parts.add("+proj=lcc")
                    projection.centralMeridian?.let { parts.add("+lon_0=$it") }
                    projection.latitudeOfOrigin?.let { parts.add("+lat_0=$it") }
                    projection.falseEasting?.let { parts.add("+x_0=$it") }
                    projection.falseNorthing?.let { parts.add("+y_0=$it") }
                }
                ProjectionType.MERCATOR -> {
                    parts.add("+proj=merc")
                    projection.centralMeridian?.let { parts.add("+lon_0=$it") }
                    projection.scaleFactor?.let { parts.add("+k=$it") }
                    projection.falseEasting?.let { parts.add("+x_0=$it") }
                    projection.falseNorthing?.let { parts.add("+y_0=$it") }
                }
                else -> return null
            }

            // WGS84 datum (most common)
            val datum = projection.datum.uppercase()
            if (datum.contains("WGS") && datum.contains("84")) {
                parts.add("+datum=WGS84")
            } else if (datum.contains("NAD") && datum.contains("83")) {
                parts.add("+datum=NAD83")
            } else {
                parts.add("+ellps=WGS84")
            }

            parts.add("+units=m")
            parts.add("+no_defs")

            return parts.joinToString(" ")
        }
    }
}
