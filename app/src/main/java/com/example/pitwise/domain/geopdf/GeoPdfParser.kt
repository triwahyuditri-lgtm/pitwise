package com.example.pitwise.domain.geopdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSInteger
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.cos.COSObject
import com.tom_roush.pdfbox.cos.COSString
import java.io.InputStream

/**
 * Parser for GeoPDF geospatial metadata.
 *
 * Extracts spatial information from PDF files conforming to:
 * - **OGC GeoPDF** standard: `/VP` → `/Measure` → `/GPTS`, `/LPTS`, `/GCS`
 * - **Adobe GeoPDF** format: `/LGIDict` → `/Projection`, `/Datum`, `/GPTS`, `/LPTS`
 *
 * Verified against real mining GeoPDF files (e.g. `mes.pdf` with WGS84 UTM Zone 50N).
 *
 * This class is stateless and thread-safe. Parse results are returned as [GeoPdfMetadata].
 */
class GeoPdfParser {

    /**
     * Parse geospatial metadata from a PDF input stream.
     *
     * @param inputStream PDF file data (will be closed after parsing)
     * @return Parsed metadata, or null if no geospatial data found
     */
    fun parse(inputStream: InputStream): GeoPdfMetadata? {
        return try {
            PDDocument.load(inputStream).use { document ->
                if (document.numberOfPages == 0) return null

                val page = document.getPage(0)
                val mediaBox = page.mediaBox
                val pageWidth = mediaBox.width.toDouble()
                val pageHeight = mediaBox.height.toDouble()

                // Try OGC format first (most common in modern GeoPDFs)
                val ogcResult = tryParseOgcFormat(page, pageWidth, pageHeight)
                if (ogcResult != null) return ogcResult

                // Try Adobe LGIDict format
                val adobeResult = tryParseAdobeFormat(page, pageWidth, pageHeight)
                if (adobeResult != null) return adobeResult

                null
            }
        } catch (e: Exception) {
            null
        }
    }

    // ────────────────────────────────────────────────────────────
    // OGC GeoPDF Format
    // ────────────────────────────────────────────────────────────

    /**
     * Parse OGC GeoPDF format.
     *
     * Structure:
     * ```
     * Page dictionary
     *   └─ /VP (array of viewports)
     *       └─ viewport dictionary
     *           ├─ /BBox [x1 y1 x2 y2]
     *           ├─ /Measure dictionary
     *           │   ├─ /Type /Measure
     *           │   ├─ /Subtype /GEO
     *           │   ├─ /Bounds [...]
     *           │   ├─ /GPTS [lat1 lng1 lat2 lng2 ...]
     *           │   ├─ /LPTS [x1 y1 x2 y2 ...]
     *           │   └─ /GCS → projection dictionary
     *           │       ├─ /Type /PROJCS or /GEOGCS
     *           │       └─ /WKT (WKT string)
     *           └─ /Name (viewport name)
     * ```
     *
     * If /VP is not present, try finding /Measure directly on the page.
     */
    private fun tryParseOgcFormat(page: PDPage, pageWidth: Double, pageHeight: Double): GeoPdfMetadata? {
        val pageDict = page.cosObject

        // Strategy 1: Look for /VP (Viewport) array — standard OGC path
        val vpArray = resolveObject(pageDict.getDictionaryObject(COSName.getPDFName("VP")))
        if (vpArray is COSArray && vpArray.size() > 0) {
            // Use the first viewport (most GeoPDFs have a single geo viewport)
            val viewport = resolveObject(vpArray.getObject(0))
            if (viewport is COSDictionary) {
                val measureDict = resolveDictionary(viewport.getDictionaryObject(COSName.getPDFName("Measure")))
                if (measureDict != null) {
                    return parseMeasureDictionary(measureDict, pageWidth, pageHeight)
                }
            }
        }

        // Strategy 2: Look for /Measure directly on the page (some generators skip /VP)
        val measureDict = resolveDictionary(pageDict.getDictionaryObject(COSName.getPDFName("Measure")))
        if (measureDict != null) {
            return parseMeasureDictionary(measureDict, pageWidth, pageHeight)
        }

        // Strategy 3: Walk all page objects looking for /Type/Measure/Subtype/GEO
        return findMeasureDictInObjectTree(page, pageWidth, pageHeight)
    }

