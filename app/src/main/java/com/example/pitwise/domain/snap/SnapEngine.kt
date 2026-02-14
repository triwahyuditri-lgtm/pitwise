package com.example.pitwise.domain.snap

/**
 * Snap Engine responsible for finding the nearest vertex within a tolerance.
 */
class SnapEngine(
    private val spatialIndex: SpatialIndex
) {

    /**
     * Update the spatial index with new vertices.
     * This rebuilds the index.
     */
    fun updateVertices(vertices: List<DxfVertex>) {
        spatialIndex.build(vertices)
    }

    /**
     * Find the nearest vertex to [worldX, worldY] within [snapThresholdMeters].
     */
    fun findVertex(worldX: Double, worldY: Double, radius: Double): SnapResult? {
        return spatialIndex.queryNearest(worldX, worldY, radius)
    }
}
