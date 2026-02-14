package com.example.pitwise.domain.dxf

/**
 * Simple vertex with optional Z.
 */
data class Vertex(
    val x: Double,
    val y: Double,
    val z: Double? = null
)

/**
 * Bounding box for DXF geometry.
 */
data class DxfBounds(
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double
)

/**
 * Minimal DXF model — lines, polylines, points + bounding box.
 * No color, no layers, no blocks.
 */
data class SimpleDxfModel(
    val lines: List<Pair<Vertex, Vertex>> = emptyList(),
    val polylines: List<List<Vertex>> = emptyList(),
    val points: List<Vertex> = emptyList(),
    val bounds: DxfBounds
)