    /**
     * Parse a /Measure dictionary with /Subtype/GEO.
     */
    private fun parseMeasureDictionary(
        measureDict: COSDictionary,
        pageWidth: Double,
        pageHeight: Double
    ): GeoPdfMetadata? {
        // Verify subtype is GEO
        val subtype = measureDict.getNameAsString(COSName.SUBTYPE)
            ?: measureDict.getNameAsString(COSName.getPDFName("Subtype"))
        if (subtype != null && subtype != "GEO") return null

        // Parse GPTS (Ground Points) — lat/lng pairs
        val gptsArray = resolveObject(measureDict.getDictionaryObject(COSName.getPDFName("GPTS")))
        val gpts = parseNumberArray(gptsArray) ?: return null
        if (gpts.size < 6) return null // Need at least 3 pairs (6 values)

        val groundPoints = mutableListOf<WorldPoint>()
        for (i in gpts.indices step 2) {
            if (i + 1 < gpts.size) {
                groundPoints.add(WorldPoint(gpts[i], gpts[i + 1])) // lat, lng
            }
        }

        // Parse LPTS (Local Points) — pixel coordinate pairs
        val lptsArray = resolveObject(measureDict.getDictionaryObject(COSName.getPDFName("LPTS")))
        val lpts = parseNumberArray(lptsArray) ?: return null

        val pixelPoints = mutableListOf<PixelPoint>()
        for (i in lpts.indices step 2) {
            if (i + 1 < lpts.size) {
                pixelPoints.add(PixelPoint(lpts[i], lpts[i + 1]))
            }
        }

        if (groundPoints.size != pixelPoints.size) return null

        // Detect if LPTS is normalized (values all in [0,1] range)
        val lptsNormalized = lpts.all { it in 0.0..1.0 }

        // Parse GCS (Geographic/Projected Coordinate System)
        val gcsObj = resolveDictionary(measureDict.getDictionaryObject(COSName.getPDFName("GCS")))
        val projection = if (gcsObj != null) {
            parseProjectionDictionary(gcsObj)
        } else {
            // Default to WGS84 geographic if no GCS found
            ProjectionInfo(
                name = "WGS84",
                type = ProjectionType.GEOGRAPHIC,
                datum = "D_WGS_1984"
            )
        }
        if (projection == null) return null

        // Build bounding box from GPTS
        val lats = groundPoints.map { it.x }
        val lngs = groundPoints.map { it.y }
        val bounds = BoundingBox(
            minLat = lats.min(),
            minLng = lngs.min(),
            maxLat = lats.max(),
            maxLng = lngs.max()
        )

        return GeoPdfMetadata(
            projection = projection,
            gpts = groundPoints,
            lpts = pixelPoints,
            lptsNormalized = lptsNormalized,
            bounds = bounds,
            pageWidth = pageWidth,
            pageHeight = pageHeight
        )
    }

    /**
     * Parse a projection dictionary (/PROJCS or /GEOGCS with /WKT).
     */
    private fun parseProjectionDictionary(dict: COSDictionary): ProjectionInfo? {
        val type = dict.getNameAsString(COSName.TYPE)
            ?: dict.getNameAsString(COSName.getPDFName("Type"))

        val wktObj = dict.getDictionaryObject(COSName.getPDFName("WKT"))
        val wkt = when (val resolved = resolveObject(wktObj)) {
            is COSString -> resolved.string
            else -> null
        }

        return when {
            type == "PROJCS" && wkt != null -> parseProjectedCrsFromWkt(wkt)
            type == "GEOGCS" && wkt != null -> parseGeographicCrsFromWkt(wkt)
            type == "PROJCS" -> parseProjectedCrsFromDict(dict)
            type == "GEOGCS" -> ProjectionInfo(
                name = "WGS84",
                type = ProjectionType.GEOGRAPHIC,
                datum = "D_WGS_1984"
            )
            else -> {
                // Check if GCS references another object that is PROJCS
                val innerGcs = resolveDictionary(dict.getDictionaryObject(COSName.getPDFName("GCS")))
                if (innerGcs != null) parseProjectionDictionary(innerGcs)
                else if (wkt != null) parseProjectedCrsFromWkt(wkt)
                else null
            }
        }
    }

