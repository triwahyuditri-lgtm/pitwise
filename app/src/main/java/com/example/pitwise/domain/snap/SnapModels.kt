package com.example.pitwise.domain.snap

/**
 * Represents a vertex from a DXF file.
 * We use a dedicated class for the Snap engine to keep it decoupled.
 */
data class DxfVertex(
    val x: Double,
    val y: Double,
    val z: Double? = null
)

/**
 * Result of a snap operation.
 */
data class SnapResult(
    val vertex: DxfVertex,
    val distance: Double
)

/**
 * Interface for a spatial index that can store points and query them efficiently.
 */
interface SpatialIndex {
    /**
     * Build the index from a list of vertices.
     * This should be called once. Re-building might overwrite previous data.
     */
    fun build(vertices: List<DxfVertex>)

    /**
     * Find the nearest vertex to [x, y] within [radius].
     * Returns null if no vertex is found within the radius.
     */
    fun queryNearest(x: Double, y: Double, radius: Double): SnapResult?
}
