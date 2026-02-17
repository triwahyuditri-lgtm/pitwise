package com.example.pitwise.domain.map

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses DXF file text content into supported geometry and annotation entities.
 *
 * Supported: POINT, LINE, POLYLINE, LWPOLYLINE, TEXT, MTEXT
 * Ignored: HATCH, BLOCK, 3DFACE, and all others
 *
 * DXF format reference:
 * - Group code/value pairs, line by line
 * - ENTITIES section contains geometry
 * - Group codes: 0=entity type, 1=text content, 10/20/30=X/Y/Z coords,
 *   11/21/31=end/alignment coords, 40=text height, 50=rotation angle,
 *   70=flags, 72=horizontal justification, 90=vertex count
 *   38=elevation for LWPOLYLINE
 */
@Singleton
class DxfParser @Inject constructor() {

    fun parse(content: String): DxfFile {
        val lines = content.lines().map { it.trim() }
        val entities = mutableListOf<DxfEntity>()

        // ── Phase 1: Parse TABLES section for layer colors ──
        val layerColors = parseLayerColors(lines)

        // ── Phase 2: Find ENTITIES section ──
        var i = 0
        while (i < lines.size - 1) {
            if (lines[i] == "2" && lines.getOrNull(i + 1) == "ENTITIES") {
                i += 2
                break
            }
            i++
        }

        // Parse entities
        while (i < lines.size - 1) {
            val groupCode = lines[i].trim()
            val value = lines.getOrNull(i + 1)?.trim() ?: ""

            if (groupCode == "0" && value == "ENDSEC") break

            if (groupCode == "0") {
                when (value) {
                    "POINT" -> {
                        i += 2
                        val result = parsePoint(lines, i)
                        entities.add(result.first)
                        i = result.second
                    }
                    "LINE" -> {
                        i += 2
                        val result = parseLine(lines, i)
                        entities.add(result.first)
                        i = result.second
                    }
                    "LWPOLYLINE" -> {
                        i += 2
                        val result = parseLwPolyline(lines, i)
                        if (result.first.vertices.isNotEmpty()) {
                            entities.add(result.first)
                        }
                        i = result.second
                    }
                    "POLYLINE" -> {
                        i += 2
                        val result = parsePolyline(lines, i)
                        if (result.first.vertices.isNotEmpty()) {
                            entities.add(result.first)
                        }
                        i = result.second
                    }
                    "TEXT" -> {
                        i += 2
                        val result = parseText(lines, i)
                        if (result.first.text.isNotBlank()) {
                            entities.add(result.first)
                        }
                        i = result.second
                    }
                    "MTEXT" -> {
                        i += 2
                        val result = parseMText(lines, i)
                        if (result.first.text.isNotBlank()) {
                            entities.add(result.first)
                        }
                        i = result.second
                    }
                    else -> i += 2 // Skip unsupported entities
                }
            } else {
                i += 2
            }
        }

        // Compute bounding box including Z
        var minX = Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var minZ = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        var maxZ = -Double.MAX_VALUE

        for (entity in entities) {
            when (entity) {
                is DxfEntity.Point -> {
                    minX = minOf(minX, entity.x)
                    minY = minOf(minY, entity.y)
                    minZ = minOf(minZ, entity.z)
                    maxX = maxOf(maxX, entity.x)
                    maxY = maxOf(maxY, entity.y)
                    maxZ = maxOf(maxZ, entity.z)
                }
                is DxfEntity.Line -> {
                    minX = minOf(minX, entity.x1, entity.x2)
                    minY = minOf(minY, entity.y1, entity.y2)
                    minZ = minOf(minZ, entity.z1, entity.z2)
                    maxX = maxOf(maxX, entity.x1, entity.x2)
                    maxY = maxOf(maxY, entity.y1, entity.y2)
                    maxZ = maxOf(maxZ, entity.z1, entity.z2)
                }
                is DxfEntity.Polyline -> {
                    for (v in entity.vertices) {
                        minX = minOf(minX, v.x)
                        minY = minOf(minY, v.y)
                        minZ = minOf(minZ, v.z)
                        maxX = maxOf(maxX, v.x)
                        maxY = maxOf(maxY, v.y)
                        maxZ = maxOf(maxZ, v.z)
                    }
                }
                is DxfEntity.Text -> {
                    minX = minOf(minX, entity.x)
                    minY = minOf(minY, entity.y)
                    minZ = minOf(minZ, entity.z)
                    maxX = maxOf(maxX, entity.x)
                    maxY = maxOf(maxY, entity.y)
                    maxZ = maxOf(maxZ, entity.z)
                }
            }
        }

        if (entities.isEmpty()) {
            minX = 0.0; minY = 0.0; minZ = 0.0
            maxX = 1.0; maxY = 1.0; maxZ = 0.0
        }

        return DxfFile(entities, minX, minY, minZ, maxX, maxY, maxZ, layerColors)
    }

