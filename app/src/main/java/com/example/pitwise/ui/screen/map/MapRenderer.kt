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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import android.util.Log
import com.example.pitwise.data.local.entity.MapAnnotation
import com.example.pitwise.domain.dxf.DxfEntity
import com.example.pitwise.domain.dxf.DxfModel
import com.example.pitwise.domain.map.MapMode
import com.example.pitwise.domain.map.MapSerializationUtils
import com.example.pitwise.domain.map.MapTransformEngine
import com.example.pitwise.domain.map.MapVertex
import com.example.pitwise.domain.map.MeasureSubMode

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
    dxfModel: DxfModel?,
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

    // ── Cached DXF Geometry ──
    val dxfGeometry = remember(dxfModel) {
        if (dxfModel == null) null else {
            val geo = generateDxfGeometry(dxfModel)
            Log.d("DXF_RENDER", "Generated geometry: ${geo.paths.size} color groups, " +
                "${geo.points.size} point groups, " +
                "bounds=[X:${dxfModel.bounds.minX}..${dxfModel.bounds.maxX}, " +
                "Y:${dxfModel.bounds.minY}..${dxfModel.bounds.maxY}]")
            geo
        }
    }

    // ── One-time render validation flag ──
    var renderValidated = remember { mutableSetOf<String>() }

    Canvas(modifier = modifier.fillMaxSize()) {
        // ════════════════════════════════════════════════════
        // LAYER 2: TRANSFORM HELPER
        // ════════════════════════════════════════════════════
        // Converts World Coordinate (Projected/Local) -> Screen Pixel
        // Used for UI overlays (GPS, Markers, Labels)
        
        fun worldToScreen(wx: Double, wy: Double): Offset {
             // 1. Apply Y-inversion (World Space -> Visual Space)
             val visualY = if (flipY) -wy else wy
             
             // 2. Apply Transform (Visual Space -> Screen Space)
             // Use Double for intermediate calculation to preserve precision
             // when World * Scale is large but cancels out with Offset.
             val sx = (wx * scale.toDouble()) + offsetX.toDouble()
             val sy = (visualY * scale.toDouble()) + offsetY.toDouble()
             
             return Offset(sx.toFloat(), sy.toFloat())
        }

        // ════════════════════════════════════════════════════
        // LAYER 1: WORLD SPACE RENDERING (Map Content)
        // ════════════════════════════════════════════════════
        // We apply the transform ONCE for the entire map layer.
        // Content inside is drawn in World Coordinates (0,0 is Origin).

        withTransform({
            // Order: Scale then Translate? Or Translate then Scale?
            // Android Canvas: transformations are pre-concatenated.
            // We want: Screen = (World * Scale) + Offset.
            // So we apply Translate first, then Scale?
            // Matrix M:
            // M.translate(ox, oy)
            // M.scale(s, s)
            // Point P:  M * P = Translate(Scale(P)) ?
            // No, standard canvas `translate` moves the origin. `scale` scales axes.
            // If we write:
            // translate(offsetX, offsetY)
            // scale(scale, scale)
            // 
            // Operations apply to the *coordinate system*.
            // 1. translate(ox, oy): Origin moves to (ox, oy).
            // 2. scale(s, s): Axes scaled by s.
            // Drawing at (1, 0) in new system:
            // Distance 1 along scaled X axis starting from origin (ox, oy).
            // Screen X = ox + (1 * s).  Matches (x*s + ox) if order is reversed?
            // Wait.
            // If I draw at (10, 10).
            // Scaled axes means 10 units = 10*s pixels.
            // Translated origin means (0,0) is at (ox, oy).
            // So (10, 10) -> (ox + 10*s, oy + 10*s).
            // This MATCHES: Screen = (World * scale) + offset.
            
            translate(offsetX, offsetY)
            scale(scale, scale)
        }) {
            // ── A. PDF Layer ──
            if (showPdfLayer && pdfBitmap != null) {
                // PDF is usually Raster, origin (0,0) is top-left.
                // It aligns with World (0,0) in our Local definition.
                drawImage(pdfBitmap.asImageBitmap())
            }

            // ── B. DXF Layer ──
            // Rendered using Relative Coordinates (Center-based) to preserve float precision
            // The drawing function handles its own transform stack (Translate Center -> Scale)
            if (showDxfLayer && dxfGeometry != null) {
                // We do NOT use the outer 'scale(scale, scale)' here because drawDxfPaths
                // establishes its own relative coordinate system centered on the geometry.
                // However, we ARE inside 'translate(offsetX, offsetY); scale(scale, scale)' from lines 137-138.
                // Wait. If we are inside `scale(scale, scale)`, precise large translations won't work.
                // We need to escape the outer transform or compensate.
                // Actually, `drawDxfPaths` uses `worldToScreen` to get the screen center.
                // `worldToScreen` returns absolute screen coordinates.
                // So we should NOT be inside the relative transform block of Layer 1.
                // We should move this call OUTSIDE the `withTransform` block of Layer 1.
            }
        } // End of Layer 1 transform

        // ── B. DXF Layer (Relative Render) ──
        if (showDxfLayer && dxfGeometry != null) {
            drawDxfPaths(dxfGeometry, scale, flipY, ::worldToScreen, renderValidated)
        }

        // ════════════════════════════════════════════════════
        // LAYER 3: SCREEN SPACE RENDERING (UI Overlays)
        // ════════════════════════════════════════════════════
        // All elements here are drawn in SCREEN PIXELS.
        // We manually project World -> Screen for each point.
        
        // ── Annotations ──
        if (annotations.isNotEmpty()) {
            drawAnnotations(annotations, ::worldToScreen)
        }

        // ── Active Drawing (Collected Points) ──
        if (collectedPoints.isNotEmpty()) {
            drawActiveDrawing(collectedPoints, currentMode, measureSubMode, zLabelPaint, ::worldToScreen)
        }

        // ── GPS Layer ──
        // Drawn strictly in Screen Space. 
        // Marker size is fixed in pixels (does not scale with map).
        if (showGpsLayer && gpsPosition != null) {
            drawGpsLayer(gpsPosition, gpsHeading, gpsAccuracy, scale, offsetX, offsetY, flipY, renderValidated, ::worldToScreen)
        }

        // ── ID Point Marker ──
        if (idPointMarker != null) {
            drawIdPointMarker(idPointMarker, isSnapped, ::worldToScreen)
        }

        // ════════════════════════════════════════════════════
        // DEBUG OVERLAY
        // ════════════════════════════════════════════════════
        // Temporary debug info to diagnose DXF rendering issues
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
                
                // Draw Bounds Rect (Transformed)
                try {
                    val p1 = worldToScreen(dxfModel.bounds.minX, dxfModel.bounds.minY)
                    val p2 = worldToScreen(dxfModel.bounds.maxX, dxfModel.bounds.maxY)
                    // Note: if flipY, minY is bottom physically, maxY is top.
                    // worldToScreen handles flip.
                    // But Rect needs left/top/right/bottom.
                    val left = minOf(p1.x, p2.x)
                    val top = minOf(p1.y, p2.y)
                    val right = maxOf(p1.x, p2.x)
                    val bottom = maxOf(p1.y, p2.y)
                    
                    drawRect(
                        color = Color.Red,
                        topLeft = Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                        style = Stroke(width = 2f)
                    )
                } catch (e: Exception) {
                    info.append("Bounds Draw Error: ${e.message}")
                }
            } else {
                info.append("DXF Model: NULL")
            }

            // Draw Origin (0,0)
            val origin = worldToScreen(0.0, 0.0)
            drawCircle(Color.Green, radius = 5f, center = origin)
            
            // Draw Text
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
// DXF Drawing Helper
// ════════════════════════════════════════════════════