    /**
     * Parse a PROJCS WKT string to extract projection parameters.
     *
     * Example WKT from mes.pdf:
     * ```
     * PROJCS["WGS_1984_UTM_Zone_50N",
     *   GEOGCS["GCS_WGS_1984",
     *     DATUM["D_WGS_1984",SPHEROID["WGS_1984",6378137.0,298.257223563]],
     *     PRIMEM["Greenwich",0.0],
     *     UNIT["Degree",0.0174532925199433]],
     *   PROJECTION["Transverse_Mercator"],
     *   PARAMETER["False_Easting",500000.0],
     *   PARAMETER["False_Northing",0.0],
     *   PARAMETER["Central_Meridian",117.0],
     *   PARAMETER["Scale_Factor",0.9996],
     *   PARAMETER["Latitude_Of_Origin",0.0],
     *   UNIT["Meter",1.0]]
     * ```
     */
    private fun parseProjectedCrsFromWkt(wkt: String): ProjectionInfo {
        val projName = extractWktName(wkt, "PROJCS") ?: "Unknown"
        val datumName = extractWktName(wkt, "DATUM") ?: "Unknown"
        val projectionMethod = extractWktName(wkt, "PROJECTION") ?: ""

        val falseEasting = extractWktParameter(wkt, "False_Easting")
        val falseNorthing = extractWktParameter(wkt, "False_Northing")
        val centralMeridian = extractWktParameter(wkt, "Central_Meridian")
        val scaleFactor = extractWktParameter(wkt, "Scale_Factor")
        val latOfOrigin = extractWktParameter(wkt, "Latitude_Of_Origin")

        // Detect projection type
        val type: ProjectionType
        var utmZone: Int? = null
        var isNorthern: Boolean? = null

        if (projName.contains("UTM", ignoreCase = true)) {
            type = ProjectionType.UTM
            // Extract zone and hemisphere from name like "WGS_1984_UTM_Zone_50N"
            val utmMatch = Regex("""Zone[_ ]?(\d+)([NS]?)""", RegexOption.IGNORE_CASE).find(projName)
            if (utmMatch != null) {
                utmZone = utmMatch.groupValues[1].toIntOrNull()
                isNorthern = utmMatch.groupValues[2].uppercase() != "S"
            }
        } else when {
            projectionMethod.contains("Transverse_Mercator", ignoreCase = true) -> {
                type = ProjectionType.TRANSVERSE_MERCATOR
            }
            projectionMethod.contains("Lambert", ignoreCase = true) -> {
                type = ProjectionType.LAMBERT_CONFORMAL_CONIC
            }
            projectionMethod.contains("Mercator", ignoreCase = true) -> {
                type = ProjectionType.MERCATOR
            }
            else -> {
                type = ProjectionType.UNKNOWN
            }
        }

        return ProjectionInfo(
            name = projName,
            type = type,
            datum = datumName,
            utmZone = utmZone,
            isNorthernHemisphere = isNorthern,
            centralMeridian = centralMeridian,
            scaleFactor = scaleFactor,
            falseEasting = falseEasting,
            falseNorthing = falseNorthing,
            latitudeOfOrigin = latOfOrigin,
            rawWkt = wkt
        )
    }

    /**
     * Parse a GEOGCS WKT string (geographic CRS, no projection).
     */
    private fun parseGeographicCrsFromWkt(wkt: String): ProjectionInfo {
        val name = extractWktName(wkt, "GEOGCS") ?: "WGS84"
        val datumName = extractWktName(wkt, "DATUM") ?: "D_WGS_1984"

        return ProjectionInfo(
            name = name,
            type = ProjectionType.GEOGRAPHIC,
            datum = datumName,
            rawWkt = wkt
        )
    }

    /**
     * Parse projection from dictionary keys (fallback when no WKT).
     */
    private fun parseProjectedCrsFromDict(dict: COSDictionary): ProjectionInfo {
        val projType = getStringValue(dict, "ProjectionType") ?: "Unknown"
        val datum = getStringValue(dict, "Datum") ?: "WGS84"

        val type = when {
            projType.contains("UTM", ignoreCase = true) -> ProjectionType.UTM
            projType.contains("TM", ignoreCase = true) -> ProjectionType.TRANSVERSE_MERCATOR
            projType.contains("Lambert", ignoreCase = true) -> ProjectionType.LAMBERT_CONFORMAL_CONIC
            else -> ProjectionType.UNKNOWN
        }

        return ProjectionInfo(
            name = projType,
            type = type,
            datum = datum
        )
    }

    // ────────────────────────────────────────────────────────────
    // Adobe LGIDict Format
    // ────────────────────────────────────────────────────────────

