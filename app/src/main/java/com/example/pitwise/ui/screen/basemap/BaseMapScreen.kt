package com.example.pitwise.ui.screen.basemap

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Satellite
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pitwise.domain.map.BaseMapType
import com.example.pitwise.domain.map.ExportFormat
import com.example.pitwise.domain.map.MapMode
import com.example.pitwise.domain.map.MapPoint
import com.example.pitwise.domain.map.MapSerializationUtils
import com.example.pitwise.domain.map.MeasureSubMode
import com.example.pitwise.domain.map.PlotSubMode
import com.example.pitwise.ui.theme.PitwiseBorder
import com.example.pitwise.ui.theme.PitwiseGray400
import com.example.pitwise.ui.theme.PitwisePrimary
import com.example.pitwise.ui.theme.PitwiseSurface
import com.example.pitwise.data.local.entity.MapAnnotation
import kotlin.math.roundToInt

// ════════════════════════════════════════════════════
// Base Map Screen
// ════════════════════════════════════════════════════

@Composable
fun BaseMapScreen(
    onBack: () -> Unit = {},
    onSendToCalculator: (type: String, value: Double) -> Unit = { _, _ -> },
    viewModel: BaseMapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // GPS Permission
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            permissionDenied = false
            viewModel.toggleGpsTracking()
        } else {
            permissionDenied = true
        }
    }

    LaunchedEffect(Unit) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    // Snackbar
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    // Navigate to Calculator when measurement is finished
    LaunchedEffect(uiState.navigateToCalc) {
        uiState.navigateToCalc?.let { (type, value) ->
            onSendToCalculator(type, value)
            viewModel.consumeNavigateToCalc()
        }
    }

    val isDrawingMode = uiState.currentMode == MapMode.PLOT || uiState.currentMode == MapMode.MEASURE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1D21))
    ) {
        // ── Tile Map Canvas ──
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    viewModel.updateCanvasSize(size.width.toFloat(), size.height.toFloat())
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (zoom != 1f) viewModel.onZoom(zoom)
                        viewModel.onPan(pan.x, pan.y)
                    }
                }
                .pointerInput(uiState.currentMode) {
                    detectTapGestures(
                        onDoubleTap = { viewModel.zoomIn() },
                        onTap = { offset ->
                            // Check if tapping on a saved annotation
                            if (uiState.currentMode == MapMode.VIEW) {
                                val tapped = uiState.annotations.firstOrNull { ann ->
                                    val points = MapSerializationUtils.parseJsonToPoints(ann.pointsJson)
                                    points.any { pt ->
                                        val sc = viewModel.gpsToScreen(pt.y, pt.x)
                                        if (sc != null) {
                                            val dx = offset.x - sc.first
                                            val dy = offset.y - sc.second
                                            (dx * dx + dy * dy) < 900 // 30px radius
                                        } else false
                                    }
                                }
                                if (tapped != null) viewModel.selectAnnotation(tapped)
                            }
                        }
                    )
                }
        ) {
            val displayZoom = uiState.zoom
            val tileZoom = displayZoom.coerceAtMost(BaseMapViewModel.MAX_TILE_ZOOM)
            val overZoomScale = 1 shl (displayZoom - tileZoom)
            val tileRenderSize = BaseMapViewModel.TILE_SIZE * overZoomScale

            val totalTiles = 1 shl tileZoom

            val centerTileX = viewModel.lngToTileX(uiState.centerLng, tileZoom)
            val centerTileY = viewModel.latToTileY(uiState.centerLat, tileZoom)

            val centerScreenX = size.width / 2f
            val centerScreenY = size.height / 2f
            val tilesH = (size.width / tileRenderSize / 2).toInt() + 2
            val tilesV = (size.height / tileRenderSize / 2).toInt() + 2

            val tileXCenter = centerTileX.toInt()
            val tileYCenter = centerTileY.toInt()
            val fracX = (centerTileX - tileXCenter).toFloat()
            val fracY = (centerTileY - tileYCenter).toFloat()

            // Draw tiles (with overzoom scaling)
            for (dx in -tilesH..tilesH) {
                for (dy in -tilesV..tilesV) {
                    val tx = ((tileXCenter + dx) % totalTiles + totalTiles) % totalTiles
                    val ty = tileYCenter + dy
                    if (ty < 0 || ty >= totalTiles) continue

                    val key = "$tileZoom/$tx/$ty"
                    val bitmap = uiState.tiles[key]
                    val screenX = centerScreenX + (dx - fracX) * tileRenderSize
                    val screenY = centerScreenY + (dy - fracY) * tileRenderSize

                    if (bitmap != null) {
                        drawIntoCanvas { canvas ->
                            val srcRect = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
                            val dstRect = android.graphics.Rect(
                                screenX.roundToInt(), screenY.roundToInt(),
                                (screenX + tileRenderSize).roundToInt(), (screenY + tileRenderSize).roundToInt()
                            )
                            canvas.nativeCanvas.drawBitmap(bitmap, srcRect, dstRect, null)
                        }

                        // Label overlay for HYBRID
                        if (uiState.mapType == BaseMapType.HYBRID) {
                            val labelBmp = uiState.tiles["labels/$key"]
                            if (labelBmp != null) {
                                drawIntoCanvas { canvas ->
                                    val srcRect = android.graphics.Rect(0, 0, labelBmp.width, labelBmp.height)
                                    val dstRect = android.graphics.Rect(
                                        screenX.roundToInt(), screenY.roundToInt(),
                                        (screenX + tileRenderSize).roundToInt(), (screenY + tileRenderSize).roundToInt()
                                    )
                                    canvas.nativeCanvas.drawBitmap(labelBmp, srcRect, dstRect, null)
                                }
                            }
                        }
                    } else {
                        drawRect(
                            color = Color(0xFF2A2D31),
                            topLeft = Offset(screenX, screenY),
                            size = Size(tileRenderSize.toFloat(), tileRenderSize.toFloat())
                        )
                    }
                }
            }

            // ── Draw saved annotations ──
            for (annotation in uiState.annotations) {
                val points = MapSerializationUtils.parseJsonToPoints(annotation.pointsJson)
                val screenPoints = points.mapNotNull { pt ->
                    viewModel.gpsToScreen(pt.y, pt.x) // y=lat, x=lng
                }

                val annotColor = try {
                    Color(android.graphics.Color.parseColor(annotation.color))
                } catch (_: Exception) { Color(0xFF00E5FF) }

                when (annotation.type) {
                    "POINT" -> {
                        screenPoints.firstOrNull()?.let { (sx, sy) ->
                            drawCircle(Color.White, 8f, Offset(sx, sy))
                            drawCircle(annotColor, 5f, Offset(sx, sy))
                        }
                    }
                    "LINE" -> {
                        for (i in 0 until screenPoints.size - 1) {
                            drawLine(
                                annotColor, Offset(screenPoints[i].first, screenPoints[i].second),
                                Offset(screenPoints[i + 1].first, screenPoints[i + 1].second),
                                strokeWidth = 3f, cap = StrokeCap.Round
                            )
                        }
                        screenPoints.forEach { (sx, sy) ->
                            drawCircle(annotColor, 4f, Offset(sx, sy))
                        }
                    }
                    "POLYGON" -> {
                        val pts = screenPoints + screenPoints.take(1)
                        for (i in 0 until pts.size - 1) {
                            drawLine(
                                annotColor, Offset(pts[i].first, pts[i].second),
                                Offset(pts[i + 1].first, pts[i + 1].second),
                                strokeWidth = 3f, cap = StrokeCap.Round
                            )
                        }
                        screenPoints.forEach { (sx, sy) ->
                            drawCircle(annotColor, 4f, Offset(sx, sy))
                        }
                    }
                }
            }

            // ── Draw active collection points ──
            if (uiState.collectedPoints.isNotEmpty()) {
                val screenPts = uiState.collectedPoints.mapNotNull { (lat, lng) ->
                    viewModel.gpsToScreen(lat, lng)
                }

                val activeColor = when (uiState.currentMode) {
                    MapMode.MEASURE -> Color(0xFFFFEB3B)
                    MapMode.PLOT -> Color(0xFFFF9800)
                    else -> Color(0xFF00E5FF)
                }

                // Draw lines between collected points
                for (i in 0 until screenPts.size - 1) {
                    drawLine(
                        activeColor, Offset(screenPts[i].first, screenPts[i].second),
                        Offset(screenPts[i + 1].first, screenPts[i + 1].second),
                        strokeWidth = 3f, cap = StrokeCap.Round
                    )
                }
                // Close polygon if in area/polygon mode
                val shouldClose = (uiState.currentMode == MapMode.MEASURE && uiState.measureSubMode == MeasureSubMode.AREA) ||
                        (uiState.currentMode == MapMode.PLOT && uiState.plotSubMode == PlotSubMode.POLYGON)
                if (shouldClose && screenPts.size >= 3) {
                    drawLine(
                        activeColor.copy(alpha = 0.5f),
                        Offset(screenPts.last().first, screenPts.last().second),
                        Offset(screenPts.first().first, screenPts.first().second),
                        strokeWidth = 2f, cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))
                    )
                }
                // Draw vertex dots
                screenPts.forEachIndexed { idx, (sx, sy) ->
                    drawCircle(Color.White, 7f, Offset(sx, sy))
                    drawCircle(activeColor, 5f, Offset(sx, sy))
                    if (idx == 0 && screenPts.size > 1) {
                        drawCircle(Color.White, 10f, Offset(sx, sy), style = Stroke(2f))
                    }
                }
            }

            // ── GPS Dot ──
            if (uiState.gpsLat != null && uiState.gpsLng != null) {
                val gpsScreen = viewModel.gpsToScreen(uiState.gpsLat!!, uiState.gpsLng!!)
                if (gpsScreen != null) {
                    val (gx, gy) = gpsScreen
                    val accuracy = uiState.gpsAccuracy ?: 10f
                    val metersPerPixel = 156543.03392 * kotlin.math.cos(
                        Math.toRadians(uiState.gpsLat!!)
                    ) / (1 shl uiState.zoom)
                    val accuracyPx = (accuracy / metersPerPixel).toFloat()
                    if (accuracyPx > 2f) {
                        drawCircle(Color(0xFF2196F3).copy(alpha = 0.15f), accuracyPx, Offset(gx, gy))
                        drawCircle(Color(0xFF2196F3).copy(alpha = 0.4f), accuracyPx, Offset(gx, gy),
                            style = Stroke(1.5f))
                    }
                    drawCircle(Color.White, 10f, Offset(gx, gy))
                    drawCircle(Color(0xFF2196F3), 7f, Offset(gx, gy))
                }
            }
        }

        // ── Crosshair (visible in draw/measure modes) ──
        if (isDrawingMode) {
            Box(modifier = Modifier.align(Alignment.Center)) {
                Canvas(modifier = Modifier.size(40.dp)) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val lineLen = 14f
                    val gap = 6f
                    val color = Color.White.copy(alpha = 0.9f)
                    drawLine(color, Offset(cx - lineLen, cy), Offset(cx - gap, cy), strokeWidth = 2f)
                    drawLine(color, Offset(cx + gap, cy), Offset(cx + lineLen, cy), strokeWidth = 2f)
                    drawLine(color, Offset(cx, cy - lineLen), Offset(cx, cy - gap), strokeWidth = 2f)
                    drawLine(color, Offset(cx, cy + gap), Offset(cx, cy + lineLen), strokeWidth = 2f)
                    drawCircle(color, 2f, Offset(cx, cy))
                }
            }
        }

        // ── Floating Top Bar ──
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                    )
                )
                .padding(top = 16.dp, bottom = 24.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("BASE MAP", style = MaterialTheme.typography.titleMedium,
                        color = Color.White, fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .background(PitwisePrimary.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(uiState.mapType.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }

                // Export button (only if annotations exist)
                if (uiState.annotations.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.toggleExportMenu() },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(Icons.Default.FileDownload, "Export", tint = Color.White)
                    }
                } else {
                    Box(modifier = Modifier.size(48.dp))
                }
            }
        }

        // ── Live Measurement HUD ──
        AnimatedVisibility(
            visible = isDrawingMode && uiState.collectedPoints.size >= 2,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 100.dp),
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            Box(
                modifier = Modifier
                    .shadow(8.dp, RoundedCornerShape(12.dp))
                    .background(PitwiseSurface.copy(alpha = 0.95f), RoundedCornerShape(12.dp))
                    .border(1.dp, PitwiseBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (uiState.liveDistance > 0) {
                        val distText = if (uiState.liveDistance >= 1000)
                            "%.2f km".format(uiState.liveDistance / 1000)
                        else "%.1f m".format(uiState.liveDistance)
                        Text("Jarak: $distText",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFFFEB3B), fontWeight = FontWeight.Bold)
                    }
                    if (uiState.liveArea > 0) {
                        val areaText = if (uiState.liveArea >= 10000)
                            "%.4f ha".format(uiState.liveArea / 10000)
                        else "%.1f m²".format(uiState.liveArea)
                        Text("Luas: $areaText",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    }
                    Text("${uiState.collectedPoints.size} titik",
                        style = MaterialTheme.typography.labelSmall,
                        color = PitwiseGray400)
                }
            }
        }

        // ── Add Point FAB (left side to avoid overlap) ──
        if (isDrawingMode) {
            FloatingActionButton(
                onClick = { viewModel.addPointAtCenter() },
                containerColor = when (uiState.currentMode) {
                    MapMode.MEASURE -> Color(0xFFFFEB3B)
                    MapMode.PLOT -> Color(0xFFFF9800)
                    else -> PitwisePrimary
                },
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
                    .size(56.dp)
            ) {
                Icon(Icons.Default.Add, "Add Point", modifier = Modifier.size(28.dp))
            }
        }

        // ── Right Side Controls ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Map Type
            FloatingActionButton(
                onClick = { viewModel.toggleTypeSelector() },
                containerColor = PitwiseSurface, contentColor = Color.White,
                shape = CircleShape, modifier = Modifier.size(44.dp)
            ) { Icon(Icons.Default.Layers, "Map Type", modifier = Modifier.size(22.dp)) }

            // Zoom In
            FloatingActionButton(
                onClick = { viewModel.zoomIn() },
                containerColor = PitwiseSurface, contentColor = Color.White,
                shape = CircleShape, modifier = Modifier.size(40.dp)
            ) { Icon(Icons.Default.Add, "Zoom In", modifier = Modifier.size(20.dp)) }

            // Zoom Out
            FloatingActionButton(
                onClick = { viewModel.zoomOut() },
                containerColor = PitwiseSurface, contentColor = Color.White,
                shape = CircleShape, modifier = Modifier.size(40.dp)
            ) { Icon(Icons.Default.Remove, "Zoom Out", modifier = Modifier.size(20.dp)) }

            // GPS Center
            FloatingActionButton(
                onClick = { viewModel.centerOnGps() },
                containerColor = if (uiState.gpsLat != null) PitwisePrimary else PitwiseSurface,
                contentColor = if (uiState.gpsLat != null) Color.Black else Color.White,
                shape = CircleShape, modifier = Modifier.size(40.dp)
            ) { Icon(Icons.Default.MyLocation, "GPS", modifier = Modifier.size(20.dp)) }

            // Send to Calculator (MEASURE mode only)
            if (uiState.currentMode == MapMode.MEASURE && uiState.collectedPoints.size >= 2) {
                FloatingActionButton(
                    onClick = {
                        val result = viewModel.getMeasurementForCalc()
                        if (result != null) onSendToCalculator(result.first, result.second)
                    },
                    containerColor = Color(0xFF4CAF50), contentColor = Color.White,
                    shape = CircleShape, modifier = Modifier.size(40.dp)
                ) { Icon(Icons.AutoMirrored.Filled.Send, "Send", modifier = Modifier.size(20.dp)) }
            }
        }

        // ── Map Type Selector Popup ──
        AnimatedVisibility(
            visible = uiState.showTypeSelector,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 68.dp, bottom = 240.dp),
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .shadow(8.dp, RoundedCornerShape(16.dp))
                    .background(PitwiseSurface, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFF3A3D41), RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BaseMapType.entries.forEach { type ->
                    val isSelected = uiState.mapType == type
                    val icon = when (type) {
                        BaseMapType.SATELLITE -> Icons.Default.Satellite
                        BaseMapType.STREET -> Icons.Default.Map
                        BaseMapType.TOPO -> Icons.Default.Terrain
                        BaseMapType.HYBRID -> Icons.Default.Public
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) PitwisePrimary.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { viewModel.setMapType(type) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .width(150.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(icon, type.label,
                            tint = if (isSelected) PitwisePrimary else PitwiseGray400,
                            modifier = Modifier.size(22.dp))
                        Text(type.label, style = MaterialTheme.typography.bodyMedium,
                            color = if (isSelected) PitwisePrimary else Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }

        // ── Export Menu Popup ──
        AnimatedVisibility(
            visible = uiState.showExportMenu,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 80.dp, end = 16.dp),
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .shadow(8.dp, RoundedCornerShape(12.dp))
                    .background(PitwiseSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, PitwiseBorder, RoundedCornerShape(12.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "KML" to ExportFormat.KML,
                    "KMZ" to ExportFormat.KMZ,
                    "DXF" to ExportFormat.DXF
                ).forEach { (label, format) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.exportAllAnnotations(format) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .width(100.dp)
                    ) {
                        Text("Export $label", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    }
                }
            }
        }

        // ── Bottom Section: Mode Toolbar + Coordinate Bar ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            // Mode Toolbar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .background(PitwiseSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, PitwiseBorder, RoundedCornerShape(12.dp))
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Active drawing actions
                val hasActiveDrawing = isDrawingMode && uiState.collectedPoints.isNotEmpty()
                if (hasActiveDrawing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.undoLastPoint() }, Modifier.size(40.dp)) {
                            Icon(Icons.Default.Undo, "Undo", tint = PitwiseGray400)
                        }
                        IconButton(onClick = { viewModel.finishDrawing() }, Modifier.size(40.dp)) {
                            Icon(Icons.Default.Check, "Save", tint = Color(0xFF4CAF50))
                        }
                        IconButton(onClick = { viewModel.cancelDrawing() }, Modifier.size(40.dp)) {
                            Icon(Icons.Default.Close, "Cancel", tint = Color(0xFFCF4444))
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = PitwiseBorder)
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Plot sub-mode
                if (uiState.currentMode == MapMode.PLOT) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SubModeButton("Point", Icons.Default.Place,
                            uiState.plotSubMode == PlotSubMode.POINT) { viewModel.setPlotSubMode(PlotSubMode.POINT) }
                        Spacer(Modifier.width(8.dp))
                        SubModeButton("Line", Icons.Default.Timeline,
                            uiState.plotSubMode == PlotSubMode.LINE) { viewModel.setPlotSubMode(PlotSubMode.LINE) }
                        Spacer(Modifier.width(8.dp))
                        SubModeButton("Polygon", Icons.Default.CropFree,
                            uiState.plotSubMode == PlotSubMode.POLYGON) { viewModel.setPlotSubMode(PlotSubMode.POLYGON) }
                    }
                }

                // Measure sub-mode
                if (uiState.currentMode == MapMode.MEASURE) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SubModeButton("Jarak", Icons.Default.Straighten,
                            uiState.measureSubMode == MeasureSubMode.DISTANCE) { viewModel.setMeasureSubMode(MeasureSubMode.DISTANCE) }
                        Spacer(Modifier.width(16.dp))
                        SubModeButton("Luas", Icons.Default.CropFree,
                            uiState.measureSubMode == MeasureSubMode.AREA) { viewModel.setMeasureSubMode(MeasureSubMode.AREA) }
                    }
                }

                // Mode buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModeBtn(Icons.Default.PanTool, "View",
                        uiState.currentMode == MapMode.VIEW, PitwisePrimary) { viewModel.setMode(MapMode.VIEW) }
                    ModeBtn(Icons.Default.Edit, "Plot",
                        uiState.currentMode == MapMode.PLOT, Color(0xFFFF9800)) { viewModel.setMode(MapMode.PLOT) }
                    ModeBtn(Icons.Default.Straighten, "Measure",
                        uiState.currentMode == MapMode.MEASURE, Color(0xFFFFEB3B)) { viewModel.setMode(MapMode.MEASURE) }
                }

                // Mode subtitle
                val subtitle = when (uiState.currentMode) {
                    MapMode.VIEW -> "Tap to see coordinates"
                    MapMode.PLOT -> "Tap + to add point"
                    MapMode.MEASURE -> "Tap + to add measurement point"
                    else -> ""
                }
                if (subtitle.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(subtitle, style = MaterialTheme.typography.labelSmall,
                        color = PitwiseGray400.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Coordinate Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PitwiseSurface.copy(alpha = 0.95f))
                    .clickable { viewModel.cycleCoordFormat() }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = viewModel.formatCoordinate(uiState.centerLat, uiState.centerLng),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .background(PitwisePrimary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(uiState.coordFormat.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = PitwisePrimary, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                        Box(
                            modifier = Modifier
                                .background(PitwisePrimary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Z${uiState.zoom}",
                                style = MaterialTheme.typography.labelSmall,
                                color = PitwisePrimary, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 180.dp)
        )

        // ── Annotation Detail Sheet ──
        uiState.selectedAnnotation?.let { annotation ->
            AnnotationDetailPanel(
                annotation = annotation,
                viewModel = viewModel,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            )
        }
    }
}

// ════════════════════════════════════════════════════
// Helper Composables
// ════════════════════════════════════════════════════

@Composable
private fun ModeBtn(
    icon: ImageVector, label: String, isActive: Boolean,
    activeColor: Color, onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(icon, label,
            tint = if (isActive) activeColor else PitwiseGray400,
            modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = if (isActive) activeColor else PitwiseGray400,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun SubModeButton(
    label: String, icon: ImageVector, isActive: Boolean, onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) PitwisePrimary.copy(alpha = 0.15f) else Color.Transparent)
            .border(
                1.dp,
                if (isActive) PitwisePrimary.copy(alpha = 0.5f) else PitwiseBorder,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, label,
            tint = if (isActive) PitwisePrimary else PitwiseGray400,
            modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = if (isActive) PitwisePrimary else PitwiseGray400,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal)
    }
}

