package com.example.pitwise.domain.debug

import com.example.pitwise.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages debug features and overlays.
 * Ensures debug info is never shown in production unless explicitly enabled in debug builds.
 */
object DebugManager {

    private val _debugEnabled = MutableStateFlow(false)
    val debugEnabled: StateFlow<Boolean> = _debugEnabled.asStateFlow()

    /**
     * Check if debug overlay should be shown.
     * Use this gate for all debug UI elements.
     */
    fun shouldShowDebugOverlay(): Boolean {
        return BuildConfig.DEBUG && _debugEnabled.value
    }

    /**
     * Toggle debug overlay.
     * Only works in DEBUG builds.
     */
    fun setDebugEnabled(enabled: Boolean) {
        if (BuildConfig.DEBUG) {
            _debugEnabled.value = enabled
        }
    }
}
