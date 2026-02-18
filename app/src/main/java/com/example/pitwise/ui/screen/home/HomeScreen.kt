package com.example.pitwise.ui.screen.home

import android.content.Context
import android.location.LocationManager
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pitwise.domain.map.MapRepository
import com.example.pitwise.ui.screen.welcome.AuthViewModel
import com.example.pitwise.ui.theme.PitwiseBorder
import com.example.pitwise.ui.theme.PitwiseGray400
import com.example.pitwise.ui.theme.PitwisePrimary
import com.example.pitwise.ui.theme.PitwiseSuccess
import com.example.pitwise.ui.theme.PitwiseSurface
import com.example.pitwise.ui.theme.PitwiseSurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToMap: () -> Unit,
    onNavigateToMeasure: () -> Unit,
    onNavigateToCalculate: () -> Unit,
    onNavigateToProductivity: () -> Unit,
    onLogout: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
    mapRepository: MapRepository = hiltViewModel<HomeStatusHelper>().mapRepository
) {
    val uiState by viewModel.uiState.collectAsState()
    val role = uiState.session?.role ?: "GUEST"

    // GPS status check
    val context = LocalContext.current
    val isGpsEnabled = remember {
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) { false }
    }

    // Map count from DB
    val maps by mapRepository.allMaps.collectAsState(initial = emptyList())
    val mapCount = maps.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onBackground)) {
                                append("PIT")
                            }
                            withStyle(SpanStyle(color = PitwisePrimary)) {
                                append("WISE")
                            }
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    // Role badge
                    Box(
                        modifier = Modifier
                            .background(
                                when (role) {
                                    "SUPERADMIN" -> PitwisePrimary.copy(alpha = 0.2f)
                                    "USER" -> PitwiseSurfaceVariant
                                    else -> PitwiseSurface
                                },
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = role,
                            style = MaterialTheme.typography.labelSmall,
                            color = when (role) {
                                "SUPERADMIN" -> PitwisePrimary
                                else -> PitwiseGray400
                            }
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = {
                        viewModel.logout()
                        onLogout()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Logout",
                            tint = PitwiseGray400
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
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
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Welcome section
            Text(
                text = "Welcome, ${
                    when {
                        role == "GUEST" -> "Guest"
                        !uiState.session?.fullName.isNullOrBlank() -> uiState.session?.fullName
                        else -> "User"
                    }
                }",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Mining Field Decision Tool",
                style = MaterialTheme.typography.bodyMedium,
                color = PitwiseGray400
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 3 Primary Action Buttons
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HomeActionButton(
                    icon = Icons.Default.Map,
                    title = "MAP",
                    subtitle = "View PDF & DXF maps offline",
                    onClick = onNavigateToMap
                )
                HomeActionButton(
                    icon = Icons.Default.Straighten,
                    title = "MEASURE",
                    subtitle = "Distance, area & elevation",
                    onClick = onNavigateToMeasure
                )
                HomeActionButton(
                    icon = Icons.Default.Calculate,
                    title = "CALCULATE",
                    subtitle = "OB, Coal, Hauling, Grade, Cut & Fill",
                    onClick = onNavigateToCalculate
                )
                HomeActionButton(
                    icon = Icons.Default.Speed,
                    title = "PRODUCTIVITY",
                    subtitle = "Track daily production & delays",
                    onClick = onNavigateToProductivity
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Status bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PitwiseSurface, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // GPS Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isGpsEnabled) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                        contentDescription = null,
                        tint = if (isGpsEnabled) PitwiseSuccess else PitwiseGray400,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isGpsEnabled) "GPS ON" else "GPS OFF",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isGpsEnabled) PitwiseSuccess else PitwiseGray400
                    )
                }

                // Map status
                Text(
                    text = if (mapCount > 0) "$mapCount map loaded" else "No map loaded",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (mapCount > 0) PitwisePrimary else PitwiseGray400
                )

                // Mode
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = PitwisePrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = role,
                        style = MaterialTheme.typography.labelSmall,
                        color = PitwiseGray400
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HomeActionButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PitwiseSurface)
            .border(1.dp, PitwiseBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(PitwisePrimary.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = PitwisePrimary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = PitwiseGray400
            )
        }
    }
}
