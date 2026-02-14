package com.example.pitwise.domain.map

import org.json.JSONArray

// ════════════════════════════════════════════════════
// Map Mode System
// ════════════════════════════════════════════════════

/**
 * Primary interaction modes for the map canvas.
 * Only one mode can be active at a time.
 */
enum class MapMode {
    VIEW,
    PLOT,
    MEASURE,
    ID_POINT
}

/**
 * Sub-modes for MEASURE mode.
 */
enum class MeasureSubMode {
    DISTANCE,
    AREA
}

// Keep legacy DrawingMode for backward compatibility with existing annotations
enum class DrawingMode { NONE, MARKER, LINE, POLYGON }

// ════════════════════════════════════════════════════
// Geometry Models
// ════════════════════════════════════════════════════

/**
 * 2D point for annotation JSON serialization (backward compatible).
 */
data class MapPoint(val x: Double, val y: Double)

/**
 * Serialization utilities for annotation points JSON.
 */
object MapSerializationUtils {
    fun pointsToJson(points: List<MapPoint>): String {
        return JSONArray().apply {
            points.forEach { pt ->
                put(JSONArray().apply { put(pt.x); put(pt.y) })
            }
        }.toString()
    }

    fun parseJsonToPoints(json: String): List<MapPoint> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val pt = arr.getJSONArray(i)
                MapPoint(pt.getDouble(0), pt.getDouble(1))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
