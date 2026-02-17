package com.example.pitwise.domain.map

import android.content.Context
import com.example.pitwise.data.local.entity.MapAnnotation
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Export format options for annotations.
 */
enum class ExportFormat(val extension: String, val mimeType: String, val label: String) {
    KML("kml", "application/vnd.google-earth.kml+xml", "KML"),
    KMZ("kmz", "application/vnd.google-earth.kmz", "KMZ"),
    DXF("dxf", "application/dxf", "DXF")
}

/**
 * Exports MapAnnotation objects to KML, KMZ, or DXF file formats.
 *
 * Coordinate conversion:
 * - KML/KMZ require WGS84 (lng, lat) — use the provided [coordToLatLng] converter
 * - DXF uses projected coordinates (UTM meters) — use [coordToProjected] or raw world coords
 */
@Singleton
class AnnotationExporter @Inject constructor() {

    /**
     * Export a single annotation to the specified format.
     *
     * @param context Android context for cache directory access
     * @param annotation The annotation to export
     * @param format Target format
     * @param coordToLatLng Converts world (x,y) → (lat, lng) for KML/KMZ. Null = use raw coords.
     * @param coordToProjected Converts world (x,y) → (easting, northing) for DXF. Null = use raw coords.
     * @return The generated file, or null on failure
     */
    fun export(
        context: Context,
        annotation: MapAnnotation,
        format: ExportFormat,
        coordToLatLng: ((Double, Double) -> Pair<Double, Double>?)? = null,
        coordToProjected: ((Double, Double) -> Pair<Double, Double>?)? = null
    ): File? {
        val points = MapSerializationUtils.parseJsonToPoints(annotation.pointsJson)
        if (points.isEmpty()) return null

        val name = annotation.name.ifEmpty { annotation.type }
        val sanitizedName = name.replace(Regex("[^a-zA-Z0-9_\\- ]"), "_")
        val fileName = "${sanitizedName}_${annotation.id}.${format.extension}"

        return try {
            val cacheDir = File(context.cacheDir, "exports").also { it.mkdirs() }
            val outputFile = File(cacheDir, fileName)

            when (format) {
                ExportFormat.KML -> {
                    val kml = generateKml(annotation, points, coordToLatLng)
                    outputFile.writeText(kml)
                }
                ExportFormat.KMZ -> {
                    val kml = generateKml(annotation, points, coordToLatLng)
                    ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
                        zos.putNextEntry(ZipEntry("doc.kml"))
                        zos.write(kml.toByteArray(Charsets.UTF_8))
                        zos.closeEntry()
                    }
                }
                ExportFormat.DXF -> {
                    val dxf = generateDxf(annotation, points, coordToProjected)
                    outputFile.writeText(dxf)
                }
            }
            outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Export multiple annotations to a single file.
     */
    fun exportAll(
        context: Context,
        annotations: List<MapAnnotation>,
        format: ExportFormat,
        coordToLatLng: ((Double, Double) -> Pair<Double, Double>?)? = null,
        coordToProjected: ((Double, Double) -> Pair<Double, Double>?)? = null
    ): File? {
        if (annotations.isEmpty()) return null

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "pitwise_export_$timestamp.${format.extension}"

        return try {
            val cacheDir = File(context.cacheDir, "exports").also { it.mkdirs() }
            val outputFile = File(cacheDir, fileName)

            when (format) {
                ExportFormat.KML -> {
                    val kml = generateKmlMulti(annotations, coordToLatLng)
                    outputFile.writeText(kml)
                }
                ExportFormat.KMZ -> {
                    val kml = generateKmlMulti(annotations, coordToLatLng)
                    ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
                        zos.putNextEntry(ZipEntry("doc.kml"))
                        zos.write(kml.toByteArray(Charsets.UTF_8))
                        zos.closeEntry()
                    }
                }
                ExportFormat.DXF -> {
                    val dxf = generateDxfMulti(annotations, coordToProjected)
                    outputFile.writeText(dxf)
                }
            }
            outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ════════════════════════════════════════════════════
    // KML Generation
    // ════════════════════════════════════════════════════

    private fun generateKml(
        annotation: MapAnnotation,
        points: List<MapPoint>,
        coordToLatLng: ((Double, Double) -> Pair<Double, Double>?)?
    ): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2">""")
        sb.appendLine("""  <Document>""")
        sb.appendLine("""    <name>${escapeXml(annotation.name.ifEmpty { "PITWISE Export" })}</name>""")
        sb.appendLine("""    <description>Exported from PITWISE</description>""")
        appendPlacemark(sb, annotation, points, coordToLatLng, "    ")
        sb.appendLine("""  </Document>""")
        sb.appendLine("""</kml>""")
        return sb.toString()
    }

