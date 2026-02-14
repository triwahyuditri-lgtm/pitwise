package com.example.pitwise.ui.screen.map

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import android.util.Log
import com.example.pitwise.data.local.entity.MapAnnotation
import com.example.pitwise.domain.dxf.SimpleDxfModel
import com.example.pitwise.domain.map.MapMode
import com.example.pitwise.domain.map.MapSerializationUtils
import com.example.pitwise.domain.map.MapTransformEngine
import com.example.pitwise.domain.map.MapVertex
import com.example.pitwise.domain.map.MeasureSubMode
import kotlin.math.abs

/**
 * High-performance map canvas renderer.
 *
 * Architecture:
 * 1. WORLD SPACE (Projected/Local): PDF & DXF geometry.
 * 2. TRANSFORM LAYER: Applies scale & offset.
 * 3. SCREEN SPACE (UI): GPS, Annotations, Active Drawing, ID Point.
 * 
 * Strict separation ensures GPS overlays do not drift during zoom/pan.
 */
@Composable
fun MapRenderer(
    modifier: Modifier = Modifier,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    pdfBitmap: Bitmap?,
    dxfModel: SimpleDxfModel?,
    showPdfLayer: Boolean,
    showDxfLayer: Boolean,
    showGpsLayer: Boolean,
    annotations: List<MapAnnotation>,
    collectedPoints: List<MapVertex>,
    currentMode: MapMode,
    measureSubMode: MeasureSubMode,
    idPointMarker: MapVertex?,
    gpsPosition: Pair<Double, Double>?,
    gpsHeading: Float = 0f,
    gpsAccuracy: Float? = null,
    flipY: Boolean = true,
    isSnapped: Boolean = false
) {
    // ── Cached Paint objects (zero allocation per frame) ──
    val zLabelPaint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 24f
            isAntiAlias = true
        }
    }

    // ── One-time render validation flag ──
    var renderValidated = remember { mutableSetOf<String>() }

    Canvas(modifier = modifier.fillMaxSize()) {
        // ════════════════════════════════════════════════════
        // TRANSFORM HELPER (for Screen-Space overlays)
        // ════════════════════════════════════════════════════
        
        fun worldToScreen(wx: Double, wy: Double): Offset {
             val visualY = if (flipY) -wy else wy
             val sx = (wx * scale.toDouble()) + offsetX.toDouble()
             val sy = (visualY * scale.toDouble()) + offsetY.toDouble()
             return Offset(sx.toFloat(), sy.toFloat())
        }

        // ════════════════════════════════════════════════════
        // LAYER 1: WORLD SPACE (PDF + DXF share ONE transform)
        // ════════════════════════════════════════════════════

        withTransform({
            translate(offsetX, offsetY)
            scale(scale, scale)
        }) {
            // ── A. PDF Layer ──
            if (showPdfLayer && pdfBitmap != null) {
                drawImage(pdfBitmap.asImageBitmap())
            }

            // ── B. DXF Layer (same transform as PDF) ──
            if (showDxfLayer && dxfModel != null) {
                drawDxfDirect(dxfModel, scale, flipY)
            }
        }

        // ════════════════════════════════════════════════════
        // LAYER 3: SCREEN SPACE RENDERING (UI Overlays)
        // ════════════════════════════════════════════════════
        
        // ── Annotations ──
        if (annotations.isNotEmpty()) {
            drawAnnotations(annotations, ::worldToScreen)
        }

        // ── Active Drawing (Collected Points) ──
        if (collectedPoints.isNotEmpty()) {
            drawActiveDrawing(collectedPoints, currentMode, measureSubMode, zLabelPaint, ::worldToScreen)
        }

        // ── GPS Layer ──
        if (showGpsLayer && gpsPosition != null) {
            drawGpsLayer(gpsPosition, gpsHeading, gpsAccuracy, scale, offsetX, offsetY, flipY, renderValidated, ::worldToScreen)
        }

        // ── ID Point / Snap Marker (DWG arrow style) ──
        if (idPointMarker != null) {
            drawSnapArrowMarker(idPointMarker, isSnapped, ::worldToScreen)
        }

        // ════════════════════════════════════════════════════
        // DEBUG OVERLAY
        // ════════════════════════════════════════════════════
        if (com.example.pitwise.domain.debug.DebugManager.shouldShowDebugOverlay()) {
            drawIntoCanvas { canvas ->
            val debugPaint = Paint().apply {
                color = android.graphics.Color.MAGENTA
                textSize = 30f
                isAntiAlias = true
            }
            
            val info = StringBuilder()
            info.append("Scale: %.5f\n".format(scale))
            info.append("Offset: %.1f, %.1f\n".format(offsetX, offsetY))
            
            if (dxfModel != null) {
                info.append("DXF: L=${dxfModel.lines.size} P=${dxfModel.polylines.size} Pt=${dxfModel.points.size}\n")
                info.append("Bounds: [X: %.1f..%.1f, Y: %.1f..%.1f]\n".format(
                    dxfModel.bounds.minX, dxfModel.bounds.maxX,
                    dxfModel.bounds.minY, dxfModel.bounds.maxY
                ))
            } else {
                info.append("DXF Model: NULL")
            }

            var y = 100f
            info.lines().forEach { line ->
                canvas.nativeCanvas.drawText(line, 20f, y, debugPaint)
                y += 40f
            }
        }
    }
}
}

