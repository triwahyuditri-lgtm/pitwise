package com.example.pitwise.ui.screen.map

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.example.pitwise.data.local.entity.MapAnnotation
import com.example.pitwise.domain.map.DrawingMode
import com.example.pitwise.domain.map.DxfEntity
import com.example.pitwise.domain.map.DxfFile
import com.example.pitwise.domain.map.MapPoint
import com.example.pitwise.domain.map.MapSerializationUtils

@Composable
fun MapContent(
    modifier: Modifier = Modifier,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    pdfBitmap: Bitmap?,
    dxfFile: DxfFile?,
    showPdfLayer: Boolean = true,
    showDxfLayer: Boolean = true,
    annotations: List<MapAnnotation>,
    currentDrawingPoints: List<MapPoint>,
    drawingMode: DrawingMode,
    gpsPosition: Pair<Double, Double>?,
    showGpsLayer: Boolean = true
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        // Translation & Scale
        drawIntoCanvas { canvas ->
            canvas.translate(offsetX, offsetY)
            
            // ── PDF Layer ──
            if (showPdfLayer && pdfBitmap != null) {
                canvas.save()
                canvas.scale(scale, scale)
                canvas.nativeCanvas.drawBitmap(
                    pdfBitmap, 0f, 0f,
                    Paint()
                )
                canvas.restore()
            }
        }
        
        // Compose DrawScope logic for vectors (easier than native canvas for lines)
        // We need to apply transform manually to coordinates
        
        // ── DXF Layer ──
        if (showDxfLayer && dxfFile != null) {
            val dxfColor = Color(0xFF00E5FF)
            
            for (entity in dxfFile.entities) {
                when (entity) {
                    is DxfEntity.Point -> {
                        val sx = (entity.x * scale).toFloat() + offsetX
                        val sy = (entity.y * scale).toFloat() + offsetY
                        drawCircle(color = dxfColor, radius = 3f, center = Offset(sx, sy))
                    }
                    is DxfEntity.Line -> {
                        drawLine(
                            color = dxfColor,
                            start = Offset((entity.x1 * scale).toFloat() + offsetX, (entity.y1 * scale).toFloat() + offsetY),
                            end = Offset((entity.x2 * scale).toFloat() + offsetX, (entity.y2 * scale).toFloat() + offsetY),
                            strokeWidth = 1.5f
                        )
                    }
                    is DxfEntity.Polyline -> {
                        val verts = entity.vertices
                        for (i in 0 until verts.size - 1) {
                            drawLine(
                                color = dxfColor,
                                start = Offset((verts[i].x * scale).toFloat() + offsetX, (verts[i].y * scale).toFloat() + offsetY),
                                end = Offset((verts[i + 1].x * scale).toFloat() + offsetX, (verts[i + 1].y * scale).toFloat() + offsetY),
                                strokeWidth = 1.5f
                            )
                        }
                        if (entity.closed && verts.size > 2) {
                            drawLine(
                                color = dxfColor,
                                start = Offset((verts.last().x * scale).toFloat() + offsetX, (verts.last().y * scale).toFloat() + offsetY),
                                end = Offset((verts.first().x * scale).toFloat() + offsetX, (verts.first().y * scale).toFloat() + offsetY),
                                strokeWidth = 1.5f
                            )
                        }
                    }
                    is DxfEntity.Text -> {
                        val sx = (entity.x * scale).toFloat() + offsetX
                        val sy = (entity.y * scale).toFloat() + offsetY
                        val textHeight = (entity.height * scale).toFloat().coerceIn(6f, 120f)
                        val textPaint = Paint().apply {
                            color = android.graphics.Color.rgb(200, 255, 220)
                            textSize = textHeight
                            isAntiAlias = true
                        }
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText(entity.text, sx, sy, textPaint)
                        }
                    }
                }
            }
        }

        // ── Persisted Annotations ──
        val annotationColor = Color(0xFF00E5FF)
        val markerColor = Color(0xFFFF5722)
        val polygonFill = Color(0xFF00E5FF).copy(alpha = 0.1f)

        for (ann in annotations) {
            val pts = MapSerializationUtils.parseJsonToPoints(ann.pointsJson)
            when (ann.type) {
                "MARKER" -> {
                    if (pts.isNotEmpty()) {
                        val pt = pts[0]
                        val sx = (pt.x * scale).toFloat() + offsetX
                        val sy = (pt.y * scale).toFloat() + offsetY
                        drawCircle(color = markerColor, radius = 10f, center = Offset(sx, sy))
                        drawCircle(color = Color.White, radius = 4f, center = Offset(sx, sy))
                    }
                }
                "LINE" -> {
                    for (i in 0 until pts.size - 1) {
                        drawLine(
                            color = annotationColor,
                            start = Offset((pts[i].x * scale).toFloat() + offsetX, (pts[i].y * scale).toFloat() + offsetY),
                            end = Offset((pts[i + 1].x * scale).toFloat() + offsetX, (pts[i + 1].y * scale).toFloat() + offsetY),
                            strokeWidth = 3f
                        )
                    }
                    pts.forEach { pt ->
                        drawCircle(
                            color = annotationColor,
                            radius = 4f,
                            center = Offset((pt.x * scale).toFloat() + offsetX, (pt.y * scale).toFloat() + offsetY)
                        )
                    }
                }
                "POLYGON" -> {
                    if (pts.isNotEmpty()) {
                        // Draw edges
                        for (i in 0 until pts.size - 1) {
                            drawLine(
                                color = annotationColor,
                                start = Offset((pts[i].x * scale).toFloat() + offsetX, (pts[i].y * scale).toFloat() + offsetY),
                                end = Offset((pts[i + 1].x * scale).toFloat() + offsetX, (pts[i + 1].y * scale).toFloat() + offsetY),
                                strokeWidth = 3f
                            )
                        }
                        // Close loop
                        drawLine(
                            color = annotationColor,
                            start = Offset((pts.last().x * scale).toFloat() + offsetX, (pts.last().y * scale).toFloat() + offsetY),
                            end = Offset((pts.first().x * scale).toFloat() + offsetX, (pts.first().y * scale).toFloat() + offsetY),
                            strokeWidth = 3f
                        )
                        // Fill would use Path, simplified here to avoid path allocation in draw loop or just edges
                    }
                }
            }
        }

        // ── Current Drawing ──
        val currentPoints = currentDrawingPoints
        if (currentPoints.isNotEmpty()) {
            val currentLineColor = Color(0xFFFFEB3B) // Yellow
            
            // Draw lines
            if (currentPoints.size >= 2) {
                for (i in 0 until currentPoints.size - 1) {
                    drawLine(
                        color = currentLineColor,
                        start = Offset((currentPoints[i].x * scale).toFloat() + offsetX, (currentPoints[i].y * scale).toFloat() + offsetY),
                        end = Offset((currentPoints[i + 1].x * scale).toFloat() + offsetX, (currentPoints[i + 1].y * scale).toFloat() + offsetY),
                        strokeWidth = 4f
                    )
                }
            }
            // Draw points
            currentPoints.forEach { pt ->
                val sx = (pt.x * scale).toFloat() + offsetX
                val sy = (pt.y * scale).toFloat() + offsetY
                drawCircle(color = currentLineColor, radius = 6f, center = Offset(sx, sy))
            }
            
            // Closing line for polygon
            if (drawingMode == DrawingMode.POLYGON && currentPoints.size >= 3) {
                 drawLine(
                    color = currentLineColor.copy(alpha = 0.5f),
                    start = Offset((currentPoints.last().x * scale).toFloat() + offsetX, (currentPoints.last().y * scale).toFloat() + offsetY),
                    end = Offset((currentPoints.first().x * scale).toFloat() + offsetX, (currentPoints.first().y * scale).toFloat() + offsetY),
                    strokeWidth = 2f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            }
        }

        // ── GPS Layer ──
        if (showGpsLayer && gpsPosition != null) {
            val (gx, gy) = gpsPosition
            val screenGx = (gx * scale).toFloat() + offsetX
            val screenGy = (gy * scale).toFloat() + offsetY

            drawCircle(
                color = Color(0xFF2979FF).copy(alpha = 0.3f),
                radius = 30f,
                center = Offset(screenGx, screenGy)
            )
            drawCircle(
                color = Color(0xFF2979FF),
                radius = 8f,
                center = Offset(screenGx, screenGy)
            )
            drawCircle(
                color = Color.White,
                radius = 3f,
                center = Offset(screenGx, screenGy)
            )
        }
    }
}
