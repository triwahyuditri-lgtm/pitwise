package com.example.pitwise.ui.navigation

sealed class Screen(val route: String) {
    // Pre-auth flow
    data object Splash : Screen("splash")
    data object Welcome : Screen("welcome")
    data object Login : Screen("login")


    // Main bottom-nav destinations
    data object Home : Screen("home")
    data object Map : Screen("map")
    data object Measure : Screen("measure")
    data object Productivity : Screen("productivity")
    data object Settings : Screen("settings")

    // Sub-screens (pushed on top of bottom-nav)
    data object Calculate : Screen("calculate")
    data object CalculateContextual : Screen("calculate_ctx?type={type}&value={value}") {
        fun createRoute(type: String, value: Double) = "calculate_ctx?type=$type&value=$value"
    }
    data object AdvisorResult : Screen("advisor_result")
    data object UnitSettings : Screen("unit_settings")
    data object Calibration : Screen("calibration")
    data object MapList : Screen("map_list")
    data object MapViewer : Screen("map_viewer/{mapId}") {
        fun createRoute(mapId: Long) = "map_viewer/$mapId"
    }

    // Main scaffold (hosts bottom nav)
    data object MainShell : Screen("main_shell")
}
