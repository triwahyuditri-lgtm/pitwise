package com.example.pitwise.ui.screen.calibration

import androidx.lifecycle.ViewModel
import com.example.pitwise.domain.map.GpsCalibrationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class CalibrationUiState(
    val gps1Lat: String = "",
    val gps1Lng: String = "",
    val local1X: String = "",
    val local1Y: String = "",
    val gps2Lat: String = "",
    val gps2Lng: String = "",
    val local2X: String = "",
    val local2Y: String = "",
    val isCalibrated: Boolean = false
)

@HiltViewModel
class CalibrationViewModel @Inject constructor(
    private val calibrationManager: GpsCalibrationManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalibrationUiState())
    val uiState: StateFlow<CalibrationUiState> = _uiState.asStateFlow()

    fun updateField(field: String, value: String) {
        val current = _uiState.value
        _uiState.value = when (field) {
            "gps1Lat" -> current.copy(gps1Lat = value)
            "gps1Lng" -> current.copy(gps1Lng = value)
            "local1X" -> current.copy(local1X = value)
            "local1Y" -> current.copy(local1Y = value)
            "gps2Lat" -> current.copy(gps2Lat = value)
            "gps2Lng" -> current.copy(gps2Lng = value)
            "local2X" -> current.copy(local2X = value)
            "local2Y" -> current.copy(local2Y = value)
            else -> current
        }
    }

    suspend fun calibrate() {
        val state = _uiState.value
        val gps1Lat = state.gps1Lat.toDoubleOrNull() ?: return
        val gps1Lng = state.gps1Lng.toDoubleOrNull() ?: return
        val local1X = state.local1X.toDoubleOrNull() ?: return
        val local1Y = state.local1Y.toDoubleOrNull() ?: return

        val gps2Lat = state.gps2Lat.toDoubleOrNull()
        val gps2Lng = state.gps2Lng.toDoubleOrNull()
        val local2X = state.local2X.toDoubleOrNull()
        val local2Y = state.local2Y.toDoubleOrNull()

        if (gps2Lat != null && gps2Lng != null && local2X != null && local2Y != null) {
            calibrationManager.calibrateTwoPoints(
                p1 = GpsCalibrationManager.CalibrationPoint(lat = gps1Lat, lng = gps1Lng, localX = local1X, localY = local1Y),
                p2 = GpsCalibrationManager.CalibrationPoint(lat = gps2Lat, lng = gps2Lng, localX = local2X, localY = local2Y)
            )
        } else {
            calibrationManager.calibrateSinglePoint(
                point = GpsCalibrationManager.CalibrationPoint(lat = gps1Lat, lng = gps1Lng, localX = local1X, localY = local1Y)
            )
        }

        _uiState.value = state.copy(isCalibrated = true)
    }
}
