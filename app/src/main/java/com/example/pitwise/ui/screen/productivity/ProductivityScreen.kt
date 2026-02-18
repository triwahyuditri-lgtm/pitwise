package com.example.pitwise.ui.screen.productivity

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pitwise.domain.model.ProductivityResult
import com.example.pitwise.domain.model.ProductivityStatus
import com.example.pitwise.domain.model.UnitModelWithBrand
import com.example.pitwise.domain.model.UnitType
import com.example.pitwise.ui.theme.PitwiseBorder
import com.example.pitwise.ui.theme.PitwiseGray400
import com.example.pitwise.ui.theme.PitwisePrimary
import com.example.pitwise.ui.theme.PitwiseSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductivityScreen(
    onWhyNotAchieved: () -> Unit,
    viewModel: ProductivityViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "PRODUCTIVITY",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
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
                .verticalScroll(rememberScrollState())
        ) {
            // ── Unit Type Tabs ───────────────────────────────
            ScrollableTabRow(
                selectedTabIndex = uiState.unitType.ordinal,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = PitwisePrimary,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    // Custom indicator if needed, or default
                }
            ) {
                UnitType.values().forEach { type ->
                    Tab(
                        selected = uiState.unitType == type,
                        onClick = { viewModel.setUnitType(type) },
                        text = { Text(type.displayName) }
                    )
                }
            }
            
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Spacer(modifier = Modifier.height(16.dp))

                // ── Unit Name — clickable picker ─────────────────
                ReadOnlyClickableField(
                    label = "Unit Class / Model",
                    value = uiState.unitName,
                    placeholder = "Select unit...",
                    isLoading = uiState.isLoadingUnits,
                    onClick = { viewModel.toggleUnitPicker(true) }
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                // ── Material Picker ──────────────────────────────
                MaterialDropdownField(
                    label = "Material",
                    options = uiState.materials,
                    selectedOption = uiState.selectedMaterial,
                    isLoading = uiState.isLoadingMaterials,
                    onOptionSelected = { viewModel.selectMaterial(it) }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // ── Spec Fields ──────────────────────────────────
                val capacityLabel = when (uiState.unitType) {
                    UnitType.LOADER -> "Bucket Capacity (m³)"
                    UnitType.HAULER -> "Vessel Capacity"
                    UnitType.DOZER -> "Blade Volume (m³)"
                }
                
                ProdField(capacityLabel, uiState.bucketCapacity) { viewModel.updateField("bucketCapacity", it) }
                Spacer(modifier = Modifier.height(8.dp))
                ProdField("Fill Factor", uiState.fillFactor) { viewModel.updateField("fillFactor", it) }
                Spacer(modifier = Modifier.height(8.dp))
                ProdField("Cycle Time (sec)", uiState.cycleTime) { viewModel.updateField("cycleTime", it) }
                ProdField("Swell Factor", uiState.swellFactor) { viewModel.updateField("swellFactor", it) }
                Spacer(modifier = Modifier.height(8.dp))
                ProdField("Job Efficiency", uiState.jobEfficiency) { viewModel.updateField("jobEfficiency", it) }
                Spacer(modifier = Modifier.height(8.dp))
                ProdField("Efektifitas Kerja (EFF)", uiState.effectiveWorkingHours) { viewModel.updateField("effectiveWorkingHours", it) }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "ℹ Jam kerja efektif alat per hari. Contoh: 2 shift = 14 jam.",
                    style = MaterialTheme.typography.labelSmall,
                    color = PitwiseGray400,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                ProdField("Target Produktifitas", uiState.targetProduction) { viewModel.updateField("targetProduction", it) }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.calculate() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PitwisePrimary,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CALCULATE PRODUCTIVITY", style = MaterialTheme.typography.labelLarge)
                }

                // Result section
                uiState.result?.let { result ->
                    Spacer(modifier = Modifier.height(20.dp))
                    ProductivityResultCard(result, uiState.unitName, onWhyNotAchieved)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // ── Unit Picker Bottom Sheet ──────────────────────────
    if (uiState.showUnitPicker) {
        UnitPickerBottomSheet(
            title = "Select ${uiState.unitType.displayName}",
            units = uiState.units,
            searchQuery = uiState.searchQuery,
            onSearchChange = { viewModel.updateSearchQuery(it) },
            onSelectUnit = { viewModel.selectUnit(it) },
            onDismiss = { viewModel.toggleUnitPicker(false) }
        )
    }
}

// ── Shared UI Components ─────────────────────────────────

@Composable
private fun ReadOnlyClickableField(
    label: String,
    value: String,
    placeholder: String,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .border(1.dp, PitwiseBorder, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = PitwisePrimary
                )
                Text(
                    text = value.ifBlank { placeholder },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (value.isBlank()) PitwiseGray400 else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = PitwisePrimary
                )
            } else {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = PitwiseGray400
                )
            }
        }
    }
}

