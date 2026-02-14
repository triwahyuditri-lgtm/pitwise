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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
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
import com.example.pitwise.domain.map.PlotSubMode
import com.example.pitwise.data.local.entity.MapAnnotation
import com.example.pitwise.domain.map.MapSerializationUtils
import com.example.pitwise.domain.map.SnapType
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

    // Mode badge color
    val modeColor = when (uiState.currentMode) {
        MapMode.VIEW -> PitwisePrimary
        MapMode.PLOT -> Color(0xFFFF9800)
        MapMode.MEASURE -> Color(0xFFFFEB3B)
        MapMode.ID_POINT -> Color(0xFFFF5722)
    }



    // Use a Box instead of Scaffold to allow full-screen map
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1D21))
    ) {
        // ── Map Canvas (Full Screen Layer) ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    viewModel.updateCanvasSize(size.width.toFloat(), size.height.toFloat())
                }
                // Transform gestures: pinch zoom with focal point + pan
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        // If Target Mode is active, user interaction breaks the lock (optional)
                        // Or we just allow panning which might fight with auto-center if GPS updates fast.
                        // Avenza allows panning but then it might snap back or disable "Follow".
                        // For now, let's keep it simple: panning is allowed.
                        viewModel.onZoom(zoom, centroid.x, centroid.y)
                        viewModel.onPan(pan.x, pan.y)
                    }
                }
                // Tap gestures
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
                gpsAccuracy = uiState.gpsAccuracy,
                selectedAnnotationId = uiState.selectedAnnotationId,
                idPointSnapped = uiState.idPointSnapped,
                idPointSnapType = uiState.idPointSnapType
            )
        }
        
        // ── Loading Layer ──
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text("Memuat peta...", color = Color.White)
            }
        }

        // ── Crosshair / Target Reticle (Center Screen) ──
        // Enhanced for Measure mode: bigger + yellow-tinted
        if (!uiState.isLoading) {
             val density = LocalDensity.current
             val isMeasureOrPlot = uiState.currentMode == MapMode.MEASURE || uiState.currentMode == MapMode.PLOT
             val armSize = with(density) { if (isMeasureOrPlot) 56.dp.toPx() else 40.dp.toPx() }
             val strokePx = with(density) { if (isMeasureOrPlot) 2.5f.dp.toPx() else 2.dp.toPx() }
             val crossColor = when (uiState.currentMode) {
                 MapMode.MEASURE -> Color(0xFFFFEB3B).copy(alpha = 0.9f)  // Yellow
                 MapMode.PLOT -> Color(0xFFFF9800).copy(alpha = 0.9f)     // Orange
                 else -> Color.White.copy(alpha = 0.8f)
             }
             val shadowColor = Color.Black.copy(alpha = 0.5f)
             
             Canvas(modifier = Modifier.align(Alignment.Center)) {
                 // Shadow
                 drawLine(color = shadowColor, start = Offset(-armSize / 2, 1f),
                     end = Offset(armSize / 2, 1f), strokeWidth = strokePx + 2f,
                     cap = androidx.compose.ui.graphics.StrokeCap.Round)
                 drawLine(color = shadowColor, start = Offset(1f, -armSize / 2),
                     end = Offset(1f, armSize / 2), strokeWidth = strokePx + 2f,
                     cap = androidx.compose.ui.graphics.StrokeCap.Round)
                 // Primary crosshair
                 drawLine(color = crossColor, start = Offset(-armSize / 2, 0f),
                     end = Offset(armSize / 2, 0f), strokeWidth = strokePx,
                     cap = androidx.compose.ui.graphics.StrokeCap.Round)
                 drawLine(color = crossColor, start = Offset(0f, -armSize / 2),
                     end = Offset(0f, armSize / 2), strokeWidth = strokePx,
                     cap = androidx.compose.ui.graphics.StrokeCap.Round)
                 // Center dot for measure/plot
                 if (isMeasureOrPlot) {
                     drawCircle(color = crossColor, radius = 4f, center = Offset.Zero)
                 }
             }
        }

        // ── "Add Point" FAB (below crosshair, for MEASURE/PLOT modes) ──
        if (uiState.currentMode == MapMode.MEASURE || uiState.currentMode == MapMode.PLOT) {
            val fabColor = when (uiState.currentMode) {
                MapMode.MEASURE -> Color(0xFFFFEB3B)
                else -> Color(0xFFFF9800)
            }
            FloatingActionButton(
                onClick = { viewModel.addCrosshairPoint() },
                containerColor = fabColor,
                contentColor = Color.Black,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 100.dp)  // Below crosshair
                    .height(48.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.Add, "Add Point", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Point", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        
        // ── Floating Top Bar (Gradient Header) ──
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.8f),
                            Color.Transparent
                        )
                    )
                )
                .padding(top = 16.dp, bottom = 24.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Back Button
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                
                // Title Info
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        uiState.mapEntry?.name ?: "Pitwise Map",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.shadow(4.dp)
                    )
                    // Mode Badge
                    Box(
                         modifier = Modifier
                             .padding(top = 4.dp)
                             .background(modeColor.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                             .padding(horizontal = 6.dp, vertical = 2.dp)
                     ) {
                         Text(
                             uiState.currentMode.name,
                             style = MaterialTheme.typography.labelSmall,
                             color = Color.Black,
                             fontWeight = FontWeight.Bold
                         )
                     }
                }
                
                // Right Actions
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Zoom All
                     IconButton(
                        onClick = { viewModel.triggerZoomAll() },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(Icons.Default.ZoomOutMap, "Zoom All", tint = Color.White)
                    }
                }
            }
        }

        // ── Right Side Controls (GPS Lock / Target Mode) ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Target Mode Toggle
            FloatingActionButton(
                onClick = { viewModel.toggleTargetMode() },
                containerColor = if (uiState.isTargetMode) PitwisePrimary else PitwiseSurface,
                contentColor = if (uiState.isTargetMode) Color.Black else Color.White,
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
               Icon(
                   if (uiState.isTargetMode) Icons.Default.GpsFixed else Icons.Default.MyLocation, 
                   "Target Mode",
                   modifier = Modifier.size(22.dp)
               )
            }
        }

        // ── Bottom Panel (Coordinates & Modes) ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            // Live Measurement HUD (above toolbar, only when MEASURE active)
            if (uiState.currentMode == MapMode.MEASURE && uiState.liveMeasurement.pointCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .background(Color(0xFF1E1E1E).copy(alpha = 0.92f), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFFFEB3B).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            if (uiState.measureSubMode == MeasureSubMode.AREA && uiState.liveMeasurement.area > 0) {
                                Text(
                                    text = "%.2f m\u00B2".format(uiState.liveMeasurement.area),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFF00E5FF),
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                                Text(
                                    text = "P: %.2f m".format(uiState.liveMeasurement.perimeter),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PitwiseGray400,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            } else {
                                Text(
                                    text = "%.2f m".format(uiState.liveMeasurement.distance),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFFFFEB3B),
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFFEB3B).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${uiState.liveMeasurement.pointCount} pts",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFEB3B),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Measure Finish Panel (Save / Clear choice)
            if (uiState.showMeasureFinishPanel) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .background(Color(0xFF1E1E1E).copy(alpha = 0.92f), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Save button
                    FloatingActionButton(
                        onClick = { viewModel.saveMeasurement() },
                        containerColor = Color(0xFF4CAF50),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Icon(Icons.Default.Check, "Save", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    // Clear button
                    FloatingActionButton(
                        onClick = { viewModel.clearMeasurement() },
                        containerColor = Color(0xFFCF4444),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Icon(Icons.Default.Close, "Clear", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            // ── Collapsible Mode Toolbar ──
            var toolbarExpanded by remember { mutableStateOf(true) }

            // Toggle chevron
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { toolbarExpanded = !toolbarExpanded }
                    .background(
                        PitwiseSurface.copy(alpha = 0.85f),
                        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                    )
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (toolbarExpanded)
                        Icons.Default.ArrowDropDown
                    else
                        Icons.Default.ArrowDropUp,
                    contentDescription = if (toolbarExpanded) "Hide toolbar" else "Show toolbar",
                    tint = PitwiseGray400,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = toolbarExpanded,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                ModeToolbar(
                    currentMode = uiState.currentMode,
                    isDxfMap = uiState.isDxfMap,
                    measureSubMode = uiState.measureSubMode,
                    plotSubMode = uiState.plotSubMode,
                    pointCount = uiState.liveMeasurement.pointCount,
                    onModeSelected = { viewModel.setMode(it) },
                    onMeasureSubModeSelected = { viewModel.setMeasureSubMode(it) },
                    onPlotSubModeSelected = { viewModel.setPlotSubMode(it) },
                    onUndo = { viewModel.undoPoint() },
                    onFinish = { viewModel.finishAndSave() },
                    onCancel = {
                        viewModel.clearPoints()
                        viewModel.setMode(MapMode.VIEW)
                    },
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            
            // Coordinate Info Panel (Fixed Bottom)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PitwiseSurface.copy(alpha = 0.95f))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .clickable { viewModel.cycleCoordinateFormat() }
            ) {
                Row(
                   modifier = Modifier.fillMaxWidth(),
                   verticalAlignment = Alignment.CenterVertically,
                   horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        // Compact horizontal coordinate display
                        if (uiState.coordinateZone.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${uiState.coordinateZone}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PitwiseGray400
                                )
                                Text(
                                    text = "  E: ${uiState.coordinateEasting}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                                Text(
                                    text = "  N: ${uiState.coordinateNorthing}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        } else {
                            // Fallback for Lat/Lng or simple format
                             Text(
                                text = uiState.coordinateText.ifEmpty { "Waiting for GPS..." },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (uiState.coordinateText.isNotEmpty()) Color.White else PitwiseGray400
                            )
                        }
                    }
                    
                    // Format Badge
                    Box(
                        modifier = Modifier
                            .background(PitwisePrimary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
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
        
         // ── GPS Permission Logic (Invisible but active) ──
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
                     .align(Alignment.TopCenter)
                     .padding(top = 80.dp) // Below header
                     .fillMaxWidth()
                     .background(Color(0xFF2D1B00))
                     .padding(16.dp)
             ) {
                 Row(
                     horizontalArrangement = Arrangement.SpaceBetween,
                     modifier = Modifier.fillMaxWidth()
                 ) {
                     Text("GPS Access Required", color = Color(0xFFFF9800))
                     Text("ENABLE", color = PitwisePrimary, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                          permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                     })
                 }
             }
         }
         
          // ── Tooltips ──
          // (Existing tooltip logic for Tap Info / Measure Panel...)
          // Copied from original but updated alignments if necessary.
          // Place them below the header
          
          // ... (Omitted to keep it clean, but I should probably include them or they will be lost)
          // Let's re-add them quickly.
          
           // ── Tap Tooltip / ID Point Inspector ──
            AnimatedVisibility(
                visible = uiState.showTapTooltip,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 90.dp)
            ) {
                if (uiState.currentMode == MapMode.ID_POINT) {
                    // ── ID Point Inspector Card ──
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .fillMaxWidth()
                            .background(
                                Color(0xFF1E1E1E).copy(alpha = 0.95f),
                                RoundedCornerShape(12.dp)
                            )
                            .border(
                                1.dp,
                                if (uiState.idPointSnapped) Color(0xFF4CAF50).copy(alpha = 0.5f)
                                else Color(0xFFFF5722).copy(alpha = 0.5f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(16.dp)
                    ) {
                        // Header: badge + close
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (uiState.idPointSnapped) Color(0xFF4CAF50).copy(alpha = 0.2f)
                                        else Color(0xFFFF5722).copy(alpha = 0.2f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (uiState.idPointSnapped) "✓ Snapped" else "○ Raw",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.idPointSnapped) Color(0xFF4CAF50) else Color(0xFFFF5722),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(onClick = { viewModel.dismissTapTooltip() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, "Close", tint = PitwiseGray400, modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        // Coordinates
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("X", style = MaterialTheme.typography.labelSmall, color = PitwiseGray400)
                                Text(
                                    "%.3f".format(uiState.tapLocalX ?: 0.0),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Y", style = MaterialTheme.typography.labelSmall, color = PitwiseGray400)
                                Text(
                                    "%.3f".format(uiState.tapLocalY ?: 0.0),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Z", style = MaterialTheme.typography.labelSmall, color = PitwiseGray400)
                                Text(
                                    uiState.tapZ?.let { "%.2f".format(it) } ?: "—",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (uiState.tapZ != null) Color(0xFF00E5FF) else PitwiseGray400,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                        // Layer name (if available)
                        if (!uiState.idPointLayer.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Layer: ", style = MaterialTheme.typography.labelSmall, color = PitwiseGray400)
                                Text(
                                    uiState.idPointLayer!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFFFEB3B),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    // ── Simple VIEW Coordinate Tooltip ──
                    Row(
                        modifier = Modifier
                            .background(PitwiseSurface, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("X: ${"%.1f".format(uiState.tapLocalX)} Y: ${"%.1f".format(uiState.tapLocalY)}", color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { viewModel.dismissTapTooltip() }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, "Close", tint = Color.Gray)
                        }
                    }
                }
            }

        // ── Annotation Detail Sheet ──
        if (uiState.showDetailSheet && uiState.selectedAnnotation != null) {
            AnnotationDetailSheet(
                annotation = uiState.selectedAnnotation!!,
                onDismiss = { viewModel.dismissDetailSheet() },
                onNameChanged = { name ->
                    viewModel.updateAnnotationDetails(uiState.selectedAnnotation!!.id, name = name)
                },
                onDescriptionChanged = { desc ->
                    viewModel.updateAnnotationDetails(uiState.selectedAnnotation!!.id, description = desc)
                },
                onDelete = { viewModel.deleteSelectedAnnotation() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            )
        }
    }

}

// ════════════════════════════════════════════════════
// Mode Toolbar (Avenza-style floating bottom bar)
// ════════════════════════════════════════════════════

@Composable
private fun ModeToolbar(
    currentMode: MapMode,
    isDxfMap: Boolean,
    measureSubMode: MeasureSubMode,
    plotSubMode: PlotSubMode,
    pointCount: Int,
    onModeSelected: (MapMode) -> Unit,
    onMeasureSubModeSelected: (MeasureSubMode) -> Unit,
    onPlotSubModeSelected: (PlotSubMode) -> Unit,
    onUndo: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .background(PitwiseSurface, RoundedCornerShape(12.dp))
            .border(1.dp, PitwiseBorder, RoundedCornerShape(12.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Active drawing actions (Undo/Finish/Cancel) — only for LINE/POLYGON collection ──
        val isActiveDrawing = when (currentMode) {
            MapMode.PLOT -> (plotSubMode == PlotSubMode.LINE || plotSubMode == PlotSubMode.POLYGON) && pointCount > 0
            MapMode.MEASURE -> pointCount > 0
            else -> false
        }
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
                    Icon(Icons.Default.Check, "Save", tint = Color(0xFF4CAF50))
                }
                IconButton(onClick = onCancel, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, "Cancel", tint = Color(0xFFCF4444))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = PitwiseBorder)
            Spacer(modifier = Modifier.height(6.dp))
        }

        // ── Plot sub-mode toggle (only when PLOT mode active on PDF) ──
        if (!isDxfMap && currentMode == MapMode.PLOT) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlotSubModeButton(
                    label = "Point",
                    icon = Icons.Default.Place,
                    isActive = plotSubMode == PlotSubMode.POINT,
                    onClick = { onPlotSubModeSelected(PlotSubMode.POINT) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                PlotSubModeButton(
                    label = "Line",
                    icon = Icons.Default.Timeline,
                    isActive = plotSubMode == PlotSubMode.LINE,
                    onClick = { onPlotSubModeSelected(PlotSubMode.LINE) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                PlotSubModeButton(
                    label = "Polygon",
                    icon = Icons.Default.CropFree,
                    isActive = plotSubMode == PlotSubMode.POLYGON,
                    onClick = { onPlotSubModeSelected(PlotSubMode.POLYGON) }
                )
            }
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

        // ── Mode buttons (conditional: DXF vs PDF) ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // View — always shown
            ModeButton(
                icon = Icons.Default.PanTool,
                label = "View",
                isActive = currentMode == MapMode.VIEW,
                activeColor = PitwisePrimary,
                onClick = { onModeSelected(MapMode.VIEW) }
            )
            if (!isDxfMap) {
                // PDF: Plot button
                ModeButton(
                    icon = Icons.Default.Edit,
                    label = "Plot",
                    isActive = currentMode == MapMode.PLOT,
                    activeColor = Color(0xFFFF9800),
                    onClick = { onModeSelected(MapMode.PLOT) }
                )
            }
            // Measure — always shown
            ModeButton(
                icon = Icons.Default.Straighten,
                label = "Measure",
                isActive = currentMode == MapMode.MEASURE,
                activeColor = Color(0xFFFFEB3B),
                onClick = { onModeSelected(MapMode.MEASURE) }
            )
            if (isDxfMap) {
                // DXF: ID Point button
                ModeButton(
                    icon = Icons.Default.Place,
                    label = "ID Point",
                    isActive = currentMode == MapMode.ID_POINT,
                    activeColor = Color(0xFF2196F3),
                    onClick = { onModeSelected(MapMode.ID_POINT) }
                )
            }
        }

        // ── Mode helper subtitle ──
        val subtitle = when {
            isDxfMap && currentMode == MapMode.VIEW -> "Pan & zoom CAD drawing"
            isDxfMap && currentMode == MapMode.MEASURE -> "Tap to snap & measure"
            isDxfMap && currentMode == MapMode.ID_POINT -> "Tap to inspect coordinate"
            currentMode == MapMode.VIEW -> "Tap to see coordinates"
            currentMode == MapMode.PLOT -> "Tap to create marker"
            currentMode == MapMode.MEASURE -> "Tap to add measurement point"
            else -> ""
        }
        if (subtitle.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = PitwiseGray400.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
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
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .background(
                if (isActive) activeColor.copy(alpha = 0.15f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            icon, label,
            tint = if (isActive) activeColor else PitwiseGray400,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) activeColor else PitwiseGray400,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            fontSize = 10.sp
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

@Composable
private fun PlotSubModeButton(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val plotColor = Color(0xFFFF9800)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .background(
                if (isActive) plotColor.copy(alpha = 0.15f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                if (isActive) plotColor.copy(alpha = 0.3f) else PitwiseBorder,
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon, label,
            tint = if (isActive) plotColor else PitwiseGray400,
            modifier = Modifier.size(16.dp)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) plotColor else PitwiseGray400,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ════════════════════════════════════════════════════
// Annotation Detail Sheet (Avenza-style)
// ════════════════════════════════════════════════════

@Composable
private fun AnnotationDetailSheet(
    annotation: MapAnnotation,
    onDismiss: () -> Unit,
    onNameChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingName by remember(annotation.id) { mutableStateOf(false) }
    var nameText by remember(annotation.id) { mutableStateOf(annotation.name) }
    var editingDesc by remember(annotation.id) { mutableStateOf(false) }
    var descText by remember(annotation.id) { mutableStateOf(annotation.description) }

    // Get coordinate text from first point
    val firstPoint = remember(annotation.pointsJson) {
        MapSerializationUtils.parseJsonToPoints(annotation.pointsJson).firstOrNull()
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

        // Header: Type badge + Name + Close button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Type badge
                val typeColor = when (annotation.type) {
                    "POINT" -> Color(0xFF2196F3)
                    "LINE" -> Color(0xFFFF9800)
                    "POLYGON" -> Color(0xFF00E5FF)
                    else -> PitwiseGray400
                }
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
                                onNameChanged(nameText)
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
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Close", tint = PitwiseGray400)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = PitwiseBorder)
        Spacer(modifier = Modifier.height(12.dp))

        // Coordinates
        if (firstPoint != null) {
            DetailRow(label = "X", value = "%.2f".format(firstPoint.x))
            DetailRow(label = "Y", value = "%.2f".format(firstPoint.y))
        }

        // Elevation
        if (annotation.elevation != null) {
            DetailRow(label = "Elevation", value = "%.1f m".format(annotation.elevation))
        }

        // Layer
        DetailRow(label = "Layer", value = annotation.layer)

        // Distance (for LINE)
        if (annotation.type == "LINE" && annotation.distance > 0) {
            DetailRow(label = "Distance", value = "%.2f m".format(annotation.distance))
        }

        // Area (for POLYGON)
        if (annotation.type == "POLYGON" && annotation.area > 0) {
            DetailRow(label = "Area", value = "%.2f m\u00B2".format(annotation.area))
            if (annotation.distance > 0) {
                DetailRow(label = "Perimeter", value = "%.2f m".format(annotation.distance))
            }
        }

        // Description
        Spacer(modifier = Modifier.height(8.dp))
        if (editingDesc) {
            androidx.compose.material3.OutlinedTextField(
                value = descText,
                onValueChange = { descText = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                label = { Text("Description", color = PitwiseGray400) },
                minLines = 2,
                maxLines = 4,
                trailingIcon = {
                    IconButton(onClick = {
                        editingDesc = false
                        onDescriptionChanged(descText)
                    }) {
                        Icon(Icons.Default.Check, "Save", tint = Color(0xFF4CAF50))
                    }
                }
            )
        } else {
            Text(
                text = annotation.description.ifEmpty { "Tap to add description..." },
                style = MaterialTheme.typography.bodySmall,
                color = if (annotation.description.isNotEmpty()) Color.White else PitwiseGray400,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editingDesc = true }
                    .padding(vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Created date
        val dateStr = remember(annotation.createdAt) {
            java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(annotation.createdAt))
        }
        Text(
            text = "Created: $dateStr",
            style = MaterialTheme.typography.labelSmall,
            color = PitwiseGray400
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Delete button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            androidx.compose.material3.TextButton(
                onClick = onDelete
            ) {
                Icon(
                    Icons.Default.Close,
                    "Delete",
                    tint = Color(0xFFCF4444),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Delete", color = Color(0xFFCF4444))
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = PitwiseGray400
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}