// ════════════════════════════════════════════════════
// Annotation Detail Panel (bottom sheet style)
// ════════════════════════════════════════════════════

@Composable
private fun AnnotationDetailPanel(
    annotation: MapAnnotation,
    viewModel: BaseMapViewModel,
    modifier: Modifier = Modifier
) {
    var editingName by remember(annotation.id) { mutableStateOf(false) }
    var nameText by remember(annotation.id) { mutableStateOf(annotation.name) }
    var editingDesc by remember(annotation.id) { mutableStateOf(false) }
    var descText by remember(annotation.id) { mutableStateOf(annotation.description) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }

    val colors = listOf(
        "#E53935" to "Merah",
        "#1E88E5" to "Biru",
        "#43A047" to "Hijau",
        "#FB8C00" to "Jingga",
        "#8E24AA" to "Ungu",
        "#FDD835" to "Kuning",
        "#00897B" to "Teal",
        "#D81B60" to "Pink",
        "#3949AB" to "Indigo",
        "#757575" to "Abu-abu"
    )

    val typeColor = when (annotation.type) {
        "POINT" -> Color(0xFF2196F3)
        "LINE" -> Color(0xFFFF9800)
        "POLYGON" -> Color(0xFF00E5FF)
        else -> PitwiseGray400
    }

    Column(
        modifier = modifier
            .background(
                PitwiseSurface,
                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .border(
                1.dp, PitwiseBorder,
                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .padding(16.dp)
    ) {
        // Drag Handle
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(40.dp)
                .height(4.dp)
                .background(PitwiseGray400, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Header: Type badge + Name + Close
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Type badge
                Box(
                    modifier = Modifier
                        .background(typeColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        annotation.type,
                        style = MaterialTheme.typography.labelSmall,
                        color = typeColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))

                // Name (editable)
                if (editingName) {
                    androidx.compose.material3.OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        modifier = Modifier.weight(1f).height(48.dp),
                        textStyle = MaterialTheme.typography.titleMedium.copy(color = Color.White),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                editingName = false
                                viewModel.updateAnnotationName(annotation, nameText)
                            }) {
                                Icon(Icons.Default.Check, "Save", tint = Color(0xFF4CAF50))
                            }
                        }
                    )
                } else {
                    Text(
                        text = annotation.name.ifEmpty { "Unnamed" },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { editingName = true }
                    )
                }
            }
            IconButton(onClick = { viewModel.dismissAnnotationDetail() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Close", tint = PitwiseGray400)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = PitwiseBorder)
        Spacer(modifier = Modifier.height(8.dp))

        // ── Color Picker ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Warna", style = MaterialTheme.typography.labelMedium, color = PitwiseGray400)
            Row(
                modifier = Modifier
                    .clickable { showColorPicker = !showColorPicker }
                    .background(PitwiseBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val currentColor = try { Color(android.graphics.Color.parseColor(annotation.color)) } catch (_: Exception) { Color(0xFF00E5FF) }
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(currentColor, CircleShape)
                )
                Text(
                    colors.firstOrNull { it.first == annotation.color }?.second ?: annotation.color,
                    style = MaterialTheme.typography.labelSmall, color = Color.White
                )
                Icon(Icons.Default.ArrowDropDown, null, tint = PitwisePrimary, modifier = Modifier.size(16.dp))
            }
        }

        // Color grid
        if (showColorPicker) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                colors.forEach { (hex, _) ->
                    val c = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.White }
                    val isSelected = annotation.color == hex
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .border(
                                if (isSelected) 2.dp else 0.dp,
                                if (isSelected) Color.White else Color.Transparent,
                                CircleShape
                            )
                            .padding(2.dp)
                            .background(c, CircleShape)
                            .clickable {
                                viewModel.updateAnnotationColor(annotation, hex)
                                showColorPicker = false
                            }
                    )
                }
            }
        }

        // ── Measurements ──
        if (annotation.type == "LINE" && annotation.distance > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Jarak", style = MaterialTheme.typography.labelMedium, color = PitwiseGray400)
                Text("%.2f m".format(annotation.distance), style = MaterialTheme.typography.bodyMedium, color = Color.White)
            }
        }
        if (annotation.type == "POLYGON") {
            if (annotation.area > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Luas", style = MaterialTheme.typography.labelMedium, color = PitwiseGray400)
                    Text("%.2f m²".format(annotation.area), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
            }
            if (annotation.distance > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Keliling", style = MaterialTheme.typography.labelMedium, color = PitwiseGray400)
                    Text("%.2f m".format(annotation.distance), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
            }
        }

        // ── Description ──
        Spacer(modifier = Modifier.height(8.dp))
        if (editingDesc) {
            androidx.compose.material3.OutlinedTextField(
                value = descText,
                onValueChange = { descText = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                label = { Text("Deskripsi", color = PitwiseGray400) },
                minLines = 2, maxLines = 4,
                trailingIcon = {
                    IconButton(onClick = {
                        editingDesc = false
                        viewModel.updateAnnotationDescription(annotation, descText)
                    }) {
                        Icon(Icons.Default.Check, "Save", tint = Color(0xFF4CAF50))
                    }
                }
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editingDesc = true }
                    .background(PitwiseBorder.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Edit, "Edit", tint = PitwiseGray400, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    annotation.description.ifEmpty { "Tambah deskripsi..." },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (annotation.description.isEmpty()) PitwiseGray400 else Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = PitwiseBorder)
        Spacer(modifier = Modifier.height(8.dp))

        // ── Action Buttons ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Export
            Box {
                TextButton(onClick = { showExportMenu = !showExportMenu }) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export", style = MaterialTheme.typography.labelMedium)
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = showExportMenu,
                    onDismissRequest = { showExportMenu = false }
                ) {
                    listOf("KML", "KMZ", "DXF").forEach { fmt ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(fmt) },
                            onClick = {
                                showExportMenu = false
                                val format = when (fmt) {
                                    "KML" -> com.example.pitwise.domain.map.ExportFormat.KML
                                    "KMZ" -> com.example.pitwise.domain.map.ExportFormat.KMZ
                                    else -> com.example.pitwise.domain.map.ExportFormat.DXF
                                }
                                viewModel.exportAnnotation(annotation, format)
                            }
                        )
                    }
                }
            }

            // Delete
            TextButton(
                onClick = { viewModel.deleteAnnotation(annotation) },
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF5350))
            ) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Hapus", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
