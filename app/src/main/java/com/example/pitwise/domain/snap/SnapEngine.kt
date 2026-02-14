package com.example.pitwise.domain.snap

/**
 * Snap Engine responsible for finding the nearest vertex within a tolerance.
 */
class SnapEngine(
    private val spatialIndex: SpatialIndex,
    private val snapThresholdMeters: Double = 2.0 // Configurable threshold
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
    fun findVertex(worldX: Double, worldY: Double): SnapResult? {
        return spatialIndex.queryNearest(worldX, worldY, snapThresholdMeters)
    }
}