// ════════════════════════════════════════════════════
// DXF — Direct World-Space Renderer (no center hack)
// ════════════════════════════════════════════════════

/** Neon green — single color for all DXF geometry */
private val DXF_RENDER_COLOR = Color(0xFF00FF00)

/**
 * Draws DXF geometry directly in world space.
 * Called INSIDE the same withTransform block as PDF.
 * No center-relative hack — coordinates are absolute world coords.
 */
private fun DrawScope.drawDxfDirect(
    dxf: SimpleDxfModel,
    scale: Float,
    flipY: Boolean
) {
    val ySign = if (flipY) -1f else 1f

    val safeScale = abs(scale).coerceAtLeast(1e-12f)
    val strokeWidth = (2f / safeScale).coerceAtLeast(0.5f)

    // Draw lines
    dxf.lines.forEach { (start, end) ->
        drawLine(
            color = DXF_RENDER_COLOR,
            start = Offset(start.x.toFloat(), start.y.toFloat() * ySign),
            end = Offset(end.x.toFloat(), end.y.toFloat() * ySign),
            strokeWidth = strokeWidth
        )
    }

    // Draw polylines
    dxf.polylines.forEach { vertices ->
        if (vertices.size > 1) {
            val path = Path()
            val first = vertices.first()
            path.moveTo(first.x.toFloat(), first.y.toFloat() * ySign)
            for (i in 1 until vertices.size) {
                val v = vertices[i]
                path.lineTo(v.x.toFloat(), v.y.toFloat() * ySign)
            }
            drawPath(path, color = DXF_RENDER_COLOR, style = Stroke(strokeWidth))
        }
    }

    // Draw points
    val pointRadius = (3f / safeScale).coerceAtLeast(1f)
    dxf.points.forEach { p ->
        drawCircle(
            color = DXF_RENDER_COLOR,
            radius = pointRadius,
            center = Offset(p.x.toFloat(), p.y.toFloat() * ySign)
        )
    }
}

// ════════════════════════════════════════════════════
// Snap Arrow Marker — DWG Style (Screen Space)
// ════════════════════════════════════════════════════

private fun DrawScope.drawSnapArrowMarker(
    marker: MapVertex,
    isSnapped: Boolean,
    worldToScreen: (Double, Double) -> Offset
) {
    val s = worldToScreen(marker.x, marker.y)

    // Yellow if snapped, Orange if free
    val color = if (isSnapped) Color(0xFFFFEB3B) else Color(0xFFFF5722)

    // 1. Small circle at snap point
    drawCircle(color = color.copy(alpha = 0.3f), radius = 18f, center = s)
    drawCircle(color = color, radius = 6f, center = s)
    drawCircle(color = Color.White, radius = 2f, center = s)

    // 2. Arrow triangle pointing upward above the point
    val arrowPath = Path().apply {
        moveTo(s.x, s.y - 35f)          // Tip (top)
        lineTo(s.x - 10f, s.y - 18f)    // Bottom-left
        lineTo(s.x + 10f, s.y - 18f)    // Bottom-right
        close()
    }
    drawPath(arrowPath, color = color)

    // 3. Stem line from circle to arrow
    drawLine(
        color = color,
        start = Offset(s.x, s.y - 6f),
        end = Offset(s.x, s.y - 18f),
        strokeWidth = 2.5f
    )
}

// ════════════════════════════════════════════════════
// Annotations Layer (Screen Space)
// ════════════════════════════════════════════════════

