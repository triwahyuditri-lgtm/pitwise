package com.example.pitwise.domain.transform

import android.graphics.PointF

/**
 * Handles the forward transformation pipeline: World (Projected) -> Screen.
 *
 * Pipeline:
 * 1. World Projected (UTM) -> Affine Transform -> PDF Page Pixel
 * 2. PDF Page Pixel -> Apply Pan/Zoom -> Screen (Pixels)
 */
class WorldToScreenTransform(
    private val affineMatrix: AffineMatrix
) {
    /**
     * Convert projected world coordinate to screen pixel.
     *
     * @param worldX Easting
     * @param worldY Northing
     * @param scale Current view scale
     * @param offsetX Current view pan X
     * @param offsetY Current view pan Y
     *
     * @return Pair(screenX, screenY)
     */
    fun transform(
        worldX: Double,
        worldY: Double,
        scale: Float,
        offsetX: Float,
        offsetY: Float
    ): Pair<Float, Float> {
        // 1. Apply Affine Transform
        val (pixelX, pixelY) = affineMatrix.map(worldX, worldY)

        // 2. Apply Zoom & Pan
        val screenX = (pixelX.toFloat() * scale) + offsetX
        val screenY = (pixelY.toFloat() * scale) + offsetY

        return Pair(screenX, screenY)
    }
}