    private fun generateKmlMulti(
        annotations: List<MapAnnotation>,
        coordToLatLng: ((Double, Double) -> Pair<Double, Double>?)?
    ): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2">""")
        sb.appendLine("""  <Document>""")
        sb.appendLine("""    <name>PITWISE Export</name>""")
        sb.appendLine("""    <description>Exported from PITWISE</description>""")
        for (ann in annotations) {
            val pts = MapSerializationUtils.parseJsonToPoints(ann.pointsJson)
            if (pts.isNotEmpty()) {
                appendPlacemark(sb, ann, pts, coordToLatLng, "    ")
            }
        }
        sb.appendLine("""  </Document>""")
        sb.appendLine("""</kml>""")
        return sb.toString()
    }

    private fun appendPlacemark(
        sb: StringBuilder,
        annotation: MapAnnotation,
        points: List<MapPoint>,
        coordToLatLng: ((Double, Double) -> Pair<Double, Double>?)?,
        indent: String
    ) {
        val name = annotation.name.ifEmpty { annotation.type }
        sb.appendLine("$indent<Placemark>")
        sb.appendLine("$indent  <name>${escapeXml(name)}</name>")
        if (annotation.description.isNotEmpty()) {
            sb.appendLine("$indent  <description>${escapeXml(annotation.description)}</description>")
        }

        // Convert points to lat/lng
        val geoPoints = points.mapNotNull { pt ->
            coordToLatLng?.invoke(pt.x, pt.y) ?: Pair(pt.y, pt.x) // fallback: assume y=lat, x=lng
        }

        when (annotation.type) {
            "POINT" -> {
                val (lat, lng) = geoPoints.first()
                val alt = annotation.elevation ?: 0.0
                sb.appendLine("$indent  <Point>")
                sb.appendLine("$indent    <coordinates>$lng,$lat,$alt</coordinates>")
                sb.appendLine("$indent  </Point>")
            }
            "LINE" -> {
                sb.appendLine("$indent  <LineString>")
                sb.appendLine("$indent    <tessellate>1</tessellate>")
                sb.appendLine("$indent    <coordinates>")
                for ((lat, lng) in geoPoints) {
                    sb.appendLine("$indent      $lng,$lat,0")
                }
                sb.appendLine("$indent    </coordinates>")
                sb.appendLine("$indent  </LineString>")
            }
            "POLYGON" -> {
                sb.appendLine("$indent  <Polygon>")
                sb.appendLine("$indent    <outerBoundaryIs>")
                sb.appendLine("$indent      <LinearRing>")
                sb.appendLine("$indent        <coordinates>")
                for ((lat, lng) in geoPoints) {
                    sb.appendLine("$indent          $lng,$lat,0")
                }
                // Close the ring
                if (geoPoints.isNotEmpty()) {
                    val (lat, lng) = geoPoints.first()
                    sb.appendLine("$indent          $lng,$lat,0")
                }
                sb.appendLine("$indent        </coordinates>")
                sb.appendLine("$indent      </LinearRing>")
                sb.appendLine("$indent    </outerBoundaryIs>")
                sb.appendLine("$indent  </Polygon>")
            }
        }
        sb.appendLine("$indent</Placemark>")
    }

    // ════════════════════════════════════════════════════
    // DXF Generation (ASCII R12 compatible)
    // ════════════════════════════════════════════════════

    private fun generateDxf(
        annotation: MapAnnotation,
        points: List<MapPoint>,
        coordToProjected: ((Double, Double) -> Pair<Double, Double>?)?
    ): String {
        val sb = StringBuilder()
        appendDxfHeader(sb)
        appendDxfEntitiesStart(sb)
        appendDxfEntity(sb, annotation, points, coordToProjected)
        appendDxfFooter(sb)
        return sb.toString()
    }

    private fun generateDxfMulti(
        annotations: List<MapAnnotation>,
        coordToProjected: ((Double, Double) -> Pair<Double, Double>?)?
    ): String {
        val sb = StringBuilder()
        appendDxfHeader(sb)
        appendDxfEntitiesStart(sb)
        for (ann in annotations) {
            val pts = MapSerializationUtils.parseJsonToPoints(ann.pointsJson)
            if (pts.isNotEmpty()) {
                appendDxfEntity(sb, ann, pts, coordToProjected)
            }
        }
        appendDxfFooter(sb)
        return sb.toString()
    }

    private fun appendDxfHeader(sb: StringBuilder) {
        sb.appendLine("0\nSECTION\n2\nHEADER\n0\nENDSEC")
        sb.appendLine("0\nSECTION\n2\nTABLES")
        // Layer table with default layer
        sb.appendLine("0\nTABLE\n2\nLAYER\n70\n1")
        sb.appendLine("0\nLAYER\n2\nPITWISE\n70\n0\n62\n7\n6\nCONTINUOUS")
        sb.appendLine("0\nENDTAB")
        sb.appendLine("0\nENDSEC")
    }

    private fun appendDxfEntitiesStart(sb: StringBuilder) {
        sb.appendLine("0\nSECTION\n2\nENTITIES")
    }

    private fun appendDxfFooter(sb: StringBuilder) {
        sb.appendLine("0\nENDSEC\n0\nEOF")
    }

    private fun appendDxfEntity(
        sb: StringBuilder,
        annotation: MapAnnotation,
        points: List<MapPoint>,
        coordToProjected: ((Double, Double) -> Pair<Double, Double>?)?
    ) {
        val layer = annotation.layer.ifEmpty { "PITWISE" }
        val elevation = annotation.elevation ?: 0.0

        // Convert points
        val projPoints = points.map { pt ->
            coordToProjected?.invoke(pt.x, pt.y) ?: Pair(pt.x, pt.y)
        }

        when (annotation.type) {
            "POINT" -> {
                val (x, y) = projPoints.first()
                sb.appendLine("0\nPOINT\n8\n$layer\n10\n$x\n20\n$y\n30\n$elevation")
                // Also add a TEXT label if name exists
                if (annotation.name.isNotEmpty()) {
                    sb.appendLine("0\nTEXT\n8\n$layer\n10\n$x\n20\n$y\n30\n$elevation\n40\n2.0\n1\n${annotation.name}")
                }
            }
            "LINE" -> {
                if (projPoints.size == 2) {
                    // Simple LINE entity
                    val (x1, y1) = projPoints[0]
                    val (x2, y2) = projPoints[1]
                    sb.appendLine("0\nLINE\n8\n$layer\n10\n$x1\n20\n$y1\n30\n$elevation\n11\n$x2\n21\n$y2\n31\n$elevation")
                } else {
                    // LWPOLYLINE for multi-segment lines
                    sb.appendLine("0\nLWPOLYLINE\n8\n$layer\n90\n${projPoints.size}\n70\n0")
                    for ((x, y) in projPoints) {
                        sb.appendLine("10\n$x\n20\n$y")
                    }
                }
            }
            "POLYGON" -> {
                // Closed LWPOLYLINE
                sb.appendLine("0\nLWPOLYLINE\n8\n$layer\n90\n${projPoints.size}\n70\n1")
                for ((x, y) in projPoints) {
                    sb.appendLine("10\n$x\n20\n$y")
                }
            }
        }
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