    /**
     * Parse Adobe GeoPDF format.
     *
     * Structure:
     * ```
     * Page dictionary
     *   └─ /LGIDict (array or dictionary)
     *       ├─ /Projection dictionary
     *       │   ├─ /ProjectionType (string)
     *       │   ├─ /Datum (string)
     *       │   ├─ /Zone (integer)
     *       │   └─ /Hemisphere (string "N" or "S")
     *       ├─ /GPTS [lat1 lng1 lat2 lng2 ...]
     *       ├─ /LPTS [x1 y1 x2 y2 ...]
     *       └─ /Bounds [...]
     * ```
     */
    private fun tryParseAdobeFormat(page: PDPage, pageWidth: Double, pageHeight: Double): GeoPdfMetadata? {
        val pageDict = page.cosObject
        val lgiObj = resolveObject(pageDict.getDictionaryObject(COSName.getPDFName("LGIDict")))

        val lgiDict: COSDictionary = when (lgiObj) {
            is COSArray -> {
                if (lgiObj.size() > 0) resolveDictionary(lgiObj.getObject(0)) ?: return null
                else return null
            }
            is COSDictionary -> lgiObj
            else -> return null
        }

        // Parse GPTS
        val gptsArray = resolveObject(lgiDict.getDictionaryObject(COSName.getPDFName("GPTS")))
        val gpts = parseNumberArray(gptsArray) ?: return null
        if (gpts.size < 6) return null

        val groundPoints = mutableListOf<WorldPoint>()
        for (i in gpts.indices step 2) {
            if (i + 1 < gpts.size) {
                groundPoints.add(WorldPoint(gpts[i], gpts[i + 1]))
            }
        }

        // Parse LPTS
        val lptsArray = resolveObject(lgiDict.getDictionaryObject(COSName.getPDFName("LPTS")))
        val lpts = parseNumberArray(lptsArray) ?: return null

        val pixelPoints = mutableListOf<PixelPoint>()
        for (i in lpts.indices step 2) {
            if (i + 1 < lpts.size) {
                pixelPoints.add(PixelPoint(lpts[i], lpts[i + 1]))
            }
        }

        if (groundPoints.size != pixelPoints.size) return null

        val lptsNormalized = lpts.all { it in 0.0..1.0 }

        // Parse Projection
        val projDict = resolveDictionary(lgiDict.getDictionaryObject(COSName.getPDFName("Projection")))
        val projection = if (projDict != null) {
            parseAdobeProjection(projDict)
        } else {
            ProjectionInfo(
                name = "WGS84",
                type = ProjectionType.GEOGRAPHIC,
                datum = "D_WGS_1984"
            )
        }

        val lats = groundPoints.map { it.x }
        val lngs = groundPoints.map { it.y }
        val bounds = BoundingBox(
            minLat = lats.min(),
            minLng = lngs.min(),
            maxLat = lats.max(),
            maxLng = lngs.max()
        )

        return GeoPdfMetadata(
            projection = projection,
            gpts = groundPoints,
            lpts = pixelPoints,
            lptsNormalized = lptsNormalized,
            bounds = bounds,
            pageWidth = pageWidth,
            pageHeight = pageHeight
        )
    }

    /**
     * Parse Adobe-format /Projection dictionary.
     */
    private fun parseAdobeProjection(dict: COSDictionary): ProjectionInfo {
        val projType = getStringValue(dict, "ProjectionType") ?: "Unknown"
        val datum = getStringValue(dict, "Datum") ?: "WGS84"
        val zone = getIntValue(dict, "Zone")
        val hemisphere = getStringValue(dict, "Hemisphere")

        val type = when {
            projType.contains("UTM", ignoreCase = true) -> ProjectionType.UTM
            projType.contains("TM", ignoreCase = true) ||
                    projType.contains("Transverse", ignoreCase = true) -> ProjectionType.TRANSVERSE_MERCATOR
            projType.contains("Lambert", ignoreCase = true) -> ProjectionType.LAMBERT_CONFORMAL_CONIC
            projType.contains("Mercator", ignoreCase = true) -> ProjectionType.MERCATOR
            else -> ProjectionType.UNKNOWN
        }

        val falseEasting = getDoubleValue(dict, "FalseEasting")
        val falseNorthing = getDoubleValue(dict, "FalseNorthing")
        val centralMeridian = getDoubleValue(dict, "CentralMeridian")
        val scaleFactor = getDoubleValue(dict, "ScaleFactor")

        return ProjectionInfo(
            name = projType,
            type = type,
            datum = datum,
            utmZone = zone,
            isNorthernHemisphere = hemisphere?.uppercase()?.firstOrNull() == 'N',
            centralMeridian = centralMeridian,
            scaleFactor = scaleFactor,
            falseEasting = falseEasting,
            falseNorthing = falseNorthing
        )
    }

    // ────────────────────────────────────────────────────────────
    // Object Tree Search (fallback)
    // ────────────────────────────────────────────────────────────

    /**
     * Walk page's object references to find /Type/Measure/Subtype/GEO dictionaries.
     * Used when neither /VP nor direct /Measure is found on the page dictionary.
     */
    private fun findMeasureDictInObjectTree(
        page: PDPage,
        pageWidth: Double,
        pageHeight: Double
    ): GeoPdfMetadata? {
        val visited = mutableSetOf<Long>()
        return searchForMeasureDict(page.cosObject, pageWidth, pageHeight, visited, depth = 0)
    }

