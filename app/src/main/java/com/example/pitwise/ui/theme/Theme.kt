package com.example.pitwise.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// PITWISE uses a dark-first design — always dark mode
private val PitwiseColorScheme = darkColorScheme(
    primary = PitwisePrimary,
    onPrimary = PitwiseOnPrimary,
    primaryContainer = PitwisePrimaryVariant,
    onPrimaryContainer = PitwiseOnPrimary,
    secondary = PitwiseGray300,
    onSecondary = PitwiseOnSurface,
    background = PitwiseBackground,
    onBackground = PitwiseOnBackground,
    surface = PitwiseSurface,
    onSurface = PitwiseOnSurface,
    surfaceVariant = PitwiseSurfaceVariant,
    onSurfaceVariant = PitwiseOnSurfaceVariant,
    error = PitwiseError,
    onError = PitwiseOnError,
    outline = PitwiseBorder,
    outlineVariant = PitwiseBorderHover
)

@Composable
fun PitwiseTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = PitwiseColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = PitwiseBackground.toArgb()
            window.navigationBarColor = PitwiseBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PitwiseTypography,
        content = content
    )
}