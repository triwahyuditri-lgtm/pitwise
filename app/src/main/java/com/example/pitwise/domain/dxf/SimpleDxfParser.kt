package com.example.pitwise.domain.dxf

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal DXF parser. Supports only LINE, LWPOLYLINE, POLYLINE, POINT.
 * No color, no layers, no blocks, no inserts.
 */
@Singleton
class SimpleDxfParser @Inject constructor() {

    companion object {
        private const val TAG = "SimpleDxfParser"
    }

    fun parse(content: String): SimpleDxfModel {
        val rawLines = content.lines().map { it.trim() }

        val lines = mutableListOf<Pair<Vertex, Vertex>>()
        val polylines = mutableListOf<List<Vertex>>()
        val points = mutableListOf<Vertex>()

        var minX = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE

        fun updateBounds(x: Double, y: Double) {
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }

        // Find ENTITIES section
        var i = 0
        while (i + 1 < rawLines.size) {
            val code = rawLines[i].toIntOrNull()
            val value = rawLines[i + 1]
            if (code == 0 && value.equals("SECTION", ignoreCase = true)) {
                if (i + 3 < rawLines.size &&
                    rawLines[i + 2].trim() == "2" &&
                    rawLines[i + 3].equals("ENTITIES", ignoreCase = true)
                ) {
                    i += 4
                    break
                }
            }
            i += 2
        }

        // Parse entities
        while (i + 1 < rawLines.size) {
            val code = rawLines[i].toIntOrNull()
            val value = rawLines[i + 1]

            if (code == 0 && value.equals("ENDSEC", ignoreCase = true)) break

            if (code == 0 && value.equals("LINE", ignoreCase = true)) {
                i = parseLine(rawLines, i + 2, lines, ::updateBounds)
                continue
            }
            if (code == 0 && value.equals("LWPOLYLINE", ignoreCase = true)) {
                i = parseLwPolyline(rawLines, i + 2, polylines, ::updateBounds)
                continue
            }
            if (code == 0 && value.equals("POLYLINE", ignoreCase = true)) {
                i = parsePolyline(rawLines, i + 2, polylines, ::updateBounds)
                continue
            }
            if (code == 0 && value.equals("POINT", ignoreCase = true)) {
                i = parsePoint(rawLines, i + 2, points, ::updateBounds)
                continue
            }

            i += 2
        }

        // Fallback bounds if empty
        if (minX > maxX) {
            minX = 0.0; maxX = 1.0; minY = 0.0; maxY = 1.0
        }

        val model = SimpleDxfModel(
            lines = lines,
            polylines = polylines,
            points = points,
            bounds = DxfBounds(minX, maxX, minY, maxY)
        )

        Log.d(TAG, "Parsed: lines=${lines.size} polylines=${polylines.size} " +
                "points=${points.size} bounds=[$minX..$maxX, $minY..$maxY]")

        return model
    }

    // ── LINE ───────────────────────────────────────────

    private fun parseLine(
        lines: List<String>,
        startIndex: Int,
        result: MutableList<Pair<Vertex, Vertex>>,
        updateBounds: (Double, Double) -> Unit
    ): Int {
        var i = startIndex
        var x1 = 0.0; var y1 = 0.0; var z1: Double? = null
        var x2 = 0.0; var y2 = 0.0; var z2: Double? = null

        while (i + 1 < lines.size) {
            val code = lines[i].toIntOrNull() ?: break
            val value = lines[i + 1]

            // Stop at next entity or section boundary
            if (code == 0) break

            when (code) {
                10 -> x1 = value.toDxfDoubleOrNull() ?: 0.0
                20 -> y1 = value.toDxfDoubleOrNull() ?: 0.0
                30 -> z1 = value.toDxfDoubleOrNull()
                11 -> x2 = value.toDxfDoubleOrNull() ?: 0.0
                21 -> y2 = value.toDxfDoubleOrNull() ?: 0.0
                31 -> z2 = value.toDxfDoubleOrNull()
            }
            i += 2
        }

        updateBounds(x1, y1)
        updateBounds(x2, y2)
        result.add(Pair(Vertex(x1, y1, z1), Vertex(x2, y2, z2)))
        return i
    }

    // ── LWPOLYLINE ────────────────────────────────────