    private fun searchForMeasureDict(
        obj: COSBase?,
        pageWidth: Double,
        pageHeight: Double,
        visited: MutableSet<Long>,
        depth: Int
    ): GeoPdfMetadata? {
        if (depth > 10 || obj == null) return null

        val resolved = resolveObject(obj)

        when (resolved) {
            is COSDictionary -> {
                // Check if this is a Measure dict
                val type = resolved.getNameAsString(COSName.TYPE)
                    ?: resolved.getNameAsString(COSName.getPDFName("Type"))
                val subtype = resolved.getNameAsString(COSName.SUBTYPE)
                    ?: resolved.getNameAsString(COSName.getPDFName("Subtype"))

                if (type == "Measure" && subtype == "GEO") {
                    return parseMeasureDictionary(resolved, pageWidth, pageHeight)
                }

                // Recursively search values
                for (key in resolved.keySet()) {
                    val value = resolved.getDictionaryObject(key)
                    val result = searchForMeasureDict(value, pageWidth, pageHeight, visited, depth + 1)
                    if (result != null) return result
                }
            }
            is COSArray -> {
                for (i in 0 until resolved.size()) {
                    val result = searchForMeasureDict(resolved.getObject(i), pageWidth, pageHeight, visited, depth + 1)
                    if (result != null) return result
                }
            }
        }

        return null
    }

    // ────────────────────────────────────────────────────────────
    // WKT Parsing Utilities
    // ────────────────────────────────────────────────────────────

    /**
     * Extract the name string from a WKT element.
     * e.g. PROJCS["WGS_1984_UTM_Zone_50N",...] → "WGS_1984_UTM_Zone_50N"
     */
    private fun extractWktName(wkt: String, element: String): String? {
        val pattern = Regex("""$element\["([^"]+)"""")
        return pattern.find(wkt)?.groupValues?.get(1)
    }

    /**
     * Extract a PARAMETER value from WKT.
     * e.g. PARAMETER["False_Easting",500000.0] → 500000.0
     */
    private fun extractWktParameter(wkt: String, paramName: String): Double? {
        val pattern = Regex("""PARAMETER\["$paramName"\s*,\s*([0-9.eE+-]+)\]""", RegexOption.IGNORE_CASE)
        return pattern.find(wkt)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    // ────────────────────────────────────────────────────────────
    // PDF Object Utilities
    // ────────────────────────────────────────────────────────────

    /**
     * Resolve a COSBase that may be an indirect reference (COSObject).
     */
    private fun resolveObject(obj: COSBase?): COSBase? {
        if (obj == null) return null
        return when (obj) {
            is COSObject -> obj.getObject()
            else -> obj
        }
    }

    /**
     * Resolve and cast to COSDictionary.
     */
    private fun resolveDictionary(obj: COSBase?): COSDictionary? {
        return resolveObject(obj) as? COSDictionary
    }

    /**
     * Parse a COSArray of numbers into a List<Double>.
     */
    private fun parseNumberArray(obj: COSBase?): List<Double>? {
        val array = resolveObject(obj) as? COSArray ?: return null
        val result = mutableListOf<Double>()
        for (i in 0 until array.size()) {
            val item = resolveObject(array.getObject(i))
            when (item) {
                is COSNumber -> result.add(item.doubleValue())
                is COSFloat -> result.add(item.doubleValue())
                is COSInteger -> result.add(item.doubleValue())
                else -> return null // Unexpected type
            }
        }
        return result
    }

    /**
     * Get a string value from a dictionary by key name.
     */
    private fun getStringValue(dict: COSDictionary, key: String): String? {
        val obj = resolveObject(dict.getDictionaryObject(COSName.getPDFName(key)))
        return when (obj) {
            is COSString -> obj.string
            is COSName -> obj.name
            else -> null
        }
    }

    /**
     * Get an integer value from a dictionary by key name.
     */
    private fun getIntValue(dict: COSDictionary, key: String): Int? {
        val obj = resolveObject(dict.getDictionaryObject(COSName.getPDFName(key)))
        return when (obj) {
            is COSInteger -> obj.intValue()
            is COSNumber -> obj.intValue()
            else -> null
        }
    }

    /**
     * Get a double value from a dictionary by key name.
     */
    private fun getDoubleValue(dict: COSDictionary, key: String): Double? {
        val obj = resolveObject(dict.getDictionaryObject(COSName.getPDFName(key)))
        return when (obj) {
            is COSNumber -> obj.doubleValue()
            else -> null
        }
    }
}
