package com.example.pitwise.ui.screen.measure

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pitwise.data.local.entity.MapAnnotation
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.mutableFloatStateOf
import com.example.pitwise.domain.map.DrawingMode
import com.example.pitwise.ui.screen.map.MapContent
import com.example.pitwise.ui.theme.PitwiseGray400
import com.example.pitwise.ui.theme.PitwisePrimary
import com.example.pitwise.ui.theme.PitwiseSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasureScreen(
    onSendToCalculator: (area: Double, distance: Double) -> Unit,
    viewModel: MeasureViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val mapAnnotations by viewModel.mapAnnotations.collectAsState()
    var showInfoSheet by remember { mutableStateOf(false) }

    // Transformation State
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.1f, 10f)
        val extraWidth = (scale - 1) * 1000 // Approximate bounds check placeholder
        val extraHeight = (scale - 1) * 1000
        offsetX += panChange.x
        offsetY += panChange.y
    }

    // ── Info Bottom Sheet ──
    if (showInfoSheet) {
        MeasureInfoBottomSheet(onDismiss = { showInfoSheet = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "MEASURE",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .border(1.5.dp, PitwisePrimary, CircleShape)
                                .clickable { showInfoSheet = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = "Measure info",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                actions = {
                    if (uiState.isLoadingMap) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = PitwisePrimary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    IconButton(onClick = { viewModel.undoLastPoint() }) {
                        Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = PitwiseGray400)
                    }
                    IconButton(onClick = { viewModel.reset() }) {
                        Icon(Icons.Filled.Delete, "Clear", tint = PitwiseGray400)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Mode selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterChip(
                    selected = uiState.mode == MeasureMode.DISTANCE,
                    onClick = { viewModel.setMode(MeasureMode.DISTANCE) },
                    label = { Text("📏 DISTANCE") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PitwisePrimary.copy(alpha = 0.2f),
                        selectedLabelColor = PitwisePrimary
                    )
                )
                FilterChip(
                    selected = uiState.mode == MeasureMode.AREA,
                    onClick = { viewModel.setMode(MeasureMode.AREA) },
                    label = { Text("📐 AREA") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PitwisePrimary.copy(alpha = 0.2f),
                        selectedLabelColor = PitwisePrimary
                    )
                )
            }

            // Live output display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(PitwiseSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, PitwisePrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(20.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (uiState.mode == MeasureMode.DISTANCE) {
                        Text(
                            text = "${"%.2f".format(uiState.totalDistance)}",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 48.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = PitwisePrimary
                        )
                        Text(
                            text = "meters",
                            style = MaterialTheme.typography.titleMedium,
                            color = PitwiseGray400
                        )
                    } else {
                        Text(
                            text = "${"%.2f".format(uiState.totalArea)}",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 48.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = PitwisePrimary
                        )
                        Text(
                            text = "m²",
                            style = MaterialTheme.typography.titleMedium,
                            color = PitwiseGray400
                        )
                    }
                    Text(
                        text = "${uiState.points.size} points",
                        style = MaterialTheme.typography.bodySmall,
                        color = PitwiseGray400.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tap Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .background(PitwiseSurface.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .border(1.dp, PitwiseGray400.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                // Map screen coords to local coords (simplified: 1px = 1m)
                                viewModel.addPoint(offset.x.toDouble(), offset.y.toDouble())
                            }
                        }
                ) {
                    val points = uiState.points
                    val lineColor = PitwisePrimary
                    val pointColor = Color(0xFF4CAF50)
                    val areaFill = PitwisePrimary.copy(alpha = 0.1f)

                    // Draw area fill
                    if (uiState.mode == MeasureMode.AREA && points.size >= 3) {
                        val path = Path().apply {
                            moveTo(points[0].x.toFloat(), points[0].y.toFloat())
                            for (i in 1 until points.size) {
                                lineTo(points[i].x.toFloat(), points[i].y.toFloat())
                            }
                            close()
                        }
                        drawPath(path, areaFill)
                        drawPath(path, lineColor, style = Stroke(width = 2f))
                    }

                    // Draw lines
                    if (points.size >= 2) {
                        for (i in 0 until points.size - 1) {
                            drawLine(
                                color = lineColor,
                                start = Offset(points[i].x.toFloat(), points[i].y.toFloat()),
                                end = Offset(points[i + 1].x.toFloat(), points[i + 1].y.toFloat()),
                                strokeWidth = 2.5f
                            )
                        }
                    }

                    // Draw points
                    for (point in points) {
                        drawCircle(
                            color = pointColor,
                            radius = 8f,
                            center = Offset(point.x.toFloat(), point.y.toFloat())
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 3f,
                            center = Offset(point.x.toFloat(), point.y.toFloat())
                        )
                    }

                    // Hint text if empty
                    if (points.isEmpty()) {
                        // Grid lines for visual reference
                        val gridColor = PitwiseGray400.copy(alpha = 0.1f)
                        for (x in 0..size.width.toInt() step 50) {
                            drawLine(gridColor, Offset(x.toFloat(), 0f), Offset(x.toFloat(), size.height))
                        }
                        for (y in 0..size.height.toInt() step 50) {
                            drawLine(gridColor, Offset(0f, y.toFloat()), Offset(size.width, y.toFloat()))
                        }
                    }
                }

                if (uiState.points.isEmpty()) {
                    Text(
                        text = "Tap to add measurement points",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PitwiseGray400,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Send to Calculator button
            Button(
                onClick = {
                    onSendToCalculator(uiState.totalArea, uiState.totalDistance)
                },
                enabled = uiState.points.size >= 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PitwisePrimary,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SEND TO CALCULATOR",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Measurements from Map ──
            if (mapAnnotations.isNotEmpty()) {
                Text(
                    "From Active Map",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    mapAnnotations.filter { it.distance > 0 || it.area > 0 }.forEach { ann ->
                        MapAnnotationCard(annotation = ann, onSend = onSendToCalculator)
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun MapAnnotationCard(
    annotation: MapAnnotation,
    onSend: (area: Double, distance: Double) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PitwiseSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, PitwiseGray400.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (annotation.type == "POLYGON") "Area Measurement" else "Distance Measurement",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "ID: ${annotation.id}", // Simply use ID for now
                    style = MaterialTheme.typography.labelSmall,
                    color = PitwiseGray400
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    if (annotation.area > 0) {
                        Text(
                            text = "${"%.2f".format(annotation.area)} m²",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (annotation.distance > 0) {
                        Text(
                            text = "${"%.2f".format(annotation.distance)} m",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PitwisePrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                IconButton(onClick = { onSend(annotation.area, annotation.distance) }) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = PitwisePrimary)
                }
            }
        }
    }
}
