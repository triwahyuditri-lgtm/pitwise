package com.example.pitwise.domain.map

import android.util.Log
import androidx.compose.ui.geometry.Offset

/**
 * Matrix-based transformation engine for the map canvas.
 *
 * Manages scale, offsetX, offsetY for world↔screen coordinate mapping.
 * Pure state holder — no Android dependencies except Compose Offset.
 *
 * DXF coordinate system: Y increases upward (world), screen Y increases downward.
 * This engine flips Y for DXF maps. PDF uses top-left origin directly.
 */
class MapTransformEngine {

    var scale: Float = 1f
        private set
    var offsetX: Float = 0f
        private set
    var offsetY: Float = 0f
        private set

    /** Whether the map is DXF (Y flipped) or PDF (Y normal). */
    var flipY: Boolean = true

    /**
     * Convert world (map) coordinates to screen pixel coordinates.
     */
    /**
     * Convert world (map) coordinates to screen pixel coordinates.
     */
    fun worldToScreen(wx: Double, wy: Double): Offset {
        val sy = if (flipY) -wy else wy
        // Use Double math
        val sx = (wx * scale.toDouble()) + offsetX.toDouble()
        val screenY = (sy * scale.toDouble()) + offsetY.toDouble()
        return Offset(sx.toFloat(), screenY.toFloat())
    }

    /**
     * Convert screen pixel coordinates to world (map) coordinates.
     */
    fun screenToWorld(sx: Float, sy: Float): Pair<Double, Double> {
        // wx = (sx - offsetX) / scale
        val wx = (sx.toDouble() - offsetX.toDouble()) / scale.toDouble()
        val rawY = (sy.toDouble() - offsetY.toDouble()) / scale.toDouble()
        val wy = if (flipY) -rawY else rawY
        return Pair(wx, wy)
    }

    /**
     * Apply a pan delta in screen pixels.
     */
    fun applyPan(dx: Float, dy: Float) {
        offsetX += dx
        offsetY += dy
    }

    /**
     * Apply zoom centered on a screen focus point.
     */
    fun applyZoom(factor: Float, focusX: Float, focusY: Float) {
        val newScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        if (newScale == scale) return // Avoid unnecessary offset recalc at limits
        val scaleChange = newScale / scale

        // Adjust offset so the focus point stays fixed
        offsetX = focusX - (focusX - offsetX) * scaleChange
        offsetY = focusY - (focusY - offsetY) * scaleChange

        scale = newScale
    }

    /**
     * Double-tap zoom: 1.5× centered on the tap point.
     */
    fun applyDoubleTapZoom(focusX: Float, focusY: Float) {
        applyZoom(DOUBLE_TAP_ZOOM_FACTOR, focusX, focusY)
    }

    /**
     * Two-finger tap zoom out: inverse of double-tap.
     */
    fun applyZoomOut(focusX: Float, focusY: Float) {
        applyZoom(1f / DOUBLE_TAP_ZOOM_FACTOR, focusX, focusY)
    }

    /**
     * Set transform state directly (for restoring state).
     */
    fun setState(scale: Float, offsetX: Float, offsetY: Float) {
        this.scale = scale.coerceIn(MIN_SCALE, MAX_SCALE)
        this.offsetX = offsetX
        this.offsetY = offsetY
    }

    /**
     * Fit bounding box to canvas (Zoom All for DXF).
     *
     * @param minX minimum X in world coordinates
     * @param minY minimum Y in world coordinates
     * @param maxX maximum X in world coordinates
     * @param maxY maximum Y in world coordinates
     * @param canvasW canvas width in pixels
     * @param canvasH canvas height in pixels
     */
    fun zoomAll(
        minX: Double, minY: Double,
        maxX: Double, maxY: Double,
        canvasW: Float, canvasH: Float
    ) {
        val worldW = maxX - minX
        val worldH = maxY - minY
        
        if (worldW <= 0.0 || worldH <= 0.0 || canvasW <= 0f || canvasH <= 0f) {
            Log.w(TAG, "zoomAll: invalid dimensions worldW=$worldW worldH=$worldH canvasW=$canvasW canvasH=$canvasH")
            return
        }

        val padding = ZOOM_ALL_PADDING
        val availW = canvasW - padding * 2
        val availH = canvasH - padding * 2

        // Use Double for scale calculation to handle very large extents vs small screens
        val scaleD = minOf(availW.toDouble() / worldW, availH.toDouble() / worldH)
            .coerceIn(MIN_SCALE.toDouble(), MAX_SCALE.toDouble())
            
        scale = scaleD.toFloat()

        val centerWorldX = (minX + maxX) / 2.0
        val centerWorldY = (minY + maxY) / 2.0

        // Center the bounding box in the canvas
        // Calculate offsets using Double precision first before casting to Float
        val offsetXDouble = canvasW.toDouble() / 2.0 - centerWorldX * scaleD
        val offsetYDouble = if (flipY) {
            canvasH.toDouble() / 2.0 + centerWorldY * scaleD  // Y flipped
        } else {
            canvasH.toDouble() / 2.0 - centerWorldY * scaleD
        }
        
        offsetX = offsetXDouble.toFloat()
        offsetY = offsetYDouble.toFloat()

        Log.d(TAG, "zoomAll: scale=$scale offsetX=$offsetX offsetY=$offsetY " +
            "bounds=[$minX..$maxX, $minY..$maxY] canvas=${canvasW}x${canvasH}")
    }

    /**
     * Fit PDF bitmap to canvas (Zoom All for PDF).
     */
    fun zoomAllPdf(bitmapW: Int, bitmapH: Int, canvasW: Float, canvasH: Float) {
        if (bitmapW <= 0 || bitmapH <= 0 || canvasW <= 0f || canvasH <= 0f) return

        val padding = ZOOM_ALL_PADDING
        val availW = canvasW - padding * 2
        val availH = canvasH - padding * 2

        scale = minOf(availW / bitmapW, availH / bitmapH).coerceIn(MIN_SCALE, MAX_SCALE)
        offsetX = (canvasW - bitmapW * scale) / 2f
        offsetY = (canvasH - bitmapH * scale) / 2f
    }

    companion object {
        private const val TAG = "DXF_TRANSFORM"
        const val MIN_SCALE = 1e-12f
        const val MAX_SCALE = 20f
        const val ZOOM_ALL_PADDING = 40f
        const val DOUBLE_TAP_ZOOM_FACTOR = 1.5f
    }
}