private fun DrawScope.drawAnnotations(annotations: List<MapAnnotation>, worldToScreen: (Double, Double) -> Offset) {
    val annotationColor = Color(0xFF00E5FF)
    val markerColor = Color(0xFFFF5722)

    for (ann in annotations) {
        val pts = MapSerializationUtils.parseJsonToPoints(ann.pointsJson)
        when (ann.type) {
            "MARKER" -> {
                if (pts.isNotEmpty()) {
                    val s = worldToScreen(pts[0].x, pts[0].y)
                    drawCircle(color = markerColor, radius = 10f, center = s)
                    drawCircle(color = Color.White, radius = 4f, center = s)
                }
            }
            "LINE" -> {
                for (i in 0 until pts.size - 1) {
                    val s1 = worldToScreen(pts[i].x, pts[i].y)
                    val s2 = worldToScreen(pts[i + 1].x, pts[i + 1].y)
                    drawLine(color = annotationColor, start = s1, end = s2, strokeWidth = 3f)
                }
                pts.forEach { pt ->
                    val s = worldToScreen(pt.x, pt.y)
                    drawCircle(color = annotationColor, radius = 4f, center = s)
                }
            }
            "POLYGON" -> {
                if (pts.isNotEmpty()) {
                    for (i in 0 until pts.size - 1) {
                        val s1 = worldToScreen(pts[i].x, pts[i].y)
                        val s2 = worldToScreen(pts[i + 1].x, pts[i + 1].y)
                        drawLine(color = annotationColor, start = s1, end = s2, strokeWidth = 3f)
                    }
                    val sLast = worldToScreen(pts.last().x, pts.last().y)
                    val sFirst = worldToScreen(pts.first().x, pts.first().y)
                    drawLine(color = annotationColor, start = sLast, end = sFirst, strokeWidth = 3f)
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════
// Active Drawing Layer (Screen Space)
// ════════════════════════════════════════════════════

private fun DrawScope.drawActiveDrawing(
    collectedPoints: List<MapVertex>,
    currentMode: MapMode,
    measureSubMode: MeasureSubMode,
    zLabelPaint: Paint,
    worldToScreen: (Double, Double) -> Offset
) {
    val activeColor = when (currentMode) {
        MapMode.PLOT -> Color(0xFFFF9800)
        MapMode.MEASURE -> Color(0xFFFFEB3B)
        else -> Color(0xFF00E5FF)
    }

    // Lines between points
    if (collectedPoints.size >= 2) {
        for (i in 0 until collectedPoints.size - 1) {
            val s1 = worldToScreen(collectedPoints[i].x, collectedPoints[i].y)
            val s2 = worldToScreen(collectedPoints[i + 1].x, collectedPoints[i + 1].y)
            drawLine(color = activeColor, start = s1, end = s2, strokeWidth = 4f)
        }
    }

    // Vertex dots
    collectedPoints.forEach { pt ->
        val s = worldToScreen(pt.x, pt.y)
        drawCircle(color = activeColor, radius = 6f, center = s)
        drawCircle(color = Color.White, radius = 2f, center = s)
    }

    // Z labels
    collectedPoints.forEach { pt ->
        if (pt.z != null) {
            val s = worldToScreen(pt.x, pt.y)
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    "Z:${"%.1f".format(pt.z)}",
                    s.x + 10f,
                    s.y - 10f,
                    zLabelPaint
                )
            }
        }
    }

    // Closing dashed line for polygon modes
    val isAreaMode = (currentMode == MapMode.PLOT) ||
            (currentMode == MapMode.MEASURE && measureSubMode == MeasureSubMode.AREA)
    if (isAreaMode && collectedPoints.size >= 3) {
        val sLast = worldToScreen(collectedPoints.last().x, collectedPoints.last().y)
        val sFirst = worldToScreen(collectedPoints.first().x, collectedPoints.first().y)
        drawLine(
            color = activeColor.copy(alpha = 0.5f),
            start = sLast,
            end = sFirst,
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        )
    }
}

// ════════════════════════════════════════════════════
// GPS Layer — SCREEN coordinates (fixed size, never scales with zoom)
// ════════════════════════════════════════════════════

private fun DrawScope.drawGpsLayer(
    gpsPosition: Pair<Double, Double>,
    heading: Float,
    accuracy: Float?,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    flipY: Boolean,
    validated: MutableSet<String>,
    worldToScreen: (Double, Double) -> Offset
) {
    val (gx, gy) = gpsPosition
    val screenPos = worldToScreen(gx, gy)
    
    // One-time GPS transform validation
    if ("gps" !in validated) {
        validated.add("gps")
        Log.d("GPS_VALIDATE", "world=($gx, $gy) → screen=(${screenPos.x}, ${screenPos.y}) " +
            "scale=$scale offsetX=$offsetX offsetY=$offsetY flipY=$flipY")
    }

    // Accuracy circle (scales with map zoom — it represents real meters)
    if (accuracy != null && accuracy > 0f) {
        val radiusPx = accuracy * scale
        drawCircle(
            color = Color(0xFF2979FF).copy(alpha = 0.10f),
            radius = radiusPx,
            center = screenPos
        )
        drawCircle(
            color = Color(0xFF2979FF).copy(alpha = 0.25f),
            radius = radiusPx,
            center = screenPos,
            style = Stroke(width = 1.5f)
        )
    }

    // GPS dot — FIXED screen size regardless of zoom
    drawCircle(color = Color(0xFF2979FF).copy(alpha = 0.3f), radius = 20f, center = screenPos)
    drawCircle(color = Color(0xFF2979FF), radius = 8f, center = screenPos)
    drawCircle(color = Color.White, radius = 3f, center = screenPos)
    
    // Heading Arrow
    val arrowPath = Path().apply {
         moveTo(0f, -40f)
         lineTo(12f, 0f)
         lineTo(0f, -10f)
         lineTo(-12f, 0f)
         close()
    }
    
    withTransform({
        rotate(heading, pivot = screenPos)
        translate(screenPos.x, screenPos.y)
    }) {
        drawPath(arrowPath, color = Color(0xFF2979FF))
    }
}
