package com.example.pitwise.domain.transform

import android.graphics.PointF

/**
 * Handles the inverse transformation pipeline: Screen -> World (Projected).
 *
 * Pipeline:
 * 1. Screen (Pixels) -> Remove Pan/Zoom -> PDF Page Pixel
 * 2. PDF Page Pixel -> Inverse Affine -> World Projected (UTM)
 */
class ScreenToWorldTransform(
    private val inverseAffineMatrix: AffineMatrix
) {
    /**
     * Convert screen tap (x, y) to projected world coordinate.
     *
     * @param screenX Touch X in view pixels
     * @param screenY Touch Y in view pixels
     * @param scale Current view scale
     * @param offsetX Current view pan X
     * @param offsetY Current view pan Y
     * @param flipY Whether Y axis is inverted (e.g. standard PDF vs Cartesian)
     *              For GeoPDF, usually flipY=false if origin is top-left, true if bottom-left.
     *              The Affine Transform is built based on Page Coordinates (Top-Left Origin usually).
     *
     * @return Pair(easting, northing)
     */
    fun transform(
        screenX: Float,
        screenY: Float,
        scale: Float,
        offsetX: Float,
        offsetY: Float
    ): Pair<Double, Double> {
        // 1. Remove Zoom & Pan
        // screen = (pixel * scale) + offset
        // pixel = (screen - offset) / scale
        val pixelX = (screenX - offsetX) / scale
        val pixelY = (screenY - offsetY) / scale

        // 2. Apply Inverse Affine Transform
        // We assume pixelX/pixelY matches the coordinate space used to build the affine transform.
        // For PDF, this is typically Page Point (User Space), origin top-left.
        
        return inverseAffineMatrix.map(pixelX.toDouble(), pixelY.toDouble())
    }
}
