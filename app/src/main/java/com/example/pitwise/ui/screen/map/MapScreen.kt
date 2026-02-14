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
import com.example.pitwise.domain.map.MeasureSubMode
import com.example.pitwise.ui.theme.PitwiseBorder
import com.example.pitwise.ui.theme.PitwiseGray400
import com.example.pitwise.ui.theme.PitwisePrimary
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

    // Zoom All on trigger
    LaunchedEffect(uiState.zoomAllTrigger) {
        if (uiState.zoomAllTrigger > 0 && uiState.canvasWidth > 0f) {
            viewModel.executeZoomAll()
        }
    }

    // Center on GPS trigger
    LaunchedEffect(uiState.centerOnGpsTrigger) {
        if (uiState.centerOnGpsTrigger > 0) {
            val gpsX = uiState.gpsLocalX
            val gpsY = uiState.gpsLocalY
            if (gpsX != null && gpsY != null) {
                val target = viewModel.transformEngine.worldToScreen(gpsX, gpsY)
                val dx = uiState.canvasWidth / 2f - target.x
                val dy = uiState.canvasHeight / 2f - target.y
                viewModel.onPan(dx, dy)
            }
        }
    }

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

            // ── Map Canvas with gesture handling ──
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Transform gestures: pinch zoom with focal point + pan
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            viewModel.onZoom(zoom, centroid.x, centroid.y)
                            viewModel.onPan(pan.x, pan.y)
                        }
                    }
                    // Tap gestures: single tap for map interaction, double-tap for zoom
                    .pointerInput(uiState.currentMode) {
                        detectTapGestures(
                            onTap = { tapOffset ->
                                viewModel.onMapTap(tapOffset.x, tapOffset.y)
                            },
                            onDoubleTap = { tapOffset ->
                                viewModel.onDoubleTapZoom(tapOffset.x, tapOffset.y)
                            }
                        )
                    }
            ) {
                MapRenderer(
                    transformEngine = viewModel.transformEngine,
                    pdfBitmap = uiState.pdfBitmap,
                    dxfFile = uiState.dxfFile,
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
                    gpsAccuracy = uiState.gpsAccuracy
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
                            "X: ${"%.1f".format(uiState.tapLocalX ?: 0.0)}  Y: ${"%.1f".format(uiState.tapLocalY ?: 0.0)}",
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
                        Icon(Icons.Default.Place, null, tint = Color(0xFFFF5722), modifier = Modifier.size(20.dp))
                        Column {
                            Text(
                                "X: ${"%.2f".format(marker.x)}  Y: ${"%.2f".format(marker.y)}",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (marker.z != null) {
                                Text(
                                    "Elevasi: ${"%.2f".format(marker.z)} m",
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
                    .padding(end = 16.dp, bottom = 120.dp),
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
                    .padding(bottom = 48.dp)
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
            .fillMaxWidth()
            .background(PitwiseSurface, RoundedCornerShape(16.dp))
            .border(1.dp, PitwiseBorder, RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Active drawing actions (Undo/Finish/Cancel) ──
        val isActiveDrawing = (currentMode == MapMode.PLOT || currentMode == MapMode.MEASURE) && pointCount > 0
        if (isActiveDrawing) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
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
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
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
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(
                if (isActive) activeColor.copy(alpha = 0.15f) else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            icon, label,
            tint = if (isActive) activeColor else PitwiseGray400,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
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
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .background(
                if (isActive) Color(0xFFFFEB3B).copy(alpha = 0.15f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                if (isActive) Color(0xFFFFEB3B).copy(alpha = 0.3f) else PitwiseBorder,
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon, label,
            tint = if (isActive) Color(0xFFFFEB3B) else PitwiseGray400,
            modifier = Modifier.size(16.dp)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) Color(0xFFFFEB3B) else PitwiseGray400,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
    }
}
