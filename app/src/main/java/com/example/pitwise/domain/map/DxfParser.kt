package com.example.pitwise.domain.map

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses DXF file text content into supported geometry entities.
 *
 * Supported: POINT, LINE, POLYLINE, LWPOLYLINE
 * Ignored: TEXT, HATCH, BLOCK, 3DFACE, and all others
 *
 * DXF format reference:
 * - Group code/value pairs, line by line
 * - ENTITIES section contains geometry
 * - Group codes: 0=entity type, 10/20/30=X/Y/Z coords,
 *   11/21/31=end coords for LINE, 70=flags, 90=vertex count
 *   38=elevation for LWPOLYLINE
 */
@Singleton
class DxfParser @Inject constructor() {

    fun parse(content: String): DxfFile {
        val lines = content.lines().map { it.trim() }
        val entities = mutableListOf<DxfEntity>()

        // Find ENTITIES section
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
            }
        }

        if (entities.isEmpty()) {
            minX = 0.0; minY = 0.0; minZ = 0.0
            maxX = 1.0; maxY = 1.0; maxZ = 0.0
        }

        return DxfFile(entities, minX, minY, minZ, maxX, maxY, maxZ)
    }

    private fun parsePoint(lines: List<String>, startIndex: Int): Pair<DxfEntity.Point, Int> {
        var i = startIndex
        var x = 0.0
        var y = 0.0
        var z = 0.0
        var layer = ""

        while (i < lines.size - 1) {
            val code = lines[i].trim()
            if (code == "0") break
            val value = lines[i + 1].trim()
            when (code) {
                "8" -> layer = value
                "10" -> x = value.toDoubleOrNull() ?: 0.0
                "20" -> y = value.toDoubleOrNull() ?: 0.0
                "30" -> z = value.toDoubleOrNull() ?: 0.0
            }
            i += 2
        }
        return DxfEntity.Point(x, y, z, layer) to i
    }

    private fun parseLine(lines: List<String>, startIndex: Int): Pair<DxfEntity.Line, Int> {
        var i = startIndex
        var x1 = 0.0; var y1 = 0.0; var z1 = 0.0
        var x2 = 0.0; var y2 = 0.0; var z2 = 0.0
        var layer = ""

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
            }
            i += 2
        }
        return DxfEntity.Line(x1, y1, z1, x2, y2, z2, layer) to i
    }

    private fun parseLwPolyline(lines: List<String>, startIndex: Int): Pair<DxfEntity.Polyline, Int> {
        var i = startIndex
        val vertices = mutableListOf<DxfEntity.Vertex>()
        var closed = false
        var layer = ""
        var currentX: Double? = null
        var currentZ: Double? = null
        var elevation = 0.0  // Group code 38: default elevation for LWPOLYLINE

        while (i < lines.size - 1) {
            val code = lines[i].trim()
            if (code == "0") break
            val value = lines[i + 1].trim()
            when (code) {
                "8" -> layer = value
                "38" -> elevation = value.toDoubleOrNull() ?: 0.0
                "70" -> closed = (value.toIntOrNull() ?: 0) and 1 == 1
                "10" -> {
                    // New vertex X — flush previous if it had a Y
                    currentX = value.toDoubleOrNull() ?: 0.0
                    currentZ = null  // reset per-vertex Z
                }
                "20" -> {
                    val y = value.toDoubleOrNull() ?: 0.0
                    val x = currentX ?: 0.0
                    val z = currentZ ?: elevation
                    vertices.add(DxfEntity.Vertex(x, y, z))
                    currentX = null
                    currentZ = null
                }
                "30" -> {
                    // Per-vertex Z override for LWPOLYLINE
                    currentZ = value.toDoubleOrNull() ?: 0.0
                }
            }
            i += 2
        }
        return DxfEntity.Polyline(vertices, closed, layer) to i
    }

    private fun parsePolyline(lines: List<String>, startIndex: Int): Pair<DxfEntity.Polyline, Int> {
        var i = startIndex
        val vertices = mutableListOf<DxfEntity.Vertex>()
        var closed = false
        var layer = ""
        var polylineZ = 0.0  // Elevation from POLYLINE header

        // Read flags from POLYLINE header
        while (i < lines.size - 1) {
            val code = lines[i].trim()
            if (code == "0") break
            val value = lines[i + 1].trim()
            when (code) {
                "8" -> layer = value
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
        return DxfEntity.Polyline(vertices, closed, layer) to i
    }
}
