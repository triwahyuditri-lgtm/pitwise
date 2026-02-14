package com.example.pitwise.domain.map

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset

/**
 * Matrix-based transformation engine for the map canvas.
 *
 * Uses Compose Snapshot State so that Canvas reads transform values
 * directly — only the Canvas redraws on pan/zoom, NOT the entire
 * composable tree. This is the key to Avenza-level performance.
 *
 * DXF coordinate system: Y increases upward (world), screen Y increases downward.
 * This engine flips Y for DXF maps. PDF uses top-left origin directly.
 */
class MapTransformEngine {

    // Compose snapshot state — Canvas reads these and auto-invalidates
    private val _scale = mutableFloatStateOf(1f)
    private val _offsetX = mutableFloatStateOf(0f)
    private val _offsetY = mutableFloatStateOf(0f)

    val scale: Float get() = _scale.floatValue
    val offsetX: Float get() = _offsetX.floatValue
    val offsetY: Float get() = _offsetY.floatValue

    /** Whether the map is DXF (Y flipped) or PDF (Y normal). */
    var flipY: Boolean = true

    /**
     * Convert world (map) coordinates to screen pixel coordinates.
     */
    fun worldToScreen(wx: Double, wy: Double): Offset {
        val sy = if (flipY) -wy else wy
        return Offset(
            x = (wx.toFloat() * scale) + offsetX,
            y = (sy.toFloat() * scale) + offsetY
        )
    }

    /**
     * Convert screen pixel coordinates to world (map) coordinates.
     */
    fun screenToWorld(sx: Float, sy: Float): Pair<Double, Double> {
        val wx = ((sx - offsetX) / scale).toDouble()
        val rawY = ((sy - offsetY) / scale).toDouble()
        val wy = if (flipY) -rawY else rawY
        return Pair(wx, wy)
    }

    /**
     * Apply a pan delta in screen pixels.
     */
    fun applyPan(dx: Float, dy: Float) {
        _offsetX.floatValue += dx
        _offsetY.floatValue += dy
    }

    /**
     * Apply zoom centered on a screen focus point.
     */
    fun applyZoom(factor: Float, focusX: Float, focusY: Float) {
        val newScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        if (newScale == scale) return // Avoid unnecessary offset recalc at limits
        val scaleChange = newScale / scale

        // Adjust offset so the focus point stays fixed
        _offsetX.floatValue = focusX - (focusX - offsetX) * scaleChange
        _offsetY.floatValue = focusY - (focusY - offsetY) * scaleChange

        _scale.floatValue = newScale
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
        _scale.floatValue = scale.coerceIn(MIN_SCALE, MAX_SCALE)
        _offsetX.floatValue = offsetX
        _offsetY.floatValue = offsetY
    }

    /**
     * Fit bounding box to canvas (Zoom All for DXF).
     */
    fun zoomAll(
        minX: Double, minY: Double,
        maxX: Double, maxY: Double,
        canvasW: Float, canvasH: Float
    ) {
        val worldW = (maxX - minX).toFloat()
        val worldH = (maxY - minY).toFloat()

        if (worldW <= 0f || worldH <= 0f || canvasW <= 0f || canvasH <= 0f) return

        val padding = ZOOM_ALL_PADDING
        val availW = canvasW - padding * 2
        val availH = canvasH - padding * 2

        _scale.floatValue = minOf(availW / worldW, availH / worldH).coerceIn(MIN_SCALE, MAX_SCALE)

        val centerWorldX = ((minX + maxX) / 2.0).toFloat()
        val centerWorldY = ((minY + maxY) / 2.0).toFloat()

        // Center the bounding box in the canvas
        _offsetX.floatValue = canvasW / 2f - centerWorldX * scale
        _offsetY.floatValue = if (flipY) {
            canvasH / 2f + centerWorldY * scale  // Y flipped
        } else {
            canvasH / 2f - centerWorldY * scale
        }
    }

    /**
     * Fit PDF bitmap to canvas (Zoom All for PDF).
     */
    fun zoomAllPdf(bitmapW: Int, bitmapH: Int, canvasW: Float, canvasH: Float) {
        if (bitmapW <= 0 || bitmapH <= 0 || canvasW <= 0f || canvasH <= 0f) return

        val padding = ZOOM_ALL_PADDING
        val availW = canvasW - padding * 2
        val availH = canvasH - padding * 2

        _scale.floatValue = minOf(availW / bitmapW, availH / bitmapH).coerceIn(MIN_SCALE, MAX_SCALE)
        _offsetX.floatValue = (canvasW - bitmapW * scale) / 2f
        _offsetY.floatValue = (canvasH - bitmapH * scale) / 2f
    }

    companion object {
        const val MIN_SCALE = 0.5f
        const val MAX_SCALE = 20f
        const val ZOOM_ALL_PADDING = 40f
        const val DOUBLE_TAP_ZOOM_FACTOR = 1.5f
    }
}
