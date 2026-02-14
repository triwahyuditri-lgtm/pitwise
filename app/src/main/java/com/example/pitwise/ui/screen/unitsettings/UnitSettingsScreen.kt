package com.example.pitwise.ui.screen.unitsettings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pitwise.data.local.entity.UnitModel
import com.example.pitwise.data.local.entity.UnitSpec
import com.example.pitwise.ui.theme.PitwiseBorder
import com.example.pitwise.ui.theme.PitwiseGray400
import com.example.pitwise.ui.theme.PitwisePrimary
import com.example.pitwise.ui.theme.PitwiseSurface
import com.example.pitwise.ui.theme.PitwiseSurfaceVariant
import com.example.pitwise.ui.theme.PitwiseSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitSettingsScreen(
    onBack: () -> Unit,
    viewModel: UnitSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "UNIT SETTINGS",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .background(PitwiseSurfaceVariant, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = uiState.currentRole,
                            style = MaterialTheme.typography.labelSmall,
                            color = PitwiseGray400
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Category filter chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterChip(
                    selected = uiState.selectedCategory == "EXCA",
                    onClick = { viewModel.selectCategory("EXCA") },
                    label = { Text("EXCAVATOR") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PitwisePrimary.copy(alpha = 0.2f),
                        selectedLabelColor = PitwisePrimary
                    )
                )
                FilterChip(
                    selected = uiState.selectedCategory == "DT",
                    onClick = { viewModel.selectCategory("DT") },
                    label = { Text("DUMP TRUCK") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PitwisePrimary.copy(alpha = 0.2f),
                        selectedLabelColor = PitwisePrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.selectedModel != null && uiState.selectedSpec != null) {
                // Spec Detail View
                SpecDetailView(
                    model = uiState.selectedModel!!,
                    spec = uiState.selectedSpec!!,
                    role = uiState.currentRole,
                    onSave = { viewModel.saveSpec(it) },
                    onCopy = { viewModel.copyToLocalOverride() },
                    onPublish = { viewModel.publishToGlobal() },
                    onBack = { viewModel.selectModel(uiState.selectedModel!!) }
                )
            } else {
                // Model List
                val filteredModels = uiState.models.filter {
                    it.category == uiState.selectedCategory
                }
                val filteredBrands = uiState.brands.filter {
                    it.category == uiState.selectedCategory
                }

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filteredBrands.forEach { brand ->
                        val brandModels = filteredModels.filter { it.brandId == brand.id }
                        if (brandModels.isNotEmpty()) {
                            item {
                                Text(
                                    text = brand.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = PitwisePrimary,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                            items(brandModels) { model ->
                                UnitModelCard(
                                    model = model,
                                    onClick = { viewModel.selectModel(model) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnitModelCard(
    model: UnitModel,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(PitwiseSurface)
            .border(1.dp, PitwiseBorder, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = model.modelName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Source: ${model.source} • v${model.version}",
                    style = MaterialTheme.typography.bodySmall,
                    color = PitwiseGray400
                )
            }
            Box(
                modifier = Modifier
                    .background(
                        when (model.source) {
                            "SYSTEM" -> PitwiseGray400.copy(alpha = 0.2f)
                            "GLOBAL" -> PitwisePrimary.copy(alpha = 0.2f)
                            "LOCAL_OVERRIDE" -> PitwiseSuccess.copy(alpha = 0.2f)
                            else -> PitwiseGray400.copy(alpha = 0.2f)
                        },
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = model.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (model.source) {
                        "SYSTEM" -> PitwiseGray400
                        "GLOBAL" -> PitwisePrimary
                        "LOCAL_OVERRIDE" -> PitwiseSuccess
                        else -> PitwiseGray400
                    }
                )
            }
        }
    }
}

@Composable
private fun SpecDetailView(
    model: UnitModel,
    spec: UnitSpec,
    role: String,
    onSave: (UnitSpec) -> Unit,
    onCopy: () -> Unit,
    onPublish: () -> Unit,
    onBack: () -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var bucketCapacity by remember { mutableStateOf(spec.bucketCapacityM3?.toString() ?: "") }
    var vesselCapacity by remember { mutableStateOf(spec.vesselCapacityM3?.toString() ?: "") }
    var fillFactor by remember { mutableStateOf(spec.fillFactorDefault?.toString() ?: "") }
    var cycleTime by remember { mutableStateOf(spec.cycleTimeRefSec?.toString() ?: "") }
    var speedLoaded by remember { mutableStateOf(spec.speedLoadedKmh?.toString() ?: "") }
    var speedEmpty by remember { mutableStateOf(spec.speedEmptyKmh?.toString() ?: "") }
    var enginePower by remember { mutableStateOf(spec.enginePowerHp?.toString() ?: "") }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Model header
            Text(
                text = model.modelName,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "${model.source} • Version ${model.version}",
                style = MaterialTheme.typography.bodySmall,
                color = PitwiseGray400
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Spec fields
        item {
            if (model.category == "EXCA") {
                SpecField("Bucket Capacity (m³)", bucketCapacity, isEditing) { bucketCapacity = it }
                Spacer(modifier = Modifier.height(8.dp))
                SpecField("Fill Factor Default", fillFactor, isEditing) { fillFactor = it }
                Spacer(modifier = Modifier.height(8.dp))
                SpecField("Cycle Time (sec)", cycleTime, isEditing) { cycleTime = it }
            } else {
                SpecField("Vessel Capacity (m³)", vesselCapacity, isEditing) { vesselCapacity = it }
                Spacer(modifier = Modifier.height(8.dp))
                SpecField("Speed Loaded (km/h)", speedLoaded, isEditing) { speedLoaded = it }
                Spacer(modifier = Modifier.height(8.dp))
                SpecField("Speed Empty (km/h)", speedEmpty, isEditing) { speedEmpty = it }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SpecField("Engine Power (HP)", enginePower, isEditing) { enginePower = it }
        }

        // Action buttons
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isEditing) {
                    Button(
                        onClick = {
                            val updatedSpec = spec.copy(
                                bucketCapacityM3 = bucketCapacity.toDoubleOrNull(),
                                vesselCapacityM3 = vesselCapacity.toDoubleOrNull(),
                                fillFactorDefault = fillFactor.toDoubleOrNull(),
                                cycleTimeRefSec = cycleTime.toDoubleOrNull(),
                                speedLoadedKmh = speedLoaded.toDoubleOrNull(),
                                speedEmptyKmh = speedEmpty.toDoubleOrNull(),
                                enginePowerHp = enginePower.toDoubleOrNull()
                            )
                            onSave(updatedSpec)
                            isEditing = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PitwisePrimary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SAVE", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    if (role != "GUEST" || model.source == "LOCAL_OVERRIDE") {
                        OutlinedButton(
                            onClick = { isEditing = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("EDIT SPEC", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    if (model.source != "LOCAL_OVERRIDE") {
                        OutlinedButton(
                            onClick = onCopy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("COPY TO LOCAL", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    if (role == "SUPERADMIN") {
                        Button(
                            onClick = onPublish,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PitwiseSuccess,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("PUBLISH GLOBAL", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecField(
    label: String,
    value: String,
    isEditing: Boolean,
    onValueChange: (String) -> Unit
) {
    if (isEditing) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PitwisePrimary,
                unfocusedBorderColor = PitwiseBorder,
                focusedContainerColor = PitwiseSurface,
                unfocusedContainerColor = PitwiseSurface,
                cursorColor = PitwisePrimary,
                focusedLabelColor = PitwisePrimary
            )
        )
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PitwiseSurface, RoundedCornerShape(8.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = PitwiseGray400
            )
            Text(
                text = value.ifBlank { "—" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = PitwisePrimary
            )
        }
    }
}