@Composable
fun MaterialDropdownField(
    label: String,
    options: List<String>,
    selectedOption: String,
    isLoading: Boolean,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        ReadOnlyClickableField(
            label = label,
            value = selectedOption,
            placeholder = "Select material...",
            isLoading = isLoading,
            onClick = { expanded = true }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(PitwiseSurface)
        ) {
            if (options.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No materials loaded", color = PitwiseGray400) },
                    onClick = { expanded = false }
                )
            }
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = Color.White) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitPickerBottomSheet(
    title: String,
    units: List<UnitModelWithBrand>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSelectUnit: (UnitModelWithBrand) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Filter units by search query
    val filteredUnits = if (searchQuery.isBlank()) units
    else units.filter {
        it.modelName.contains(searchQuery, ignoreCase = true)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A1A),
        contentColor = Color.White,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = PitwisePrimary,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = PitwiseGray400)
                }
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search...", color = PitwiseGray400) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = PitwiseGray400)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PitwisePrimary,
                    unfocusedBorderColor = PitwiseBorder,
                    focusedLabelColor = PitwisePrimary,
                    cursorColor = PitwisePrimary,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )

            // Unit list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .padding(horizontal = 8.dp)
            ) {
                items(filteredUnits, key = { it.modelName }) { unit ->
                    UnitPickerItem(unit = unit, onClick = { onSelectUnit(unit) })
                }

                if (filteredUnits.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No units found",
                                color = PitwiseGray400,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun UnitPickerItem(
    unit: UnitModelWithBrand,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Type indicator
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    color = PitwisePrimary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = unit.category.take(2),
                style = MaterialTheme.typography.labelSmall,
                color = PitwisePrimary,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = unit.modelName,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ProdField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PitwisePrimary,
            unfocusedBorderColor = PitwiseBorder,
            focusedLabelColor = PitwisePrimary,
            cursorColor = PitwisePrimary
        ),
        singleLine = true
    )
}

@Composable
private fun ProductivityResultCard(
    result: ProductivityResult,
    unitName: String,
    onWhyNotAchieved: () -> Unit
) {
    val statusColor = when (result.status) {
        ProductivityStatus.GREEN -> Color(0xFF4CAF50)
        ProductivityStatus.YELLOW -> Color(0xFFFF9800)
        ProductivityStatus.RED -> Color(0xFFCF4444)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(PitwiseSurface, RoundedCornerShape(12.dp))
            .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(20.dp)
    ) {
        Column {
            // Status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = unitName.ifBlank { "Unit" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Box(
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = result.status.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Actual vs Target
            Text(
                text = "ACTUAL",
                style = MaterialTheme.typography.labelSmall,
                color = PitwiseGray400
            )
            Text(
                text = "${"%.1f".format(result.totalProduction)}",
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 40.sp),
                color = statusColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "TARGET: ${"%.1f".format(result.targetProduction)}",
                style = MaterialTheme.typography.bodyMedium,
                color = PitwiseGray400
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            val progress = if (result.targetProduction > 0) {
                (result.totalProduction / result.targetProduction).toFloat().coerceIn(0f, 1f)
            } else 0f
            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = tween(800),
                label = "progress"
            )

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp)),
                color = statusColor,
                trackColor = PitwiseSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Deviation: ${"%.1f".format(result.deviationPercent)}%",
                style = MaterialTheme.typography.bodyMedium,
                color = PitwiseGray400
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Productivity/hr: ${"%.1f".format(result.actualProductivityPerHour)}",
                style = MaterialTheme.typography.bodyMedium,
                color = PitwiseGray400
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // WHY TARGET NOT ACHIEVED button
    Button(
        onClick = onWhyNotAchieved,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (result.status == ProductivityStatus.RED)
                Color(0xFFCF4444) else PitwiseSurface,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(Icons.Default.QuestionMark, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "WHY TARGET NOT ACHIEVED?",
            style = MaterialTheme.typography.labelLarge
        )
    }
}
