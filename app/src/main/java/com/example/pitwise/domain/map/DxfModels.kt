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
 * DXF entity models — supported geometry and annotation types.
 *
 * Color handling:
 * - `colorIndex` = AutoCAD Color Index (ACI), group code 62
 * - 0 = BYBLOCK (inherit from parent block)
 * - 1–255 = explicit ACI color
 * - 256 = BYLAYER (inherit from layer definition, the default)
 */
sealed class DxfEntity {
    data class Point(
        val x: Double,
        val y: Double,
        val z: Double = 0.0,
        val layer: String = "",
        val colorIndex: Int = 256
    ) : DxfEntity()

    data class Line(
        val x1: Double,
        val y1: Double,
        val z1: Double = 0.0,
        val x2: Double,
        val y2: Double,
        val z2: Double = 0.0,
        val layer: String = "",
        val colorIndex: Int = 256
    ) : DxfEntity()

    data class Polyline(
        val vertices: List<Vertex>,
        val closed: Boolean = false,
        val layer: String = "",
        val colorIndex: Int = 256
    ) : DxfEntity()

    /**
     * TEXT and MTEXT entity — built-in labels/annotations in the DXF file.
     *
     * DXF group codes:
     * - 1: Primary text content
     * - 3: Additional text (MTEXT continuation)
     * - 10/20/30: Insertion point X/Y/Z
     * - 11/21/31: Alignment point (used when justified)
     * - 40: Text height (world units)
     * - 50: Rotation angle (degrees, CCW from X-axis)
     * - 72: Horizontal justification (0=left, 1=center, 2=right)
     */
    data class Text(
        val x: Double,
        val y: Double,
        val z: Double = 0.0,
        val text: String,
        val height: Double = 1.0,
        val rotation: Double = 0.0,  // degrees
        val layer: String = "",
        val horizontalJustification: Int = 0,  // 0=left, 1=center, 2=right
        val colorIndex: Int = 256
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
    val maxZ: Double,
    /** Layer name → ACI color index from TABLES section */
    val layerColors: Map<String, Int> = emptyMap()
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