    /**
     * Parse TABLES section to extract layer colors.
     * Each LAYER entry has group code 2 (name) and 62 (color index).
     */
    private fun parseLayerColors(lines: List<String>): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        var i = 0

        // Find TABLES section
        while (i < lines.size - 1) {
            if (lines[i] == "2" && lines.getOrNull(i + 1) == "TABLES") {
                i += 2
                break
            }
            i++
        }
        if (i >= lines.size) return result

        // Scan for LAYER entries within TABLE blocks
        while (i < lines.size - 1) {
            val code = lines[i].trim()
            val value = lines.getOrNull(i + 1)?.trim() ?: ""

            // End of TABLES section
            if (code == "0" && value == "ENDSEC") break

            // Found a LAYER entry
            if (code == "0" && value == "LAYER") {
                i += 2
                var layerName = ""
                var colorIdx = 7  // default white
                while (i < lines.size - 1) {
                    val lc = lines[i].trim()
                    if (lc == "0") break
                    val lv = lines[i + 1].trim()
                    when (lc) {
                        "2" -> layerName = lv
                        "62" -> colorIdx = lv.toIntOrNull() ?: 7
                    }
                    i += 2
                }
                if (layerName.isNotEmpty()) {
                    // Negative color = layer is off/frozen, store absolute value
                    result[layerName] = kotlin.math.abs(colorIdx)
                }
            } else {
                i += 2
            }
        }
        return result
    }

    private fun parsePoint(lines: List<String>, startIndex: Int): Pair<DxfEntity.Point, Int> {
        var i = startIndex
        var x = 0.0
        var y = 0.0
        var z = 0.0
        var layer = ""
        var colorIndex = 256

        while (i < lines.size - 1) {
            val code = lines[i].trim()
            if (code == "0") break
            val value = lines[i + 1].trim()
            when (code) {
                "8" -> layer = value
                "10" -> x = value.toDoubleOrNull() ?: 0.0
                "20" -> y = value.toDoubleOrNull() ?: 0.0
                "30" -> z = value.toDoubleOrNull() ?: 0.0
                "62" -> colorIndex = value.toIntOrNull() ?: 256
            }
            i += 2
        }
        return DxfEntity.Point(x, y, z, layer, colorIndex) to i
    }

    private fun parseLine(lines: List<String>, startIndex: Int): Pair<DxfEntity.Line, Int> {
        var i = startIndex
        var x1 = 0.0; var y1 = 0.0; var z1 = 0.0
        var x2 = 0.0; var y2 = 0.0; var z2 = 0.0
        var layer = ""
        var colorIndex = 256

        while (i < lines.size - 1) {
            val code = lines[i].trim()
            if (code == "0") break
            val value = lines[i + 1].trim()
            when (code) {
                "8" -> layer = value
                "10" -> x1 = value.toDoubleOrNull() ?: 0.0
                "20" -> y1 = value.toDoubleOrNull() ?: 0.0
                "30" -> z1 = value.toDoubleOrNull() ?: 0.0
                "11" -> x2 = value.toDoubleOrNull() ?: 0.0
                "21" -> y2 = value.toDoubleOrNull() ?: 0.0
                "31" -> z2 = value.toDoubleOrNull() ?: 0.0
                "62" -> colorIndex = value.toIntOrNull() ?: 256
            }
            i += 2
        }
        return DxfEntity.Line(x1, y1, z1, x2, y2, z2, layer, colorIndex) to i
    }

    private fun parseLwPolyline(lines: List<String>, startIndex: Int): Pair<DxfEntity.Polyline, Int> {
        var i = startIndex
        val vertices = mutableListOf<DxfEntity.Vertex>()
        var closed = false
        var layer = ""
        var colorIndex = 256
        var currentX: Double? = null
        var currentY: Double? = null
        var currentZ: Double? = null
        var elevation = 0.0  // Group code 38: default elevation for LWPOLYLINE

        while (i < lines.size - 1) {
            val code = lines[i].trim()
            if (code == "0") break
            val value = lines[i + 1].trim()
            when (code) {
                "8" -> layer = value
                "38" -> elevation = value.toDoubleOrNull() ?: 0.0
                "62" -> colorIndex = value.toIntOrNull() ?: 256
                "70" -> closed = (value.toIntOrNull() ?: 0) and 1 == 1
                "10" -> {
                    // Flush previous vertex if we have X+Y
                    if (currentX != null && currentY != null) {
                        vertices.add(DxfEntity.Vertex(currentX!!, currentY!!, currentZ ?: elevation))
                    }
                    // Start new vertex
                    currentX = value.toDoubleOrNull() ?: 0.0
                    currentY = null
                    currentZ = null
                }
                "20" -> {
                    currentY = value.toDoubleOrNull() ?: 0.0
                }
                "30" -> {
                    // Per-vertex Z override for LWPOLYLINE
                    currentZ = value.toDoubleOrNull() ?: 0.0
                }
            }
            i += 2
        }
        // Flush last vertex
        if (currentX != null && currentY != null) {
            vertices.add(DxfEntity.Vertex(currentX!!, currentY!!, currentZ ?: elevation))
        }
        return DxfEntity.Polyline(vertices, closed, layer, colorIndex) to i
    }

    private fun parsePolyline(lines: List<String>, startIndex: Int): Pair<DxfEntity.Polyline, Int> {
        var i = startIndex
        val vertices = mutableListOf<DxfEntity.Vertex>()
        var closed = false
        var layer = ""
        var colorIndex = 256
        var polylineZ = 0.0  // Elevation from POLYLINE header

        // Read flags from POLYLINE header
        while (i < lines.size - 1) {
            val code = lines[i].trim()
            if (code == "0") break
            val value = lines[i + 1].trim()
            when (code) {
                "8" -> layer = value
                "62" -> colorIndex = value.toIntOrNull() ?: 256
                "70" -> closed = (value.toIntOrNull() ?: 0) and 1 == 1
                "30" -> polylineZ = value.toDoubleOrNull() ?: 0.0
            }
            i += 2
        }

        // Read VERTEX entities until SEQEND
        while (i < lines.size - 1) {
            val code = lines[i].trim()
            val value = lines[i + 1].trim()

            if (code == "0" && value == "SEQEND") {
                i += 2
                break
            }
            if (code == "0" && value == "VERTEX") {
                i += 2
                var x = 0.0
                var y = 0.0
                var z: Double? = null  // track if VERTEX has its own Z
                while (i < lines.size - 1) {
                    val vc = lines[i].trim()
                    if (vc == "0") break
                    val vv = lines[i + 1].trim()
                    when (vc) {
                        "10" -> x = vv.toDoubleOrNull() ?: 0.0
                        "20" -> y = vv.toDoubleOrNull() ?: 0.0
                        "30" -> z = vv.toDoubleOrNull() ?: 0.0
                    }
                    i += 2
                }
                vertices.add(DxfEntity.Vertex(x, y, z ?: polylineZ))
            } else {
                i += 2
            }
        }
        return DxfEntity.Polyline(vertices, closed, layer, colorIndex) to i
    }

    /**
     * Parse a TEXT entity.
     * Group codes: 1=text, 10/20/30=insertion, 11/21/31=alignment, 40=height, 50=rotation, 72=justification
     */
    private fun parseText(lines: List<String>, startIndex: Int): Pair<DxfEntity.Text, Int> {
        var i = startIndex
        var x = 0.0; var y = 0.0; var z = 0.0
        var alignX: Double? = null; var alignY: Double? = null
        var text = ""
        var height = 1.0
        var rotation = 0.0
        var layer = ""
        var hJust = 0
        var colorIndex = 256

        while (i < lines.size - 1) {
            val code = lines[i].trim()
            if (code == "0") break
            val value = lines[i + 1].trim()
            when (code) {
                "1" -> text = value
                "8" -> layer = value
                "10" -> x = value.toDoubleOrNull() ?: 0.0
                "20" -> y = value.toDoubleOrNull() ?: 0.0
                "30" -> z = value.toDoubleOrNull() ?: 0.0
                "11" -> alignX = value.toDoubleOrNull()
                "21" -> alignY = value.toDoubleOrNull()
                "40" -> height = value.toDoubleOrNull() ?: 1.0
                "50" -> rotation = value.toDoubleOrNull() ?: 0.0
                "62" -> colorIndex = value.toIntOrNull() ?: 256
                "72" -> hJust = value.toIntOrNull() ?: 0
            }
            i += 2
        }
        // If aligned (justify != 0), use alignment point instead of insertion point
        val finalX = if (hJust != 0 && alignX != null) alignX else x
        val finalY = if (hJust != 0 && alignY != null) alignY else y

        return DxfEntity.Text(finalX, finalY, z, text, height, rotation, layer, hJust, colorIndex) to i
    }

    /**
     * Parse an MTEXT entity.
     * Group codes: 1=text, 3=continuation text, 10/20/30=insertion, 40=height, 50=rotation
     * MTEXT can have formatting codes like \P (newline), {\fArial|...} (font changes) — we strip them.
     */
    private fun parseMText(lines: List<String>, startIndex: Int): Pair<DxfEntity.Text, Int> {
        var i = startIndex
        var x = 0.0; var y = 0.0; var z = 0.0
        val textParts = mutableListOf<String>()
        var height = 1.0
        var rotation = 0.0
        var layer = ""
        var colorIndex = 256

        while (i < lines.size - 1) {
            val code = lines[i].trim()
            if (code == "0") break
            val value = lines[i + 1].trim()
            when (code) {
                "1" -> textParts.add(value)   // Primary text
                "3" -> textParts.add(value)   // Continuation text (before group 1)
                "8" -> layer = value
                "10" -> x = value.toDoubleOrNull() ?: 0.0
                "20" -> y = value.toDoubleOrNull() ?: 0.0
                "30" -> z = value.toDoubleOrNull() ?: 0.0
                "40" -> height = value.toDoubleOrNull() ?: 1.0
                "50" -> rotation = value.toDoubleOrNull() ?: 0.0
                "62" -> colorIndex = value.toIntOrNull() ?: 256
            }
            i += 2
        }

        // Combine and strip MTEXT formatting
        val rawText = textParts.joinToString("")
        val cleanText = stripMtextFormatting(rawText)

        return DxfEntity.Text(x, y, z, cleanText, height, rotation, layer, 0, colorIndex) to i
    }

    /**
     * Strip MTEXT formatting codes:
     * \P → newline (we use space), {\fArial|...;text} → text,
     * \H1.5; → height override, \\S → stacking, etc.
     */
    private fun stripMtextFormatting(raw: String): String {
        var result = raw
        // Replace \P with space (newline in MTEXT)
        result = result.replace("\\P", " ")
        result = result.replace("\\p", " ")
        // Remove font/style blocks like {\fArial|b0|i0|...;  and closing }
        result = result.replace(Regex("""\\f[^;]*;"""), "")
        // Remove height overrides like \H1.5;
        result = result.replace(Regex("""\\H[\d.]+;"""), "")
        // Remove width factor like \W0.8;
        result = result.replace(Regex("""\\W[\d.]+;"""), "")
        // Remove braces (from font groupings)
        result = result.replace("{", "").replace("}", "")
        // Remove other common escape sequences
        result = result.replace("\\~", " ")  // non-breaking space
        result = result.replace("%%d", "°")  // degree symbol
        result = result.replace("%%p", "±")  // plus-minus
        return result.trim()
    }
}
