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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import com.example.pitwise.data.local.entity.MapAnnotation
import com.example.pitwise.domain.map.DxfEntity
import com.example.pitwise.domain.map.DxfFile
import com.example.pitwise.domain.map.MapMode
import com.example.pitwise.domain.map.MapSerializationUtils
import com.example.pitwise.domain.map.MapTransformEngine
import com.example.pitwise.domain.map.MapVertex
import com.example.pitwise.domain.map.MeasureSubMode

/**
 * High-performance map canvas renderer.
 *
 * Draws all layers: PDF → DXF → Annotations → Active drawing → GPS → ID Point.
 * Uses MapTransformEngine for coordinate mapping.
 *
 * Performance rules:
 * - Paint objects cached via remember (zero allocation per frame)
 * - PDF rendered with withTransform (single matrix, no per-pixel work)
 * - GPS dot drawn in screen coordinates (fixed screen size)
 */
@Composable
fun MapRenderer(
    modifier: Modifier = Modifier,
    transformEngine: MapTransformEngine,
    pdfBitmap: Bitmap?,
    dxfFile: DxfFile?,
    showPdfLayer: Boolean,
    showDxfLayer: Boolean,
    showGpsLayer: Boolean,
    annotations: List<MapAnnotation>,
    collectedPoints: List<MapVertex>,
    currentMode: MapMode,
    measureSubMode: MeasureSubMode,
    idPointMarker: MapVertex?,
    gpsPosition: Pair<Double, Double>?,
    gpsAccuracy: Float? = null
) {
    // ── Cached Paint objects (zero allocation per frame) ──
    val zLabelPaint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 24f
            isAntiAlias = true
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val engine = transformEngine

        // ══ PDF Layer (withTransform — single matrix) ══
        if (showPdfLayer && pdfBitmap != null) {
            drawIntoCanvas { canvas ->
                canvas.save()
                canvas.translate(engine.offsetX, engine.offsetY)
                canvas.scale(engine.scale, engine.scale)
                canvas.nativeCanvas.drawBitmap(pdfBitmap, 0f, 0f, null)
                canvas.restore()
            }
        }

        // ══ DXF Layer ══
        if (showDxfLayer && dxfFile != null) {
            drawDxfLayer(engine, dxfFile)
        }

        // ══ Persisted Annotations ══
        drawAnnotations(engine, annotations)

        // ══ Active Drawing (Collected Points) ══
        if (collectedPoints.isNotEmpty()) {
            drawActiveDrawing(engine, collectedPoints, currentMode, measureSubMode, zLabelPaint)
        }

        // ══ GPS Layer — drawn in SCREEN coordinates (fixed size) ══
        if (showGpsLayer && gpsPosition != null) {
            drawGpsLayer(engine, gpsPosition, gpsAccuracy)
        }

        // ══ ID Point Marker ══
        if (idPointMarker != null) {
            drawIdPointMarker(engine, idPointMarker)
        }
    }
}

// ════════════════════════════════════════════════════
// DXF Layer
// ════════════════════════════════════════════════════

