package com.example.pitwise.ui.screen.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pitwise.domain.map.MapMode
import com.example.pitwise.domain.map.MapTransformEngine
import com.example.pitwise.domain.map.MeasureSubMode
import com.example.pitwise.ui.theme.PitwiseBorder
import com.example.pitwise.ui.theme.PitwiseGray400
import com.example.pitwise.ui.theme.PitwisePrimary
import com.example.pitwise.domain.geopdf.GeoPdfDebugInfo
import com.example.pitwise.ui.theme.PitwiseSurface

// ════════════════════════════════════════════════════
// Avenza-style Map Screen
// ════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    mapId: Long,
    onBack: () -> Unit = {},
    onSendToCalculator: (type: String, value: Double) -> Unit = { _, _ -> },
    viewModel: MapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Permission denied state
    var permissionDenied by remember { mutableStateOf(false) }

    // Mode badge color
    val modeColor = when (uiState.currentMode) {
        MapMode.VIEW -> PitwisePrimary
        MapMode.PLOT -> Color(0xFFFF9800)
        MapMode.MEASURE -> Color(0xFFFFEB3B)
        MapMode.ID_POINT -> Color(0xFFFF5722)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            uiState.mapEntry?.name ?: "Peta",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (uiState.mapEntry != null) {
                                Text(
                                    uiState.mapEntry!!.type,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PitwiseGray400
                                )
                            }
                            // Mode badge
                            Box(
                                modifier = Modifier
                                    .background(modeColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    uiState.currentMode.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = modeColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    // Zoom All
                    IconButton(onClick = { viewModel.triggerZoomAll() }) {
                        Icon(
                            Icons.Default.ZoomOutMap,
                            "Zoom All",
                            tint = PitwiseGray400
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PitwiseSurface
                )
            )
        },
        containerColor = Color(0xFF1A1D21)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .onSizeChanged { size ->
                    viewModel.updateCanvasSize(size.width.toFloat(), size.height.toFloat())
                }
        ) {
            // ── GPS Permission ──
            val context = LocalContext.current
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
                if (!uiState.isGpsTracking) {
                    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
                        viewModel.toggleGpsTracking()
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                }
            }

            // ── Permission Denied Banner ──
            if (permissionDenied) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2D1B00))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "GPS permission required",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFFF9800)
                        )
                        Text(
                            "ENABLE GPS",
                            style = MaterialTheme.typography.labelMedium,
                            color = PitwisePrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                                .padding(4.dp)
                        )
                    }
                }
            }

            // ── Local Transform State (Hardware Acceleration Friendly) ──
            var scale by remember { mutableStateOf(1f) }
            var offsetX by remember { mutableStateOf(0f) }
            var offsetY by remember { mutableStateOf(0f) }

            // Sync ViewModel initial state or "Zoom All" triggers
            // Sync ViewModel state triggers (e.g. Zoom All or Initial Load)
            // We compare only if the ViewModel state explicitly changes (triggers)
            // But since we decoupled, uiState.scale/offset ONLY changes when ViewModel wants to force a view (ZoomAll).
            // So we can safely apply it.
            LaunchedEffect(uiState.scale, uiState.offsetX, uiState.offsetY) {
                // Determine if this is a "force update" from ViewModel.
                // Since we don't push local state TO ViewModel, any change in uiState.scale/offset
                // MUST be an external command (load, zoom all).
                // However, we must ensure we don't reset if uiState is just "default" and we have panned.
                // But uiState doesn't change unless we call update.
                // Note: initialization gives 1f, 0f, 0f.
                // If we pan, local becomes 1.1, 10, 10. uiState stays 1f, 0f, 0f.
                // LaunchedEffect DOES NOT run.
                // If we triggers Zoom All -> uiState becomes 0.5, 100, 100.
                // LaunchedEffect runs. We update local. Correct.
                
                // One edge case: If Zoom All result HAPPENS to match previous uiState?
                // Then LaunchedEffect won't run. But that's fine, we are already there? 
                // No, local state might be different. 
                // But Zoom All usually changes state.
                
                // To be safe, we rely on the fact that uiState ONLY changes on events.
                if (uiState.scale != scale || uiState.offsetX != offsetX || uiState.offsetY != offsetY) {
                     scale = uiState.scale
                     offsetX = uiState.offsetX
                     offsetY = uiState.offsetY
                }
            }

            // Zoom All on trigger
            // Zoom All on trigger or when canvas size becomes available for the first time
            LaunchedEffect(uiState.zoomAllTrigger, uiState.canvasWidth, uiState.canvasHeight) {
                if (uiState.zoomAllTrigger > 0 && uiState.canvasWidth > 0f && uiState.canvasHeight > 0f) {
                    viewModel.executeZoomAll()
                }
            }

            // Center on GPS trigger
            LaunchedEffect(uiState.centerOnGpsTrigger) {
                if (uiState.centerOnGpsTrigger > 0) {
                    val gpsX = uiState.gpsLocalX
                    val gpsY = uiState.gpsLocalY
                    if (gpsX != null && gpsY != null) {
                        // Calculate target screen position for GPS point using LOCAL state
                        // We want GPS to be at center (width/2, height/2)
                        // Current screen pos of GPS:
                        // screenX = (gpsX * scale) + offsetX
                        // screenY = (gpsY * scale) + offsetY (assuming no flipY or handled)
                        
                        // We want newOffsetX such that:
                        // center.x = (gpsX * scale) + newOffsetX
                        // newOffsetX = center.x - (gpsX * scale)
                        
                        // Handling flipY:
                        val isFlipY = viewModel.transformEngine.flipY
                        val vy = if (isFlipY) -gpsY else gpsY
                        
                        val targetScale = scale // Keep current zoom
                        
                        val cx = uiState.canvasWidth / 2f
                        val cy = uiState.canvasHeight / 2f
                        
                        offsetX = cx - (gpsX.toFloat() * targetScale)
                        offsetY = cy - (vy.toFloat() * targetScale)
                        
                        // Note: We update local state directly. We do NOT call viewModel.onPan.
                    }
                }
            }

            // ── Map Canvas with gesture handling ──
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val oldScale = scale
                            val newScale = (scale * zoom).coerceIn(MapTransformEngine.MIN_SCALE, MapTransformEngine.MAX_SCALE)
                            val scaleChange = newScale / oldScale

                            // Zoom around centroid
                            offsetX = centroid.x - (centroid.x - offsetX) * scaleChange + pan.x
                            offsetY = centroid.y - (centroid.y - offsetY) * scaleChange + pan.y
                            scale = newScale

                            // Update ViewModel *lazily* or on gesture end if needed,
                            // but for now we keep it local for performance.
                            // If we need coordinate updates, we calculate them using local state.
                        }
                    }
                    .pointerInput(uiState.currentMode) {
                        detectTapGestures(
                            onTap = { tapOffset ->
                                // Convert to World Coordinates locally
                                val wx = (tapOffset.x - offsetX) / scale
                                val rawY = (tapOffset.y - offsetY) / scale
                                val wy = if (viewModel.transformEngine.flipY) -rawY else rawY
                                viewModel.onMapTap(worldX = wx.toDouble(), worldY = wy.toDouble())
                            },
                            onDoubleTap = { tapOffset ->
                                val zoomFactor = 1.5f
                                val newScale = (scale * zoomFactor).coerceIn(MapTransformEngine.MIN_SCALE, MapTransformEngine.MAX_SCALE)
                                val scaleChange = newScale / scale
                                offsetX = tapOffset.x - (tapOffset.x - offsetX) * scaleChange
                                offsetY = tapOffset.y - (tapOffset.y - offsetY) * scaleChange
                                scale = newScale
                            }
                        )
                    }
            ) {
                MapRenderer(
                    modifier = Modifier.fillMaxSize(),
                    scale = scale,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    pdfBitmap = uiState.pdfBitmap,
                    dxfModel = uiState.dxfModel,
                    showPdfLayer = uiState.showPdfLayer,
                    showDxfLayer = uiState.showDxfLayer,
                    showGpsLayer = uiState.showGpsLayer,
                    annotations = uiState.annotations,
                    collectedPoints = uiState.collectedPoints,
                    currentMode = uiState.currentMode,
                    measureSubMode = uiState.measureSubMode,
                    idPointMarker = uiState.idPointMarker,
                    gpsPosition = if (uiState.gpsLocalX != null && uiState.gpsLocalY != null) {
                        Pair(uiState.gpsLocalX!!, uiState.gpsLocalY!!)
                    } else null,
                    gpsHeading = uiState.gpsHeading,
                    gpsAccuracy = uiState.gpsAccuracy,
                    flipY = viewModel.transformEngine.flipY,
                    isSnapped = uiState.isSnapped
                )
            }

            // ── Debug Overlay ──
            // ── Debug Overlay ──
            if (com.example.pitwise.domain.debug.DebugManager.shouldShowDebugOverlay()) {
                MapDebugOverlay(
                    info = uiState.geoPdfDebugInfo,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 80.dp, start = 16.dp)
                )
            }

            // ── Loading ──
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Memuat peta...",
                        color = PitwiseGray400,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // ── Error ──
            if (uiState.errorMessage != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        uiState.errorMessage!!,
                        color = Color(0xFFCF4444),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            }

            // ── Tap Tooltip (VIEW mode) ──
            AnimatedVisibility(
                visible = uiState.showTapTooltip,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .background(PitwiseSurface, RoundedCornerShape(12.dp))
                        .border(1.dp, PitwiseBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "X: ${"%.1f".format(uiState.tapProjectedX ?: 0.0)}  Y: ${"%.1f".format(uiState.tapProjectedY ?: 0.0)}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (uiState.tapZ != null) {
                            Text(
                                "Z (Elevasi): ${"%.2f".format(uiState.tapZ)} m",
                                color = PitwisePrimary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.dismissTapTooltip() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, "Close", tint = PitwiseGray400, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // ── Live Measurement Panel (PLOT / MEASURE modes) ──
            val measurement = uiState.liveMeasurement
            val showMeasurePanel = (uiState.currentMode == MapMode.PLOT || uiState.currentMode == MapMode.MEASURE) &&
                    measurement.pointCount > 0
            AnimatedVisibility(
                visible = showMeasurePanel,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .background(PitwiseSurface.copy(alpha = 0.95f), RoundedCornerShape(12.dp))
                        .border(1.dp, PitwiseBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Distance
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            "${"%.2f".format(measurement.distance)} m",
                            color = modeColor,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text("Jarak", color = PitwiseGray400, style = MaterialTheme.typography.labelSmall)
                    }

                    // Area (show if >=3 points and area mode)
                    val isAreaMode = (uiState.currentMode == MapMode.PLOT) ||
                            (uiState.currentMode == MapMode.MEASURE && uiState.measureSubMode == MeasureSubMode.AREA)
                    if (isAreaMode && measurement.pointCount >= 3) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                "${"%.2f".format(measurement.area)} m²",
                                color = Color(0xFF4CAF50),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text("Luas", color = PitwiseGray400, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    // Points count
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            "${measurement.pointCount}",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text("Titik", color = PitwiseGray400, style = MaterialTheme.typography.labelSmall)
                    }

                    // Send to Calculator button
                    if (uiState.currentMode == MapMode.MEASURE && measurement.pointCount >= 2) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val isArea = uiState.measureSubMode == MeasureSubMode.AREA && measurement.pointCount >= 3
                                val mType = if (isArea) "AREA" else "DISTANCE"
                                val mValue = if (isArea) measurement.area else measurement.distance
                                onSendToCalculator(mType, mValue)
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .background(PitwisePrimary.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Calculate,
                                contentDescription = "Send to Calculator",
                                tint = PitwisePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // ── ID Point Info Panel (ID_POINT mode) ──
            AnimatedVisibility(
                visible = uiState.currentMode == MapMode.ID_POINT && uiState.idPointMarker != null,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            ) {
                val marker = uiState.idPointMarker
                if (marker != null) {
                    Row(
                        modifier = Modifier
                            .background(PitwiseSurface, RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFFF5722).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Place, null, tint = Color(0xFFFF5722), modifier = Modifier.size(24.dp))
                            if (uiState.isSnapped) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(Color(0xFF4CAF50).copy(alpha = 0.3f), CircleShape)
                                )
                            }
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "X: ${"%.3f".format(marker.x)}  Y: ${"%.3f".format(marker.y)}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (uiState.isSnapped) {
                                  Box(
                                      modifier = Modifier
                                          .background(Color(0xFF4CAF50), RoundedCornerShape(4.dp))
                                          .padding(horizontal = 4.dp, vertical = 1.dp)
                                  ) {
                                      Text("SNAPPED", fontSize = 9.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                  }
                                }
                            }
                            if (marker.z != null) {
                                Text(
                                    "Z: ${"%.3f".format(marker.z)}",
                                    color = Color(0xFFFF5722),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(
                                    "Z tidak tersedia",
                                    color = PitwiseGray400,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }

            // ── Right-side FABs (GPS + Zoom All) ──
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // GPS Center
                SmallFloatingActionButton(
                    onClick = { viewModel.centerOnGps() },
                    containerColor = PitwiseSurface,
                    contentColor = if (uiState.isGpsTracking) Color(0xFF2196F3) else PitwiseGray400,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.MyLocation, "Center GPS", modifier = Modifier.size(20.dp))
                }
            }

            // ── Mode Toolbar (bottom, Avenza-style) ──
            ModeToolbar(
                currentMode = uiState.currentMode,
                measureSubMode = uiState.measureSubMode,
                pointCount = uiState.liveMeasurement.pointCount,
                onModeSelected = { viewModel.setMode(it) },
                onMeasureSubModeSelected = { viewModel.setMeasureSubMode(it) },
                onUndo = { viewModel.undoPoint() },
                onFinish = { viewModel.finishAndSave() },
                onCancel = {
                    viewModel.clearPoints()
                    viewModel.setMode(MapMode.VIEW)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 60.dp)
            )

            // ── Coordinate Bar (very bottom) ──
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(PitwiseSurface.copy(alpha = 0.95f))
                    .clickable { viewModel.cycleCoordinateFormat() }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.GpsFixed, null,
                            tint = if (uiState.isGpsTracking) Color(0xFF4CAF50) else PitwiseGray400,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            uiState.coordinateText.ifEmpty { "Menunggu GPS..." },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (uiState.coordinateText.isNotEmpty()) Color.White else PitwiseGray400,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(PitwisePrimary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            uiState.coordinateFormat.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = PitwisePrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════
// Mode Toolbar (Avenza-style floating bottom bar)
// ════════════════════════════════════════════════════

@Composable
private fun ModeToolbar(
    currentMode: MapMode,
    measureSubMode: MeasureSubMode,
    pointCount: Int,
    onModeSelected: (MapMode) -> Unit,
    onMeasureSubModeSelected: (MeasureSubMode) -> Unit,
    onUndo: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .wrapContentWidth()
            .background(PitwiseSurface, RoundedCornerShape(16.dp))
            .border(1.dp, PitwiseBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Active drawing actions (Undo/Finish/Cancel) ──
        val isActiveDrawing = (currentMode == MapMode.PLOT || currentMode == MapMode.MEASURE) && pointCount > 0
        if (isActiveDrawing) {
            Row(
                modifier = Modifier.wrapContentWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onUndo, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Undo, "Undo", tint = PitwiseGray400)
                }
                IconButton(onClick = onFinish, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Check, "Simpan", tint = Color(0xFF4CAF50))
                }
                IconButton(onClick = onCancel, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, "Batal", tint = Color(0xFFCF4444))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = PitwiseBorder)
            Spacer(modifier = Modifier.height(6.dp))
        }

        // ── Measure sub-mode toggle (only when MEASURE mode active) ──
        if (currentMode == MapMode.MEASURE) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MeasureSubModeButton(
                    label = "Jarak",
                    icon = Icons.Default.Straighten,
                    isActive = measureSubMode == MeasureSubMode.DISTANCE,
                    onClick = { onMeasureSubModeSelected(MeasureSubMode.DISTANCE) }
                )
                Spacer(modifier = Modifier.width(16.dp))
                MeasureSubModeButton(
                    label = "Luas",
                    icon = Icons.Default.CropFree,
                    isActive = measureSubMode == MeasureSubMode.AREA,
                    onClick = { onMeasureSubModeSelected(MeasureSubMode.AREA) }
                )
            }
        }

        // ── Mode buttons ──
        Row(
            modifier = Modifier.wrapContentWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModeButton(
                icon = Icons.Default.PanTool,
                label = "View",
                isActive = currentMode == MapMode.VIEW,
                activeColor = PitwisePrimary,
                onClick = { onModeSelected(MapMode.VIEW) }
            )
            ModeButton(
                icon = Icons.Default.Edit,
                label = "Plot",
                isActive = currentMode == MapMode.PLOT,
                activeColor = Color(0xFFFF9800),
                onClick = { onModeSelected(MapMode.PLOT) }
            )
            ModeButton(
                icon = Icons.Default.Straighten,
                label = "Measure",
                isActive = currentMode == MapMode.MEASURE,
                activeColor = Color(0xFFFFEB3B),
                onClick = { onModeSelected(MapMode.MEASURE) }
            )
            ModeButton(
                icon = Icons.Default.Place,
                label = "ID Point",
                isActive = currentMode == MapMode.ID_POINT,
                activeColor = Color(0xFFFF5722),
                onClick = { onModeSelected(MapMode.ID_POINT) }
            )
        }
    }
}

// ════════════════════════════════════════════════════
// Components
// ════════════════════════════════════════════════════

@Composable
private fun ModeButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    if (isActive) activeColor.copy(alpha = 0.2f) else Color.Transparent,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                label,
                tint = if (isActive) activeColor else PitwiseGray400,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) activeColor else PitwiseGray400,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun MeasureSubModeButton(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isActive) Color(0xFFFFEB3B).copy(alpha = 0.2f) else Color.Transparent,
                RoundedCornerShape(20.dp)
            )
            .border(
                1.dp,
                if (isActive) Color(0xFFFFEB3B).copy(alpha = 0.5f) else PitwiseBorder,
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            icon,
            null,
            tint = if (isActive) Color(0xFFFFEB3B) else PitwiseGray400,
            modifier = Modifier.size(16.dp)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isActive) Color(0xFFFFEB3B) else PitwiseGray400,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
    }
}
