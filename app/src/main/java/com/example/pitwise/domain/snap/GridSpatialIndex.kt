package com.example.pitwise.domain.snap

import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Spatial index based on a uniform grid partition.
 * Optimized for fast nearest-neighbor lookups for massive point sets.
 */
class GridSpatialIndex(
    private val cellSize: Double = 5.0 // 5m cells for mining-grade snap precision
) : SpatialIndex {

    // Map key: Long composed of (xBits << 32) | yBits
    // Value: List of vertices in that cell
    private val grid = mutableMapOf<Long, MutableList<DxfVertex>>()

    override fun build(vertices: List<DxfVertex>) {
        grid.clear()
        for (v in vertices) {
            val cellKey = getCellKey(v.x, v.y)
            grid.getOrPut(cellKey) { mutableListOf() }.add(v)
        }
    }

    override fun queryNearest(x: Double, y: Double, radius: Double): SnapResult? {
        val minCellX = floor((x - radius) / cellSize).toInt()
        val maxCellX = floor((x + radius) / cellSize).toInt()
        val minCellY = floor((y - radius) / cellSize).toInt()
        val maxCellY = floor((y + radius) / cellSize).toInt()

        var bestVertex: DxfVertex? = null
        var bestDistSq = radius * radius // Start with radius threshold squared

        // Iterate through all cells that intersect the query circle (usually 3x3)
        for (cx in minCellX..maxCellX) {
            for (cy in minCellY..maxCellY) {
                val cellKey = encodeKey(cx, cy)
                val pointsInCell = grid[cellKey] ?: continue

                for (point in pointsInCell) {
                    val dx = point.x - x
                    val dy = point.y - y
                    val dSq = dx * dx + dy * dy

                    if (dSq <= bestDistSq) {
                        bestDistSq = dSq
                        bestVertex = point
                    }
                }
            }
        }

        return if (bestVertex != null) {
            SnapResult(bestVertex, sqrt(bestDistSq))
        } else {
            null
        }
    }

    private fun getCellKey(x: Double, y: Double): Long {
        val cx = floor(x / cellSize).toInt()
        val cy = floor(y / cellSize).toInt()
        return encodeKey(cx, cy)
    }

    private fun encodeKey(x: Int, y: Int): Long {
        return (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)
    }
}
