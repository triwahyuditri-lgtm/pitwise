package com.example.pitwise.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.pitwise.data.local.OnboardingPreferences
import com.example.pitwise.ui.screen.advisor.AdvisorResultScreen
import com.example.pitwise.ui.screen.calculate.CalculateScreen
import com.example.pitwise.ui.screen.calibration.CalibrationScreen
import com.example.pitwise.ui.screen.home.HomeScreen
import com.example.pitwise.ui.screen.map.MapScreen
import com.example.pitwise.ui.screen.maplist.MapListScreen

import com.example.pitwise.ui.screen.productivity.ProductivityScreen
import com.example.pitwise.ui.screen.settings.SettingsScreen
import com.example.pitwise.ui.screen.unitsettings.UnitSettingsScreen
import com.example.pitwise.ui.theme.PitwiseBackground
import com.example.pitwise.ui.theme.PitwiseGray400
import com.example.pitwise.ui.theme.PitwisePrimary
import com.example.pitwise.ui.theme.PitwiseSurface
import kotlinx.coroutines.launch

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, "Home", Icons.Default.Home),
    BottomNavItem(Screen.Map, "Map", Icons.Default.Map),
    BottomNavItem(Screen.Calculate, "Calculate", Icons.Default.Calculate),
    BottomNavItem(Screen.Productivity, "Prod", Icons.Default.Speed),
    BottomNavItem(Screen.Settings, "Settings", Icons.Default.Settings)
)

@Composable
fun MainScaffold(
    parentNavController: NavHostController,
    onLogout: () -> Unit,
    onboardingPreferences: OnboardingPreferences
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val scope = rememberCoroutineScope()

    // ── Onboarding state ──
    val isFirstLaunch by onboardingPreferences.isFirstLaunch.collectAsState(initial = false)
    var showOnboarding by remember { mutableStateOf(false) }
    var onboardingStep by remember { mutableIntStateOf(0) }
    val navItemBounds = remember { mutableStateMapOf<Int, Rect>() }

    // Trigger onboarding on first launch
    LaunchedEffect(isFirstLaunch) {
        if (isFirstLaunch) {
            showOnboarding = true
            onboardingStep = 0
        }
    }

    // Hide bottom bar on sub-screens
    val showBottomBar = bottomNavItems.any { item ->
        currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
    }

    // Wrap everything in a Box so the overlay can sit on top
    androidx.compose.foundation.layout.Box {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = PitwiseSurface,
                        tonalElevation = 0.dp
                    ) {
                        bottomNavItems.forEachIndexed { index, item ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.route == item.screen.route
                            } == true

                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PitwisePrimary,
                                    selectedTextColor = PitwisePrimary,
                                    unselectedIconColor = PitwiseGray400,
                                    unselectedTextColor = PitwiseGray400,
                                    indicatorColor = PitwiseBackground
                                ),
                                modifier = Modifier.onGloballyPositioned { coords ->
                                    navItemBounds[index] = coords.boundsInRoot()
                                }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        onNavigateToMap = { navController.navigate(Screen.Map.route) },
                        onNavigateToMeasure = { navController.navigate(Screen.Map.route) },
                        onNavigateToCalculate = { navController.navigate(Screen.Calculate.route) },
                        onLogout = onLogout
                    )
                }

                composable(Screen.Map.route) {
                    MapListScreen(
                        onOpenMap = { mapId ->
                            navController.navigate(Screen.MapViewer.createRoute(mapId))
                        }
                    )
                }

                composable(Screen.Calculate.route) {
                    CalculateScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.Productivity.route) {
                    ProductivityScreen(
                        onWhyNotAchieved = {
                            navController.navigate(Screen.AdvisorResult.route)
                        }
                    )
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onNavigateToUnitSettings = {
                            navController.navigate(Screen.UnitSettings.route)
                        },
                        onNavigateToCalibration = {
                            navController.navigate(Screen.Calibration.route)
                        },
                        onShowGuideAgain = {
                            scope.launch {
                                onboardingPreferences.resetOnboarding()
                            }
                            onboardingStep = 0
                            showOnboarding = true
                        }
                    )
                }

                // Sub-screens (no bottom bar)

                composable(Screen.AdvisorResult.route) {
                    AdvisorResultScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.UnitSettings.route) {
                    UnitSettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.Calibration.route) {
                    CalibrationScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.MapList.route) {
                    MapListScreen(
                        onOpenMap = { mapId ->
                            navController.navigate(Screen.MapViewer.createRoute(mapId))
                        }
                    )
                }

                composable(
                    route = Screen.MapViewer.route,
                    arguments = listOf(navArgument("mapId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val mapId = backStackEntry.arguments?.getLong("mapId") ?: return@composable
                    MapScreen(
                        mapId = mapId,
                        onBack = { navController.popBackStack() },
                        onSendToCalculator = { type, value ->
                            navController.navigate(
                                Screen.CalculateContextual.createRoute(type, value)
                            )
                        }
                    )
                }

                // Contextual calculator (from Map measurement)
                composable(
                    route = Screen.CalculateContextual.route,
                    arguments = listOf(
                        navArgument("type") { type = NavType.StringType; defaultValue = "" },
                        navArgument("value") { type = NavType.StringType; defaultValue = "0.0" }
                    )
                ) { backStackEntry ->
                    val mType = backStackEntry.arguments?.getString("type") ?: ""
                    val mValue = backStackEntry.arguments?.getString("value")?.toDoubleOrNull() ?: 0.0
                    CalculateScreen(
                        onBack = { navController.popBackStack() },
                        measurementType = mType.ifEmpty { null },
                        measurementValue = mValue
                    )
                }
            }
        }

                if (showOnboarding) {
            OnboardingGuide(
                currentStep = onboardingStep,
                navItemBounds = navItemBounds,
                onNext = {
                    if (onboardingStep < onboardingSteps.size - 1) {
                        onboardingStep++
                    } else {
                        showOnboarding = false
                        scope.launch {
                            onboardingPreferences.setOnboardingComplete()
                        }
                    }
                },
                onSkip = {
                    showOnboarding = false
                    scope.launch {
                        onboardingPreferences.setOnboardingComplete()
                    }
                }
            )
        }
    }
}