private fun DrawScope.drawDxfPaths(
    geometry: DxfGeometry,
    mapScale: Float,
    flipY: Boolean,
    worldToScreen: (Double, Double) -> Offset,
    validated: MutableSet<String> = mutableSetOf()
) {
    // Calculate Screen Position of the Geometry Center
    val screenCenter = worldToScreen(geometry.centerX, geometry.centerY)

    // Apply Transform: Translate to Screen Center -> Scale (and flip Y if needed)
    withTransform({
        translate(screenCenter.x, screenCenter.y)
        // If flipY is true, we need to invert Y scale.
        // NOTE: worldToScreen ALREADY handles flipY for the center position.
        // But for the PATH itself (which is generated as Y-Up or Y-Down relative),
        // we need to match the coordinate system of the path.
        // DXF is Y-Up. Relative coords (y - cy) are Y-Up.
        // Screen is Y-Down.
        // If flipY is true (standard), we scale(1f, -1f).
        // If flipY is false (already Y-Down?), we scale(1f, 1f).
        val yScale = if (flipY) -mapScale else mapScale
        scale(mapScale, yScale)
    }) {
         // 1. Draw Lines/Polylines (Grouped by Color)
        // Stroke width: desire ~1.5 screen pixels.
        // Since we are inside a `scale(mapScale)` transform, 
        // a stroke width of W will be drawn as W * mapScale pixels.
        // To get 1.5 pixels, W must be 1.5 / mapScale.
        // We add a safety check for scale > 0.
        val targetScreenPx = 1.5f
        val strokeWidth = if (mapScale > 0) targetScreenPx / mapScale else 1f
        
        // Validation logging
        if ("dxf" !in validated) {
            validated.add("dxf")
            Log.d("DXF_RENDER_VALIDATE", "Drawing Relative: center=(${geometry.centerX}, ${geometry.centerY}) " +
                "screen=(${screenCenter.x}, ${screenCenter.y}) strokeWidth=$strokeWidth scale=$mapScale")
        }
        
        geometry.paths.forEach { (colorInt, path) ->
            drawPath(
                path = path,
                color = Color(colorInt),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
            )
        }

        // 2. Draw Points (Grouped by Color)
        // Radius: desire ~3 screen pixels.
        // Radius R will be drawn as R * mapScale pixels.
        // To get 3 pixels, R = 3 / mapScale.
        val targetRadiusPx = 3f
        val pointRadius = if (mapScale > 0) targetRadiusPx / mapScale else 2f

        geometry.points.forEach { (colorInt, pointsList) ->
            val pointColor = Color(colorInt)
            pointsList.forEach { point ->
                // Point coordinates are relative to center (0,0 in this transformed space)
                drawCircle(
                    color = pointColor, 
                    radius = pointRadius, 
                    center = Offset(point.x.toFloat(), point.y.toFloat())
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════
// Geometry Generators
// ════════════════════════════════════════════════════

private data class DxfGeometry(
    val paths: Map<Int, androidx.compose.ui.graphics.Path>,
    val points: Map<Int, List<DxfEntity.Point>>,
    val centerX: Double,
    val centerY: Double
)

private fun generateDxfGeometry(dxf: DxfModel): DxfGeometry {
    val paths = mutableMapOf<Int, androidx.compose.ui.graphics.Path>()
    val resultPoints = mutableMapOf<Int, MutableList<DxfEntity.Point>>()

    // Calculate center for relative rendering (prevents Float precision issues)
    val centerX = (dxf.bounds.minX + dxf.bounds.maxX) / 2.0
    val centerY = (dxf.bounds.minY + dxf.bounds.maxY) / 2.0

    // Helper to get path
    fun getPath(color: Int): androidx.compose.ui.graphics.Path {
        return paths.getOrPut(color) { androidx.compose.ui.graphics.Path() }
    }

    dxf.lines.forEach { line ->
        val p = getPath(line.color)
        p.moveTo((line.start.x - centerX).toFloat(), (line.start.y - centerY).toFloat())
        p.lineTo((line.end.x - centerX).toFloat(), (line.end.y - centerY).toFloat())
    }

    dxf.polylines.forEach { poly ->
        if (poly.vertices.isNotEmpty()) {
            val p = getPath(poly.color)
            val first = poly.vertices.first()
            p.moveTo((first.x - centerX).toFloat(), (first.y - centerY).toFloat())
            for (i in 1 until poly.vertices.size) {
                val v = poly.vertices[i]
                p.lineTo((v.x - centerX).toFloat(), (v.y - centerY).toFloat())
            }
            if (poly.isClosed) {
                p.close()
            }
        }
    }

    dxf.points.forEach { point ->
        // Store points relative to center?
        // Actually, drawCircle takes absolute offset if we use drawCircle.
        // But we want to use the same relative transform for points too.
        // So let's store RELATIVE coordinates in a new list?
        // DxfEntity.Point is immutable. We can create a lightweight relative point or just map it at draw time?
        // Mapping at draw time means dealing with Doubles again.
        // Better to store transformed points.
        // But DxfEntity.Point has metadata.
        // Let's just create a new list of relative offsets for drawing points.
        resultPoints.getOrPut(point.color) { mutableListOf() }.add(
            point.copy(x = point.x - centerX, y = point.y - centerY)
        )
    }

    return DxfGeometry(paths, resultPoints, centerX, centerY)
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
        val radiusPx = accuracy * scale  // meters → screen pixels
        // NOTE: If scale is "pixels per meter", this is correct.
        // If scale is "zoom factor" (1.0 = default view), then we need the base PPM.
        // But in MapTransformEngine, scale starts at 1.0f (or fitted value).
        // Does 1.0f mean 1 meter = 1 pixel?
        // Usually NO. 
        // But for display purposes, we assume `scale` relates World Units to Screen Pixels relatively.
        // If the World Unit matches Metric (UTM), then `scale` IS pixels/meter.
        // If World Unit is PDF pixels, then `scale` is Zoom factor.
        // If PDF pixels are not meters, then `accuracy * scale` is wrong unless we know meters/pixel.
        // BUT: user prompt says "Radius must NOT scale with zoom." -> Wait, user said "Radius must NOT scale with zoom" for the DOT.
        // For accuracy circle, it SHOULD scale with zoom (it's a geographic area).
        
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

    // GPS dot — FIXED screen size (20px outer, 8px inner) regardless of zoom
    drawCircle(color = Color(0xFF2979FF).copy(alpha = 0.3f), radius = 20f, center = screenPos)
    drawCircle(color = Color(0xFF2979FF), radius = 8f, center = screenPos)
    drawCircle(color = Color.White, radius = 3f, center = screenPos)
    
    // Heading Arrow
    // Rotates around screenPos
    val arrowPath = androidx.compose.ui.graphics.Path().apply {
         moveTo(0f, -40f) // Tip
         lineTo(12f, 0f)  // Bottom Right relative center
         lineTo(0f, -10f) // Inner notch
         lineTo(-12f, 0f) // Bottom Left relative center
         close()
    }
    
    withTransform({
        rotate(heading, pivot = screenPos)
        translate(screenPos.x, screenPos.y)
    }) {
        drawPath(arrowPath, color = Color(0xFF2979FF))
    }
}

// ════════════════════════════════════════════════════
// ID Point Marker (Screen Space)
// ════════════════════════════════════════════════════

private fun DrawScope.drawIdPointMarker(
    marker: MapVertex, 
    isSnapped: Boolean, 
    worldToScreen: (Double, Double) -> Offset
) {
    val s = worldToScreen(marker.x, marker.y)
    val crossSize = 16f
    
    // Green if snapped, Orange if free
    val color = if (isSnapped) Color(0xFF4CAF50) else Color(0xFFFF5722)

    drawLine(
        color = color,
        start = Offset(s.x - crossSize, s.y),
        end = Offset(s.x + crossSize, s.y),
        strokeWidth = 3f // Thicker for visibility
    )
    drawLine(
        color = color,
        start = Offset(s.x, s.y - crossSize),
        end = Offset(s.x, s.y + crossSize),
        strokeWidth = 3f
    )
    
    // Outer glow
    drawCircle(color = color.copy(alpha = 0.3f), radius = 20f, center = s)
    
    // Inner dot
    drawCircle(color = color, radius = 6f, center = s)
    
    // White center for contrast
    drawCircle(color = Color.White, radius = 2f, center = s)
}
