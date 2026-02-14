package com.example.pitwise.ui.screen.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pitwise.ui.screen.welcome.AuthViewModel
import com.example.pitwise.ui.theme.PitwiseBorder
import com.example.pitwise.ui.theme.PitwiseGray400
import com.example.pitwise.ui.theme.PitwisePrimary
import com.example.pitwise.ui.theme.PitwiseSurface

import com.example.pitwise.ui.components.AboutDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.example.pitwise.BuildConfig
import com.example.pitwise.domain.debug.DebugManager
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToUnitSettings: () -> Unit,
    onNavigateToCalibration: () -> Unit,
    onShowGuideAgain: () -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val role = uiState.session?.role ?: "GUEST"
    var gpsInterval by remember { mutableFloatStateOf(5f) }
    var showAboutDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
    val debugEnabled by DebugManager.debugEnabled.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SETTINGS",
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Section: Unit Settings
            SectionHeader("EQUIPMENT")
            Spacer(modifier = Modifier.height(8.dp))
            SettingsItem(
                icon = Icons.Default.Build,
                title = "Unit Settings",
                subtitle = "View & manage equipment specs",
                onClick = onNavigateToUnitSettings
            )

            if (role == "SUPERADMIN") {
                Spacer(modifier = Modifier.height(8.dp))
                SettingsItem(
                    icon = Icons.Default.Settings,
                    title = "Edit & Publish Units",
                    subtitle = "Superadmin: edit global unit data",
                    onClick = onNavigateToUnitSettings
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: GPS & Calibration
            SectionHeader("GPS & CALIBRATION")
            Spacer(modifier = Modifier.height(8.dp))
            SettingsItem(
                icon = Icons.Default.GpsFixed,
                title = "GPS Calibration",
                subtitle = "WGS84 → Local Grid offset & rotation",
                onClick = onNavigateToCalibration
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsItem(
                icon = Icons.Default.Refresh,
                title = "Reset Calibration",
                subtitle = "Clear calibration data",
                onClick = { /* Will call GpsCalibrationManager.resetCalibration() */ }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // GPS Update Interval Slider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PitwiseSurface)
                    .border(1.dp, PitwiseBorder, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Timer, contentDescription = null, tint = PitwisePrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GPS Update Interval",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${gpsInterval.toInt()} seconds",
                        style = MaterialTheme.typography.headlineSmall,
                        color = PitwisePrimary
                    )
                    Slider(
                        value = gpsInterval,
                        onValueChange = { gpsInterval = it },
                        valueRange = 1f..30f,
                        steps = 28,
                        colors = SliderDefaults.colors(
                            thumbColor = PitwisePrimary,
                            activeTrackColor = PitwisePrimary,
                            inactiveTrackColor = PitwiseGray400.copy(alpha = 0.3f)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("1s", style = MaterialTheme.typography.labelSmall, color = PitwiseGray400)
                        Text("30s", style = MaterialTheme.typography.labelSmall, color = PitwiseGray400)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Help
            SectionHeader("HELP")
            Spacer(modifier = Modifier.height(8.dp))
            SettingsItem(
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                title = "Starting Guide",
                subtitle = "Show onboarding guide again",
                onClick = onShowGuideAgain
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Section: About
            SectionHeader("ABOUT")
            Spacer(modifier = Modifier.height(8.dp))
            SettingsItem(
                icon = Icons.Default.Info,
                title = "PITWISE v1.0",
                subtitle = "Field Decision Tool • Offline-first • Mining Operations",
                onClick = { showAboutDialog = true }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section: Developer Options (Debug Only)
            if (BuildConfig.DEBUG) {
                SectionHeader("DEVELOPER OPTIONS")
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(PitwiseSurface)
                        .border(1.dp, PitwiseBorder, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Build, null, tint = Color.Red, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Show Debug Overlay", 
                            style = MaterialTheme.typography.titleSmall, 
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Switch(
                        checked = debugEnabled,
                        onCheckedChange = { DebugManager.setDebugEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color.Red,
                            uncheckedThumbColor = PitwiseGray400,
                            uncheckedTrackColor = PitwiseSurface
                        )
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        if (showAboutDialog) {
            AboutDialog(onDismiss = { showAboutDialog = false })
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = PitwisePrimary,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun SettingsItem(
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
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PitwisePrimary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = PitwiseGray400
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = PitwiseGray400,
            modifier = Modifier.size(20.dp)
        )
    }
}
