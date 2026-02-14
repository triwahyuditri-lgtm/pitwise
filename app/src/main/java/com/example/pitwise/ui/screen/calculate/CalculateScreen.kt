package com.example.pitwise.ui.screen.calculate

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pitwise.domain.calculator.MeasurementType
import com.example.pitwise.ui.theme.PitwiseBorder
import com.example.pitwise.ui.theme.PitwiseGray400
import com.example.pitwise.ui.theme.PitwisePrimary
import com.example.pitwise.ui.theme.PitwiseSurface
import java.text.NumberFormat
import java.util.Locale

// ════════════════════════════════════════════════════
// Calculator Screen — contextual workflow engine
// ════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculateScreen(
    onBack: () -> Unit,
    measurementType: String? = null,
    measurementValue: Double = 0.0,
    viewModel: CalculateViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Apply measurement context once
    LaunchedEffect(measurementType, measurementValue) {
        if (measurementType != null && measurementValue > 0.0) {
            viewModel.setMeasurementContext(measurementType, measurementValue)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            uiState.selectedCalculator != null ->
                                uiState.selectedCalculator!!.name.replace("_", " ")
                            uiState.isContextual -> "CALCULATE"
                            else -> "CALCULATE"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.selectedCalculator != null) viewModel.clearSelection()
                        else onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PitwiseGray400
                        )
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Measurement Source Banner ──
            if (uiState.isContextual && uiState.selectedCalculator == null) {
                MeasurementSourceBanner(
                    type = uiState.measurementType,
                    value = uiState.measurementValue,
                    onClear = { viewModel.clearMeasurementContext() }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (uiState.selectedCalculator == null) {
                // ── Calculator selection cards (filtered) ──
                Spacer(modifier = Modifier.height(8.dp))

                val calcCards = mapOf(
                    CalculatorType.OB_VOLUME to Triple(Icons.Default.Landscape, "OB VOLUME", "Overburden bank & loose volume"),
                    CalculatorType.COAL_TONNAGE to Triple(Icons.Default.Terrain, "COAL TONNAGE", "ROM & clean coal tonnage"),
                    CalculatorType.HAULING_CYCLE to Triple(Icons.Default.LocalShipping, "HAULING CYCLE", "Cycle time, trips & production"),
                    CalculatorType.ROAD_GRADE to Triple(Icons.Default.TrendingUp, "ROAD GRADE", "Grade %, status & warning"),
                    CalculatorType.CUT_FILL to Triple(Icons.Default.Calculate, "CUT & FILL", "Cut/fill volume & net balance")
                )

                for (calcType in uiState.availableCalculators) {
                    val (icon, title, subtitle) = calcCards[calcType] ?: continue
                    CalculatorCard(
                        icon = icon,
                        title = title,
                        subtitle = subtitle,
                        onClick = { viewModel.selectCalculator(calcType) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))
            } else {
                // ── Measurement banner inside form ──
                if (uiState.isContextual) {
                    MeasurementSourceBannerCompact(
                        type = uiState.measurementType,
                        value = uiState.measurementValue,
                        isModified = uiState.isModifiedFromMap
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // ── Calculator input forms ──
                when (uiState.selectedCalculator) {
                    CalculatorType.OB_VOLUME -> ObVolumeForm(viewModel, uiState)
                    CalculatorType.COAL_TONNAGE -> CoalTonnageForm(viewModel, uiState)
                    CalculatorType.HAULING_CYCLE -> HaulingCycleForm(viewModel, uiState)
                    CalculatorType.ROAD_GRADE -> RoadGradeForm(viewModel, uiState)
                    CalculatorType.CUT_FILL -> CutFillForm(viewModel, uiState)
                    else -> {}
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════
// Measurement Source Banners
// ════════════════════════════════════════════════════

@Composable
private fun MeasurementSourceBanner(
    type: MeasurementType?,
    value: Double,
    onClear: () -> Unit
) {
    val displayUnit = if (type == MeasurementType.AREA) "m²" else "m"
    val displayLabel = if (type == MeasurementType.AREA) "Area" else "Distance"
    val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(PitwisePrimary.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .border(1.dp, PitwisePrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Map,
                    contentDescription = null,
                    tint = PitwisePrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Measurement Source",
                        style = MaterialTheme.typography.labelSmall,
                        color = PitwiseGray400
                    )
                    Text(
                        "$displayLabel = ${numberFormat.format(value)} $displayUnit",
                        style = MaterialTheme.typography.titleMedium,
                        color = PitwisePrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Clear source",
                    tint = PitwiseGray400,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun MeasurementSourceBannerCompact(
    type: MeasurementType?,
    value: Double,
    isModified: Boolean
) {
    val displayUnit = if (type == MeasurementType.AREA) "m²" else "m"
    val displayLabel = if (type == MeasurementType.AREA) "Area" else "Distance"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PitwisePrimary.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Map, null, tint = PitwisePrimary, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "$displayLabel: ${"%.2f".format(value)} $displayUnit",
                style = MaterialTheme.typography.labelMedium,
                color = PitwisePrimary,
                fontWeight = FontWeight.Bold
            )
        }
        if (isModified) {
            Text(
                "Modified",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFFF9800),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ════════════════════════════════════════════════════
// Shared Components
// ════════════════════════════════════════════════════

@Composable
private fun CalculatorCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PitwiseSurface)
            .border(1.dp, PitwiseBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(PitwisePrimary.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = title, tint = PitwisePrimary, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = PitwiseGray400)
        }
    }
}

@Composable
private fun PitwiseField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isPrefilled: Boolean = false,
    onModified: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newVal ->
            onValueChange(newVal)
            if (isPrefilled) onModified?.invoke()
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (isPrefilled) PitwisePrimary else PitwisePrimary,
            unfocusedBorderColor = if (isPrefilled) PitwisePrimary.copy(alpha = 0.5f) else PitwiseBorder,
            focusedLabelColor = PitwisePrimary,
            cursorColor = PitwisePrimary
        ),
        singleLine = true,
        supportingText = if (isPrefilled) {
            { Text("Auto-filled from Map", style = MaterialTheme.typography.labelSmall, color = PitwisePrimary.copy(alpha = 0.7f)) }
        } else null
    )
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = PitwiseGray400)
        Text(value, style = MaterialTheme.typography.titleMedium, color = PitwisePrimary)
    }
}

// ════════════════════════════════════════════════════
// Calculator Forms
// ════════════════════════════════════════════════════

@Composable
private fun ObVolumeForm(viewModel: CalculateViewModel, uiState: CalculateUiState) {
    val hasPrefill = uiState.prefillArea > 0
    var area by remember { mutableStateOf(if (hasPrefill) "%.2f".format(uiState.prefillArea) else "") }
    var thickness by remember { mutableStateOf("") }
    var swellFactor by remember { mutableStateOf("0.8") }
    var density by remember { mutableStateOf("2.2") }
    val context = LocalContext.current

    Spacer(modifier = Modifier.height(8.dp))
    PitwiseField(
        "Area (m²)", area, { area = it },
        isPrefilled = hasPrefill,
        onModified = { viewModel.markModifiedFromMap() }
    )
    Spacer(modifier = Modifier.height(8.dp))
    PitwiseField("Thickness (m)", thickness, { thickness = it })
    Spacer(modifier = Modifier.height(8.dp))
    PitwiseField("Swell Factor", swellFactor, { swellFactor = it })
    Spacer(modifier = Modifier.height(8.dp))
    PitwiseField("Density (ton/m³)", density, { density = it })
    Spacer(modifier = Modifier.height(16.dp))

    val canCalculate = (area.toDoubleOrNull() ?: 0.0) > 0.0 &&
            (thickness.toDoubleOrNull() ?: 0.0) > 0.0

    Button(
        onClick = {
            viewModel.calculateOb(
                area.toDoubleOrNull() ?: 0.0,
                thickness.toDoubleOrNull() ?: 0.0,
                swellFactor.toDoubleOrNull() ?: 0.8,
                density.toDoubleOrNull() ?: 2.2
            )
        },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PitwisePrimary, contentColor = Color.Black),
        shape = RoundedCornerShape(12.dp),
        enabled = canCalculate
    ) {
        Text("CALCULATE", style = MaterialTheme.typography.labelLarge)
    }

    if (!canCalculate && thickness.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            if ((area.toDoubleOrNull() ?: 0.0) <= 0.0) "⚠ Area must be > 0"
            else "⚠ Thickness must be > 0",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFFF9800)
        )
    }

    uiState.obResult?.let { result ->
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(PitwiseSurface, RoundedCornerShape(12.dp))
                .border(1.dp, PitwisePrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column {
                Text("RESULT", style = MaterialTheme.typography.labelMedium, color = PitwisePrimary)
                Spacer(modifier = Modifier.height(8.dp))
                ResultRow("Volume BCM", "${"%.2f".format(result.volumeBcm)} m³")
                ResultRow("Volume LCM", "${"%.2f".format(result.volumeLcm)} m³")
                ResultRow("Tonnage", "${"%.2f".format(result.tonnage)} ton")
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        viewModel.shareResult(context, "OB Volume", listOf(
                            "Volume BCM" to "${"%.2f".format(result.volumeBcm)} m³",
                            "Volume LCM" to "${"%.2f".format(result.volumeLcm)} m³",
                            "Tonnage" to "${"%.2f".format(result.tonnage)} ton"
                        ))
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PitwiseSurface),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = PitwisePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SHARE RESULT", color = PitwisePrimary)
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun CoalTonnageForm(viewModel: CalculateViewModel, uiState: CalculateUiState) {
    val hasPrefill = uiState.prefillArea > 0
    var area by remember { mutableStateOf(if (hasPrefill) "%.2f".format(uiState.prefillArea) else "") }
    var seamThickness by remember { mutableStateOf("") }
    var density by remember { mutableStateOf("1.3") }
    var recovery by remember { mutableStateOf("100") }
    val context = LocalContext.current

    Spacer(modifier = Modifier.height(8.dp))
    PitwiseField(
        "Area (m²)", area, { area = it },
        isPrefilled = hasPrefill,
        onModified = { viewModel.markModifiedFromMap() }
    )
    Spacer(modifier = Modifier.height(8.dp))
    PitwiseField("Seam Thickness (m)", seamThickness, { seamThickness = it })
    Spacer(modifier = Modifier.height(8.dp))
    PitwiseField("Density (ton/m³)", density, { density = it })
    Spacer(modifier = Modifier.height(8.dp))
    PitwiseField("Recovery (%)", recovery, { recovery = it })
    Spacer(modifier = Modifier.height(16.dp))

    val canCalculate = (area.toDoubleOrNull() ?: 0.0) > 0.0 &&
            (seamThickness.toDoubleOrNull() ?: 0.0) > 0.0

    Button(
        onClick = {
            viewModel.calculateCoal(
                area.toDoubleOrNull() ?: 0.0,
                seamThickness.toDoubleOrNull() ?: 0.0,
                density.toDoubleOrNull() ?: 1.3,
                recovery.toDoubleOrNull() ?: 100.0
            )
        },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PitwisePrimary, contentColor = Color.Black),
        shape = RoundedCornerShape(12.dp),
        enabled = canCalculate
    ) {
        Text("CALCULATE", style = MaterialTheme.typography.labelLarge)
    }

    uiState.coalResult?.let { result ->
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(PitwiseSurface, RoundedCornerShape(12.dp))
                .border(1.dp, PitwisePrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column {
                Text("RESULT", style = MaterialTheme.typography.labelMedium, color = PitwisePrimary)
                Spacer(modifier = Modifier.height(8.dp))
                ResultRow("ROM Tonnage", "${"%.2f".format(result.romTonnage)} ton")
                ResultRow("Clean Coal", "${"%.2f".format(result.cleanCoalTonnage)} ton")
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        viewModel.shareResult(context, "Coal Tonnage", listOf(
                            "ROM Tonnage" to "${"%.2f".format(result.romTonnage)} ton",
                            "Clean Coal" to "${"%.2f".format(result.cleanCoalTonnage)} ton"
                        ))
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PitwiseSurface),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = PitwisePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SHARE RESULT", color = PitwisePrimary)
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun HaulingCycleForm(viewModel: CalculateViewModel, uiState: CalculateUiState) {
    val hasPrefill = uiState.prefillDistance > 0
    var distance by remember { mutableStateOf(if (hasPrefill) "%.2f".format(uiState.prefillDistance) else "") }
    var speedLoaded by remember { mutableStateOf("") }
    var speedEmpty by remember { mutableStateOf("") }
    var loadTime by remember { mutableStateOf("") }
    var dumpTime by remember { mutableStateOf("") }
    var vessel by remember { mutableStateOf("") }
    val context = LocalContext.current

    Spacer(modifier = Modifier.height(8.dp))
    PitwiseField(
        "Distance (m)", distance, { distance = it },
        isPrefilled = hasPrefill,
        onModified = { viewModel.markModifiedFromMap() }
    )
    Spacer(modifier = Modifier.height(8.dp))
    PitwiseField("Speed Loaded (km/h)", speedLoaded, { speedLoaded = it })
    Spacer(modifier = Modifier.height(8.dp))
    PitwiseField("Speed Empty (km/h)", speedEmpty, { speedEmpty = it })
    Spacer(modifier = Modifier.height(8.dp))
    PitwiseField("Loading Time (min)", loadTime, { loadTime = it })
    Spacer(modifier = Modifier.height(8.dp))
    PitwiseField("Dumping Time (min)", dumpTime, { dumpTime = it })
    Spacer(modifier = Modifier.height(8.dp))
    PitwiseField("Vessel Capacity (m³)", vessel, { vessel = it })
    Spacer(modifier = Modifier.height(16.dp))

    val canCalculate = (distance.toDoubleOrNull() ?: 0.0) > 0.0 &&
            (speedLoaded.toDoubleOrNull() ?: 0.0) > 0.0 &&
            (speedEmpty.toDoubleOrNull() ?: 0.0) > 0.0

    Button(
        onClick = {
            viewModel.calculateHauling(
                distance.toDoubleOrNull() ?: 0.0,
                speedLoaded.toDoubleOrNull() ?: 0.0,
                speedEmpty.toDoubleOrNull() ?: 0.0,
                loadTime.toDoubleOrNull() ?: 0.0,
                dumpTime.toDoubleOrNull() ?: 0.0,
                vessel.toDoubleOrNull() ?: 0.0
            )
        },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PitwisePrimary, contentColor = Color.Black),
        shape = RoundedCornerShape(12.dp),
        enabled = canCalculate
    ) {
        Text("CALCULATE", style = MaterialTheme.typography.labelLarge)
    }

    if (!canCalculate && distance.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            if ((distance.toDoubleOrNull() ?: 0.0) <= 0.0) "⚠ Distance must be > 0"
            else "⚠ Fill in speed loaded & empty",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFFF9800)
        )
    }

    uiState.haulingResult?.let { result ->
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(PitwiseSurface, RoundedCornerShape(12.dp))
                .border(1.dp, PitwisePrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column {
                Text("RESULT", style = MaterialTheme.typography.labelMedium, color = PitwisePrimary)
                Spacer(modifier = Modifier.height(8.dp))
                ResultRow("Cycle Time", "${"%.1f".format(result.cycleTimeMin)} min")
                ResultRow("Trips/Hour", "${"%.1f".format(result.tripsPerHour)}")
                ResultRow("Production", "${"%.1f".format(result.productionPerUnit)} m³/hr")
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        viewModel.shareResult(context, "Hauling Cycle", listOf(
                            "Cycle Time" to "${"%.1f".format(result.cycleTimeMin)} min",
                            "Trips/Hour" to "${"%.1f".format(result.tripsPerHour)}",
                            "Production" to "${"%.1f".format(result.productionPerUnit)} m³/hr"
                        ))
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PitwiseSurface),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = PitwisePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SHARE RESULT", color = PitwisePrimary)
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun RoadGradeForm(viewModel: CalculateViewModel, uiState: CalculateUiState) {
    val hasPrefill = uiState.prefillDistance > 0
    var horizontalDist by remember { mutableStateOf(if (hasPrefill) "%.2f".format(uiState.prefillDistance) else "") }
    var elevStart by remember { mutableStateOf("") }
    var elevEnd by remember { mutableStateOf("") }
    val context = LocalContext.current

    Spacer(modifier = Modifier.height(8.dp))
    PitwiseField(
        "Horizontal Distance (m)", horizontalDist, { horizontalDist = it },
        isPrefilled = hasPrefill,
        onModified = { viewModel.markModifiedFromMap() }
    )
    Spacer(modifier = Modifier.height(8.dp))
    PitwiseField("Elevation Start (m)", elevStart, { elevStart = it })
    Spacer(modifier = Modifier.height(8.dp))
    PitwiseField("Elevation End (m)", elevEnd, { elevEnd = it })
    Spacer(modifier = Modifier.height(16.dp))

    val canCalculate = (horizontalDist.toDoubleOrNull() ?: 0.0) > 0.0

    Button(
        onClick = {
            viewModel.calculateGrade(
                horizontalDist.toDoubleOrNull() ?: 0.0,
                elevStart.toDoubleOrNull() ?: 0.0,
                elevEnd.toDoubleOrNull() ?: 0.0
            )
        },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PitwisePrimary, contentColor = Color.Black),
        shape = RoundedCornerShape(12.dp),
        enabled = canCalculate
    ) {
        Text("CALCULATE", style = MaterialTheme.typography.labelLarge)
    }

    uiState.gradeResult?.let { result ->
        Spacer(modifier = Modifier.height(16.dp))
        val statusColor = when (result.status.name) {
            "SAFE" -> Color(0xFF4CAF50)
            "WARNING" -> Color(0xFFFF9800)
            else -> Color(0xFFCF4444)
        }
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(PitwiseSurface, RoundedCornerShape(12.dp))
                .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("RESULT", style = MaterialTheme.typography.labelMedium, color = PitwisePrimary)
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier.background(statusColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(result.status.name, style = MaterialTheme.typography.labelSmall, color = statusColor)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                ResultRow("Grade", "${"%.1f".format(result.gradePercent)}%")
                ResultRow("Elevation Δ", "${"%.1f".format(result.deltaH)} m")
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        viewModel.shareResult(context, "Road Grade", listOf(
                            "Grade" to "${"%.1f".format(result.gradePercent)}%",
                            "Elevation Δ" to "${"%.1f".format(result.deltaH)} m",
                            "Status" to result.status.name
                        ))
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PitwiseSurface),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = PitwisePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SHARE RESULT", color = PitwisePrimary)
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun CutFillForm(viewModel: CalculateViewModel, uiState: CalculateUiState) {
    var existingElev by remember { mutableStateOf("") }
    var targetElev by remember { mutableStateOf("") }
    var width by remember { mutableStateOf("") }
    var length by remember { mutableStateOf("") }
    val context = LocalContext.current

    Spacer(modifier = Modifier.height(8.dp))
    PitwiseField("Existing Elevation (m)", existingElev, { existingElev = it })
    Spacer(modifier = Modifier.height(8.dp))
    PitwiseField("Target Elevation (m)", targetElev, { targetElev = it })
    Spacer(modifier = Modifier.height(8.dp))
    PitwiseField("Road Width (m)", width, { width = it })
    Spacer(modifier = Modifier.height(8.dp))
    PitwiseField("Segment Length (m)", length, { length = it })
    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = {
            viewModel.calculateCutFill(
                existingElev.toDoubleOrNull() ?: 0.0,
                targetElev.toDoubleOrNull() ?: 0.0,
                width.toDoubleOrNull() ?: 0.0,
                length.toDoubleOrNull() ?: 0.0
            )
        },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PitwisePrimary, contentColor = Color.Black),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text("CALCULATE", style = MaterialTheme.typography.labelLarge)
    }

    uiState.cutFillResult?.let { result ->
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(PitwiseSurface, RoundedCornerShape(12.dp))
                .border(1.dp, PitwisePrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column {
                Text("RESULT", style = MaterialTheme.typography.labelMedium, color = PitwisePrimary)
                Spacer(modifier = Modifier.height(8.dp))
                ResultRow("Cut Volume", "${"%.2f".format(result.cutVolumeM3)} m³")
                ResultRow("Fill Volume", "${"%.2f".format(result.fillVolumeM3)} m³")
                ResultRow("Net Volume", "${"%.2f".format(result.netVolumeM3)} m³")
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        viewModel.shareResult(context, "Cut & Fill", listOf(
                            "Cut Volume" to "${"%.2f".format(result.cutVolumeM3)} m³",
                            "Fill Volume" to "${"%.2f".format(result.fillVolumeM3)} m³",
                            "Net Volume" to "${"%.2f".format(result.netVolumeM3)} m³"
                        ))
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PitwiseSurface),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = PitwisePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SHARE RESULT", color = PitwisePrimary)
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(24.dp))
}
