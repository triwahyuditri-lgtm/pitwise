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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.sqrt
import com.example.pitwise.data.local.entity.MapAnnotation
import com.example.pitwise.domain.map.DxfEntity
import com.example.pitwise.domain.map.DxfFile
import com.example.pitwise.domain.map.MapMode
import com.example.pitwise.domain.map.MapPoint
import com.example.pitwise.domain.map.MapSerializationUtils
import com.example.pitwise.domain.map.MapTransformEngine
import com.example.pitwise.domain.map.MapVertex
import com.example.pitwise.domain.map.MeasureSubMode
import com.example.pitwise.domain.map.SnapType

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
    gpsAccuracy: Float? = null,
    selectedAnnotationId: Long? = null,
    idPointSnapped: Boolean = false,
    idPointSnapType: SnapType = SnapType.VERTEX
) {
    // ── Cached Paint objects (zero allocation per frame) ──
    val zLabelPaint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 24f
            isAntiAlias = true
        }
    }
    val measureLabelPaint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 28f
            isAntiAlias = true
            isFakeBoldText = true
            setShadowLayer(4f, 1f, 1f, android.graphics.Color.BLACK)
        }
    }
    val measureBgPaint = remember {
        Paint().apply {
            color = android.graphics.Color.argb(200, 30, 30, 30)
            isAntiAlias = true
        }
    }

    // ── Pre-parse annotation points (once per annotation list change, NOT per frame) ──
    val parsedAnnotations = remember(annotations) {
        annotations.map { ann ->
            Pair(ann, MapSerializationUtils.parseJsonToPoints(ann.pointsJson))
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
        drawAnnotations(engine, parsedAnnotations, selectedAnnotationId)

        // ══ Persisted Measurement Labels (on-canvas) ══
        drawAnnotationLabels(engine, parsedAnnotations, measureLabelPaint, measureBgPaint)

        // ══ Active Drawing (Collected Points) ══
        if (collectedPoints.isNotEmpty()) {
            drawActiveDrawing(engine, collectedPoints, currentMode, measureSubMode,
                zLabelPaint, measureLabelPaint, measureBgPaint)
        }

        // ══ GPS Layer — drawn in SCREEN coordinates (fixed size) ══
        if (showGpsLayer && gpsPosition != null) {
            drawGpsLayer(engine, gpsPosition, gpsAccuracy)
        }

        // ══ ID Point Marker ══
        if (idPointMarker != null) {
            drawIdPointMarker(engine, idPointMarker, idPointSnapped, idPointSnapType)
        }
    }
}

// ════════════════════════════════════════════════════
// DXF Layer
// ════════════════════════════════════════════════════