    private fun parseLwPolyline(
        lines: List<String>,
        startIndex: Int,
        result: MutableList<List<Vertex>>,
        updateBounds: (Double, Double) -> Unit
    ): Int {
        var i = startIndex
        val vertices = mutableListOf<Vertex>()
        var isClosed = false
        var elevation: Double? = null

        // Accumulate vertex data
        var currentX: Double? = null
        var currentY: Double? = null
        var currentZ: Double? = null

        fun finalizePendingVertex() {
            val x = currentX
            val y = currentY
            if (x != null && y != null) {
                val z = currentZ ?: elevation
                vertices.add(Vertex(x, y, z))
                updateBounds(x, y)
                currentX = null
                currentY = null
                currentZ = null
            }
        }

        while (i + 1 < lines.size) {
            val code = lines[i].toIntOrNull() ?: break
            val value = lines[i + 1]

            if (code == 0) break

            when (code) {
                70 -> {
                    val flags = value.toIntOrNull() ?: 0
                    isClosed = (flags and 1) == 1
                }
                38 -> elevation = value.toDxfDoubleOrNull()
                10 -> {
                    // New vertex starts with code 10
                    finalizePendingVertex()
                    currentX = value.toDxfDoubleOrNull() ?: 0.0
                }
                20 -> currentY = value.toDxfDoubleOrNull() ?: 0.0
                30 -> currentZ = value.toDxfDoubleOrNull()
            }
            i += 2
        }

        // Finalize last pending vertex
        finalizePendingVertex()

        if (isClosed && vertices.size > 1) {
            vertices.add(vertices.first())
        }

        if (vertices.isNotEmpty()) {
            result.add(vertices)
        }
        return i
    }

    // ── POLYLINE (heavy) ──────────────────────────────

    private fun parsePolyline(
        lines: List<String>,
        startIndex: Int,
        result: MutableList<List<Vertex>>,
        updateBounds: (Double, Double) -> Unit
    ): Int {
        var i = startIndex
        val vertices = mutableListOf<Vertex>()
        var isClosed = false
        var polylineElevation: Double? = null

        // Read polyline header until VERTEX or SEQEND
        while (i + 1 < lines.size) {
            val code = lines[i].toIntOrNull() ?: break
            val value = lines[i + 1]

            if (code == 0) break

            when (code) {
                70 -> {
                    val flags = value.toIntOrNull() ?: 0
                    isClosed = (flags and 1) == 1
                }
                30 -> polylineElevation = value.toDxfDoubleOrNull()
            }
            i += 2
        }

        // Now parse VERTEX entities until SEQEND
        while (i < lines.size) {
            if (lines[i].equals("SEQEND", ignoreCase = true)) {
                i++
                // Skip SEQEND codes
                while (i + 1 < lines.size) {
                    val code = lines[i].toIntOrNull() ?: break
                    if (code == 0) break
                    i += 2
                }
                break
            }

            if (lines[i].equals("VERTEX", ignoreCase = true)) {
                i++
                var vx = 0.0; var vy = 0.0; var vz: Double? = null

                while (i + 1 < lines.size) {
                    val code = lines[i].toIntOrNull() ?: break
                    val value = lines[i + 1]
                    if (code == 0) break

                    when (code) {
                        10 -> vx = value.toDxfDoubleOrNull() ?: 0.0
                        20 -> vy = value.toDxfDoubleOrNull() ?: 0.0
                        30 -> vz = value.toDxfDoubleOrNull()
                    }
                    i += 2
                }

                val finalZ = vz ?: polylineElevation
                vertices.add(Vertex(vx, vy, finalZ))
                updateBounds(vx, vy)
                continue
            }

            // Skip unknown entity types between POLYLINE and SEQEND
            if (i + 1 < lines.size) {
                val code = lines[i].toIntOrNull()
                if (code == 0) {
                    // It's some other entity type inside POLYLINE block, skip its name
                    i++
                    continue
                }
            }
            i++
        }

        if (isClosed && vertices.size > 1) {
            vertices.add(vertices.first())
        }

        if (vertices.isNotEmpty()) {
            result.add(vertices)
        }
        return i
    }

    // ── POINT ─────────────────────────────────────────

    private fun parsePoint(
        lines: List<String>,
        startIndex: Int,
        result: MutableList<Vertex>,
        updateBounds: (Double, Double) -> Unit
    ): Int {
        var i = startIndex
        var x = 0.0; var y = 0.0; var z: Double? = null

        while (i + 1 < lines.size) {
            val code = lines[i].toIntOrNull() ?: break
            val value = lines[i + 1]

            if (code == 0) break

            when (code) {
                10 -> x = value.toDxfDoubleOrNull() ?: 0.0
                20 -> y = value.toDxfDoubleOrNull() ?: 0.0
                30 -> z = value.toDxfDoubleOrNull()
            }
            i += 2
        }

        updateBounds(x, y)
        result.add(Vertex(x, y, z))
        return i
    }
}

private fun String.toDxfDoubleOrNull(): Double? =
    this.toDoubleOrNull() ?: this.replace(',', '.').toDoubleOrNull()
