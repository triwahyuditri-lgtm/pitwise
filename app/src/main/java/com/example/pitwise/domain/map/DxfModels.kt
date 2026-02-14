package com.example.pitwise.domain.map

/**
 * Standalone vertex used for coordinate results, tap info, etc.
 */
data class MapVertex(
    val x: Double,
    val y: Double,
    val z: Double?
)

/**
 * DXF entity models — only the geometry types we support.
 * TEXT, HATCH, BLOCK, 3DFACE are intentionally excluded.
 */
sealed class DxfEntity {
    data class Point(
        val x: Double,
        val y: Double,
        val z: Double = 0.0,
        val layer: String = ""
    ) : DxfEntity()

    data class Line(
        val x1: Double,
        val y1: Double,
        val z1: Double = 0.0,
        val x2: Double,
        val y2: Double,
        val z2: Double = 0.0,
        val layer: String = ""
    ) : DxfEntity()

    data class Polyline(
        val vertices: List<Vertex>,
        val closed: Boolean = false,
        val layer: String = ""
    ) : DxfEntity()

    data class Vertex(
        val x: Double,
        val y: Double,
        val z: Double = 0.0
    )
}

/**
 * Parsed DXF file result.
 */
data class DxfFile(
    val entities: List<DxfEntity>,
    val minX: Double,
    val minY: Double,
    val minZ: Double,
    val maxX: Double,
    val maxY: Double,
    val maxZ: Double
)

/**
 * Type of snap hit on a DXF entity.
 */
enum class SnapType {
    VERTEX,   // Snapped to an exact vertex
    SEGMENT   // Snapped to the nearest point on a line segment
}

/**
 * Result from the DXF snap engine.
 */
data class SnapResult(
    val worldX: Double,
    val worldY: Double,
    val z: Double?,
    val type: SnapType,
    val layer: String? = null
)