private fun DrawScope.drawDxfLayer(engine: MapTransformEngine, dxf: DxfFile) {
    val dxfColor = Color(0xFF00FF66) // Neon Green

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

private fun DrawScope.drawAnnotations(
    engine: MapTransformEngine,
    parsedAnnotations: List<Pair<MapAnnotation, List<MapPoint>>>,
    selectedAnnotationId: Long?
) {
    val pointColor = Color(0xFF2196F3)
    val lineColor = Color(0xFFFF9800)
    val polygonColor = Color(0xFF00E5FF)
    val selectedGlow = Color(0xFF448AFF)
    val markerColor = Color(0xFFFF5722)

    for ((ann, pts) in parsedAnnotations) {
        val isSelected = ann.id == selectedAnnotationId

        when (ann.type) {
            "POINT" -> {
                if (pts.isNotEmpty()) {
                    val s = engine.worldToScreen(pts[0].x, pts[0].y)
                    if (isSelected) {
                        // Selection glow
                        drawCircle(color = selectedGlow.copy(alpha = 0.3f), radius = 20f, center = s)
                        drawCircle(color = selectedGlow, radius = 14f, center = s,
                            style = Stroke(width = 3f))
                    }
                    // Blue pin marker
                    drawCircle(color = pointColor, radius = 10f, center = s)
                    drawCircle(color = Color.White, radius = 4f, center = s)
                }
            }
            "MARKER" -> {
                // Legacy support for old markers
                if (pts.isNotEmpty()) {
                    val s = engine.worldToScreen(pts[0].x, pts[0].y)
                    if (isSelected) {
                        drawCircle(color = selectedGlow.copy(alpha = 0.3f), radius = 20f, center = s)
                    }
                    drawCircle(color = markerColor, radius = 10f, center = s)
                    drawCircle(color = Color.White, radius = 4f, center = s)
                }
            }
            "LINE" -> {
                // Line segments
                for (i in 0 until pts.size - 1) {
                    val s1 = engine.worldToScreen(pts[i].x, pts[i].y)
                    val s2 = engine.worldToScreen(pts[i + 1].x, pts[i + 1].y)
                    if (isSelected) {
                        drawLine(color = selectedGlow.copy(alpha = 0.3f),
                            start = s1, end = s2, strokeWidth = 8f)
                    }
                    drawLine(color = lineColor, start = s1, end = s2, strokeWidth = 3f)
                }
                // Vertex markers
                pts.forEach { pt ->
                    val s = engine.worldToScreen(pt.x, pt.y)
                    if (isSelected) {
                        // Blue square selection markers
                        drawRect(color = selectedGlow, topLeft = Offset(s.x - 6f, s.y - 6f),
                            size = androidx.compose.ui.geometry.Size(12f, 12f))
                    } else {
                        drawCircle(color = lineColor, radius = 4f, center = s)
                    }
                }
            }
            "POLYGON" -> {
                if (pts.isNotEmpty()) {
                    // Polygon edges
                    for (i in 0 until pts.size - 1) {
                        val s1 = engine.worldToScreen(pts[i].x, pts[i].y)
                        val s2 = engine.worldToScreen(pts[i + 1].x, pts[i + 1].y)
                        if (isSelected) {
                            drawLine(color = selectedGlow.copy(alpha = 0.3f),
                                start = s1, end = s2, strokeWidth = 8f)
                        }
                        drawLine(color = polygonColor, start = s1, end = s2, strokeWidth = 3f)
                    }
                    // Closing edge
                    val sLast = engine.worldToScreen(pts.last().x, pts.last().y)
                    val sFirst = engine.worldToScreen(pts.first().x, pts.first().y)
                    if (isSelected) {
                        drawLine(color = selectedGlow.copy(alpha = 0.3f),
                            start = sLast, end = sFirst, strokeWidth = 8f)
                    }
                    drawLine(color = polygonColor, start = sLast, end = sFirst, strokeWidth = 3f)

                    // Vertex markers
                    pts.forEach { pt ->
                        val s = engine.worldToScreen(pt.x, pt.y)
                        if (isSelected) {
                            drawRect(color = selectedGlow, topLeft = Offset(s.x - 6f, s.y - 6f),
                                size = androidx.compose.ui.geometry.Size(12f, 12f))
                        }
                    }
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
    zLabelPaint: Paint,
    labelPaint: Paint,
    bgPaint: Paint
) {
    val isMeasure = currentMode == MapMode.MEASURE
    val isAreaMode = (currentMode == MapMode.MEASURE && measureSubMode == MeasureSubMode.AREA) ||
        (currentMode == MapMode.PLOT && collectedPoints.size >= 3)

    // Colors based on mode
    val primaryColor = when {
        isMeasure -> Color(0xFFFFEB3B)     // Yellow for measure
        else -> Color(0xFFFF9800)           // Orange for plot
    }
    val glowColor = primaryColor.copy(alpha = 0.25f)
    val fillColor = if (isMeasure && isAreaMode) Color(0xFF00E5FF).copy(alpha = 0.10f)
                    else primaryColor.copy(alpha = 0.08f)

    // Dynamic stroke width - always visible even zoomed out
    val strokeW = maxOf(4f, 5f / engine.scale)
    val glowW = strokeW * 2.5f

    // ── Area Fill (semi-transparent polygon) ──
    if (isAreaMode && collectedPoints.size >= 3) {
        val path = Path()
        val s0 = engine.worldToScreen(collectedPoints[0].x, collectedPoints[0].y)
        path.moveTo(s0.x, s0.y)
        for (i in 1 until collectedPoints.size) {
            val s = engine.worldToScreen(collectedPoints[i].x, collectedPoints[i].y)
            path.lineTo(s.x, s.y)
        }
        path.close()
        drawPath(path = path, color = fillColor)
    }

    // ── Lines between points (shadow glow + solid primary) ──
    if (collectedPoints.size >= 2) {
        for (i in 0 until collectedPoints.size - 1) {
            val s1 = engine.worldToScreen(collectedPoints[i].x, collectedPoints[i].y)
            val s2 = engine.worldToScreen(collectedPoints[i + 1].x, collectedPoints[i + 1].y)
            // Shadow glow
            drawLine(color = glowColor, start = s1, end = s2, strokeWidth = glowW,
                cap = androidx.compose.ui.graphics.StrokeCap.Round)
            // Primary line
            drawLine(color = primaryColor, start = s1, end = s2, strokeWidth = strokeW,
                cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }

    // ── Closing dashed line for area modes ──
    if (isAreaMode && collectedPoints.size >= 3) {
        val sLast = engine.worldToScreen(collectedPoints.last().x, collectedPoints.last().y)
        val sFirst = engine.worldToScreen(collectedPoints.first().x, collectedPoints.first().y)
        drawLine(
            color = primaryColor.copy(alpha = 0.5f),
            start = sLast, end = sFirst,
            strokeWidth = maxOf(3f, strokeW * 0.7f),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }

    // ── Vertex markers (big outer ring + white center) ──
    collectedPoints.forEach { pt ->
        val s = engine.worldToScreen(pt.x, pt.y)
        // Outer glow
        drawCircle(color = primaryColor.copy(alpha = 0.3f), radius = 16f, center = s)
        // Colored ring
        drawCircle(color = primaryColor, radius = 10f, center = s, style = Stroke(width = 3f))
        // White center
        drawCircle(color = Color.White, radius = 4f, center = s)
    }

    // ── Mid-segment distance labels ──
    if (isMeasure && collectedPoints.size >= 2) {
        drawIntoCanvas { canvas ->
            for (i in 0 until collectedPoints.size - 1) {
                val p1 = collectedPoints[i]
                val p2 = collectedPoints[i + 1]
                val dx = p2.x - p1.x
                val dy = p2.y - p1.y
                val segDist = sqrt(dx * dx + dy * dy)
                val label = "%.2f m".format(segDist)

                // Midpoint in screen coordinates
                val mid = engine.worldToScreen(
                    (p1.x + p2.x) / 2.0,
                    (p1.y + p2.y) / 2.0
                )

                val textW = labelPaint.measureText(label)
                val textH = 28f // approx text height
                val padH = 10f
                val padV = 6f

                // Dark pill background
                canvas.nativeCanvas.drawRoundRect(
                    mid.x - textW / 2 - padH,
                    mid.y - textH / 2 - padV,
                    mid.x + textW / 2 + padH,
                    mid.y + textH / 2 + padV,
                    12f, 12f, bgPaint
                )
                // White text
                canvas.nativeCanvas.drawText(
                    label,
                    mid.x - textW / 2,
                    mid.y + textH / 4,
                    labelPaint
                )
            }
        }
    }

    // ── Area centroid label (total area + perimeter) ──
    if (isMeasure && isAreaMode && collectedPoints.size >= 3) {
        // Compute centroid
        val cx = collectedPoints.sumOf { it.x } / collectedPoints.size
        val cy = collectedPoints.sumOf { it.y } / collectedPoints.size
        val centroid = engine.worldToScreen(cx, cy)

        // Compute area (Shoelace)
        var areaSum = 0.0
        for (i in collectedPoints.indices) {
            val j = (i + 1) % collectedPoints.size
            areaSum += collectedPoints[i].x * collectedPoints[j].y
            areaSum -= collectedPoints[j].x * collectedPoints[i].y
        }
        val area = kotlin.math.abs(areaSum) / 2.0

        // Compute perimeter
        var perim = 0.0
        for (i in 0 until collectedPoints.size) {
            val j = (i + 1) % collectedPoints.size
            val ddx = collectedPoints[j].x - collectedPoints[i].x
            val ddy = collectedPoints[j].y - collectedPoints[i].y
            perim += sqrt(ddx * ddx + ddy * ddy)
        }

        val areaLabel = "%.2f m\u00B2".format(area)
        val perimLabel = "P: %.2f m".format(perim)

        drawIntoCanvas { canvas ->
            val areaW = labelPaint.measureText(areaLabel)
            val perimW = labelPaint.measureText(perimLabel)
            val maxW = maxOf(areaW, perimW)
            val padH = 12f
            val padV = 6f
            val lineH = 32f
            val totalH = lineH * 2 + padV * 2

            canvas.nativeCanvas.drawRoundRect(
                centroid.x - maxW / 2 - padH,
                centroid.y - totalH / 2,
                centroid.x + maxW / 2 + padH,
                centroid.y + totalH / 2,
                14f, 14f, bgPaint
            )
            canvas.nativeCanvas.drawText(
                areaLabel,
                centroid.x - areaW / 2,
                centroid.y - 4f,
                labelPaint
            )
            // Perimeter in slightly smaller font
            canvas.nativeCanvas.drawText(
                perimLabel,
                centroid.x - perimW / 2,
                centroid.y + lineH - 4f,
                labelPaint
            )
        }
    }

    // ── Z labels (kept for reference) ──
    if (!isMeasure) {
        collectedPoints.forEach { pt ->
            if (pt.z != null) {
                val s = engine.worldToScreen(pt.x, pt.y)
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(
                        "Z:${"%.1f".format(pt.z)}",
                        s.x + 14f,
                        s.y - 14f,
                        zLabelPaint
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════
// Persisted Annotation Labels (mid-segment + area/perimeter)
// ════════════════════════════════════════════════════

private fun DrawScope.drawAnnotationLabels(
    engine: MapTransformEngine,
    parsedAnnotations: List<Pair<MapAnnotation, List<MapPoint>>>,
    labelPaint: Paint,
    bgPaint: Paint
) {
    for ((ann, pts) in parsedAnnotations) {
        if (pts.size < 2) continue

        when (ann.type) {
            "LINE" -> {
                // Total distance label at line midpoint
                if (ann.distance > 0) {
                    val midIdx = pts.size / 2
                    val midPt = if (pts.size % 2 == 0 && midIdx > 0) {
                        val p1 = pts[midIdx - 1]
                        val p2 = pts[midIdx]
                        MapPoint((p1.x + p2.x) / 2, (p1.y + p2.y) / 2)
                    } else pts[midIdx]

                    val s = engine.worldToScreen(midPt.x, midPt.y)
                    val label = "%.2f m".format(ann.distance)
                    drawPillLabel(s, label, labelPaint, bgPaint)
                }
            }
            "POLYGON" -> {
                // Area + perimeter at centroid
                if (ann.area > 0) {
                    val cx = pts.sumOf { it.x } / pts.size
                    val cy = pts.sumOf { it.y } / pts.size
                    val s = engine.worldToScreen(cx, cy)

                    val areaLabel = "%.2f m\u00B2".format(ann.area)
                    drawPillLabel(s, areaLabel, labelPaint, bgPaint)
                }
            }
        }
    }
}

private fun DrawScope.drawPillLabel(
    pos: Offset,
    text: String,
    labelPaint: Paint,
    bgPaint: Paint
) {
    val textW = labelPaint.measureText(text)
    val padH = 10f
    val padV = 6f
    val textH = 28f

    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawRoundRect(
            pos.x - textW / 2 - padH,
            pos.y - textH / 2 - padV,
            pos.x + textW / 2 + padH,
            pos.y + textH / 2 + padV,
            12f, 12f, bgPaint
        )
        canvas.nativeCanvas.drawText(
            text,
            pos.x - textW / 2,
            pos.y + textH / 4,
            labelPaint
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

    // 1. Accuracy circle (World Scale) - Represents real error range
    if (accuracy != null && accuracy > 0f) {
        val radiusPx = accuracy * engine.scale  // meters -> screen pixels
        // Only draw if reasonably visible to avoid clutter
        if (radiusPx > 2f) {
            drawCircle(
                color = Color(0xFF2979FF).copy(alpha = 0.15f),
                radius = radiusPx,
                center = screenPos
            )
            drawCircle(
                color = Color(0xFF2979FF).copy(alpha = 0.4f),
                radius = radiusPx,
                center = screenPos,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
            )
        }
    }

    // 2. Main GPS Marker (Screen Space - Fixed Size)
    // Does NOT scale with map zoom.
    
    // Outer white halo/shadow for contrast against dark maps
    drawCircle(
        color = Color.White.copy(alpha = 0.8f),
        radius = 26f, // Slightly larger than main body
        center = screenPos,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
    )
    
    // Main Blue Body
    drawCircle(
        color = Color(0xFF2196F3), // Material Blue 500
        radius = 22f, // Fixed 22px
        center = screenPos
    )
    
    // Inner Darker Blue (Depth effect)
    drawCircle(
        color = Color(0xFF1976D2), // Material Blue 700
        radius = 18f, 
        center = screenPos
    )
    
    // Center alignment point (White)
    drawCircle(
        color = Color.White,
        radius = 5f,
        center = screenPos
    )
    
    // Optional: Direction arrow could go here if bearing is known
}

// ════════════════════════════════════════════════════
// ID Point Marker
// ════════════════════════════════════════════════════

private fun DrawScope.drawIdPointMarker(
    engine: MapTransformEngine,
    marker: MapVertex,
    snapped: Boolean,
    snapType: SnapType
) {
    val s = engine.worldToScreen(marker.x, marker.y)
    val accentColor = Color(0xFF2196F3) // DWG FastView blue

    // Outer glow pulse
    drawCircle(color = accentColor.copy(alpha = 0.10f), radius = 36f, center = s)
    drawCircle(color = accentColor.copy(alpha = 0.20f), radius = 24f, center = s)

    if (snapped && snapType == SnapType.SEGMENT) {
        // Segment snap — perpendicular tick marks
        val tickLen = 14f
        drawLine(color = accentColor, start = Offset(s.x - tickLen, s.y),
            end = Offset(s.x + tickLen, s.y), strokeWidth = 2f)
        drawLine(color = accentColor, start = Offset(s.x, s.y - tickLen),
            end = Offset(s.x, s.y + tickLen), strokeWidth = 2f)
    } else if (snapped) {
        // Vertex snap — highlight ring
        drawCircle(color = accentColor, radius = 16f, center = s,
            style = Stroke(width = 2.5f))
    }

    // Main dot (large, DWG FastView style)
    drawCircle(color = accentColor, radius = 12f, center = s)
    drawCircle(color = Color.White, radius = 5f, center = s)

    // Arrow indicator pointing upward (survey pick style)
    val arrowPath = Path().apply {
        moveTo(s.x, s.y - 28f)       // top of arrow
        lineTo(s.x - 7f, s.y - 18f)  // left wing
        lineTo(s.x + 7f, s.y - 18f)  // right wing
        close()
    }
    drawPath(arrowPath, color = accentColor)
}
