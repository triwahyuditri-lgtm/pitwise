package com.example.pitwise.ui.screen.calibration

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GpsFixed
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pitwise.ui.theme.PitwiseBorder
import com.example.pitwise.ui.theme.PitwiseGray400
import com.example.pitwise.ui.theme.PitwisePrimary
import com.example.pitwise.ui.theme.PitwiseSurface
import com.example.pitwise.ui.theme.PitwiseSuccess
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    onBack: () -> Unit,
    viewModel: CalibrationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "GPS CALIBRATION",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = PitwiseGray400)
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
            Spacer(modifier = Modifier.height(16.dp))

            // Info card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PitwisePrimary.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Calibrate by providing a GPS coordinate (WGS84) and the corresponding Local Grid coordinate. Use 1 point for translation-only, or 2 points for translation + rotation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Point 1
            Text(
                text = "POINT 1 — GPS (WGS84)",
                style = MaterialTheme.typography.labelMedium,
                color = PitwisePrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CalField("Latitude", uiState.gps1Lat, Modifier.weight(1f)) { viewModel.updateField("gps1Lat", it) }
                CalField("Longitude", uiState.gps1Lng, Modifier.weight(1f)) { viewModel.updateField("gps1Lng", it) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Local Grid Coordinates",
                style = MaterialTheme.typography.labelSmall,
                color = PitwiseGray400
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CalField("X (East)", uiState.local1X, Modifier.weight(1f)) { viewModel.updateField("local1X", it) }
                CalField("Y (North)", uiState.local1Y, Modifier.weight(1f)) { viewModel.updateField("local1Y", it) }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Point 2 (optional)
            Text(
                text = "POINT 2 — GPS (WGS84) — OPTIONAL",
                style = MaterialTheme.typography.labelMedium,
                color = PitwiseGray400
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CalField("Latitude", uiState.gps2Lat, Modifier.weight(1f)) { viewModel.updateField("gps2Lat", it) }
                CalField("Longitude", uiState.gps2Lng, Modifier.weight(1f)) { viewModel.updateField("gps2Lng", it) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CalField("X (East)", uiState.local2X, Modifier.weight(1f)) { viewModel.updateField("local2X", it) }
                CalField("Y (North)", uiState.local2Y, Modifier.weight(1f)) { viewModel.updateField("local2Y", it) }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Calibrate button
            Button(
                onClick = {
                    scope.launch { viewModel.calibrate() }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PitwisePrimary,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.GpsFixed, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("APPLY CALIBRATION", style = MaterialTheme.typography.labelLarge)
            }

            // Success indicator
            if (uiState.isCalibrated) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PitwiseSuccess.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(PitwiseSuccess, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Calibration saved!",
                        style = MaterialTheme.typography.titleSmall,
                        color = PitwiseSuccess,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CalField(label: String, value: String, modifier: Modifier = Modifier, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PitwisePrimary,
            unfocusedBorderColor = PitwiseBorder,
            focusedLabelColor = PitwisePrimary,
            cursorColor = PitwisePrimary
        ),
        singleLine = true
    )
}