private fun DrawScope.drawDxfLayer(engine: MapTransformEngine, dxf: DxfFile) {
    val dxfColor = Color(0xFF00E5FF)

    for (entity in dxf.entities) {
        when (entity) {
            is DxfEntity.Point -> {
                val s = engine.worldToScreen(entity.x, entity.y)
                drawCircle(color = dxfColor, radius = 3f, center = s)
            }
            is DxfEntity.Line -> {
                val s1 = engine.worldToScreen(entity.x1, entity.y1)
                val s2 = engine.worldToScreen(entity.x2, entity.y2)
                drawLine(color = dxfColor, start = s1, end = s2, strokeWidth = 1.5f)
            }
            is DxfEntity.Polyline -> {
                val verts = entity.vertices
                for (i in 0 until verts.size - 1) {
                    val s1 = engine.worldToScreen(verts[i].x, verts[i].y)
                    val s2 = engine.worldToScreen(verts[i + 1].x, verts[i + 1].y)
                    drawLine(color = dxfColor, start = s1, end = s2, strokeWidth = 1.5f)
                }
                if (entity.closed && verts.size > 2) {
                    val s1 = engine.worldToScreen(verts.last().x, verts.last().y)
                    val s2 = engine.worldToScreen(verts.first().x, verts.first().y)
                    drawLine(color = dxfColor, start = s1, end = s2, strokeWidth = 1.5f)
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════
// Annotations Layer
// ════════════════════════════════════════════════════

private fun DrawScope.drawAnnotations(engine: MapTransformEngine, annotations: List<MapAnnotation>) {
    val annotationColor = Color(0xFF00E5FF)
    val markerColor = Color(0xFFFF5722)

    for (ann in annotations) {
        val pts = MapSerializationUtils.parseJsonToPoints(ann.pointsJson)
        when (ann.type) {
            "MARKER" -> {
                if (pts.isNotEmpty()) {
                    val s = engine.worldToScreen(pts[0].x, pts[0].y)
                    drawCircle(color = markerColor, radius = 10f, center = s)
                    drawCircle(color = Color.White, radius = 4f, center = s)
                }
            }
            "LINE" -> {
                for (i in 0 until pts.size - 1) {
                    val s1 = engine.worldToScreen(pts[i].x, pts[i].y)
                    val s2 = engine.worldToScreen(pts[i + 1].x, pts[i + 1].y)
                    drawLine(color = annotationColor, start = s1, end = s2, strokeWidth = 3f)
                }
                pts.forEach { pt ->
                    val s = engine.worldToScreen(pt.x, pt.y)
                    drawCircle(color = annotationColor, radius = 4f, center = s)
                }
            }
            "POLYGON" -> {
                if (pts.isNotEmpty()) {
                    for (i in 0 until pts.size - 1) {
                        val s1 = engine.worldToScreen(pts[i].x, pts[i].y)
                        val s2 = engine.worldToScreen(pts[i + 1].x, pts[i + 1].y)
                        drawLine(color = annotationColor, start = s1, end = s2, strokeWidth = 3f)
                    }
                    val sLast = engine.worldToScreen(pts.last().x, pts.last().y)
                    val sFirst = engine.worldToScreen(pts.first().x, pts.first().y)
                    drawLine(color = annotationColor, start = sLast, end = sFirst, strokeWidth = 3f)
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════
// Active Drawing Layer
// ════════════════════════════════════════════════════

private fun DrawScope.drawActiveDrawing(
    engine: MapTransformEngine,
    collectedPoints: List<MapVertex>,
    currentMode: MapMode,
    measureSubMode: MeasureSubMode,
    zLabelPaint: Paint
) {
    val activeColor = when (currentMode) {
        MapMode.PLOT -> Color(0xFFFF9800)
        MapMode.MEASURE -> Color(0xFFFFEB3B)
        else -> Color(0xFF00E5FF)
    }

    // Lines between points
    if (collectedPoints.size >= 2) {
        for (i in 0 until collectedPoints.size - 1) {
            val s1 = engine.worldToScreen(collectedPoints[i].x, collectedPoints[i].y)
            val s2 = engine.worldToScreen(collectedPoints[i + 1].x, collectedPoints[i + 1].y)
            drawLine(color = activeColor, start = s1, end = s2, strokeWidth = 4f)
        }
    }

    // Vertex dots
    collectedPoints.forEach { pt ->
        val s = engine.worldToScreen(pt.x, pt.y)
        drawCircle(color = activeColor, radius = 6f, center = s)
        drawCircle(color = Color.White, radius = 2f, center = s)
    }

    // Z labels
    collectedPoints.forEach { pt ->
        if (pt.z != null) {
            val s = engine.worldToScreen(pt.x, pt.y)
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
        val sLast = engine.worldToScreen(collectedPoints.last().x, collectedPoints.last().y)
        val sFirst = engine.worldToScreen(collectedPoints.first().x, collectedPoints.first().y)
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
    engine: MapTransformEngine,
    gpsPosition: Pair<Double, Double>,
    accuracy: Float?
) {
    val (gx, gy) = gpsPosition
    val screenPos = engine.worldToScreen(gx, gy)

    // Accuracy circle (scales with map zoom — it represents real meters)
    if (accuracy != null && accuracy > 0f) {
        val radiusPx = accuracy * engine.scale  // meters → screen pixels
        drawCircle(
            color = Color(0xFF2979FF).copy(alpha = 0.10f),
            radius = radiusPx,
            center = screenPos
        )
        drawCircle(
            color = Color(0xFF2979FF).copy(alpha = 0.25f),
            radius = radiusPx,
            center = screenPos,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
        )
    }

    // GPS dot — FIXED screen size (10px outer, 4px inner) regardless of zoom
    drawCircle(color = Color(0xFF2979FF).copy(alpha = 0.3f), radius = 20f, center = screenPos)
    drawCircle(color = Color(0xFF2979FF), radius = 8f, center = screenPos)
    drawCircle(color = Color.White, radius = 3f, center = screenPos)
}

// ════════════════════════════════════════════════════
// ID Point Marker
// ════════════════════════════════════════════════════

private fun DrawScope.drawIdPointMarker(engine: MapTransformEngine, marker: MapVertex) {
    val s = engine.worldToScreen(marker.x, marker.y)
    val crossSize = 16f

    drawLine(
        color = Color(0xFFFF5722),
        start = Offset(s.x - crossSize, s.y),
        end = Offset(s.x + crossSize, s.y),
        strokeWidth = 2f
    )
    drawLine(
        color = Color(0xFFFF5722),
        start = Offset(s.x, s.y - crossSize),
        end = Offset(s.x, s.y + crossSize),
        strokeWidth = 2f
    )
    drawCircle(color = Color(0xFFFF5722).copy(alpha = 0.3f), radius = 20f, center = s)
    drawCircle(color = Color(0xFFFF5722), radius = 6f, center = s)
}
