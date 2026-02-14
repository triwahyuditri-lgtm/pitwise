package com.example.pitwise.domain.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Manages device heading sensors to provide a smooth azimuth for the UI.
 * Uses Sensor.TYPE_ROTATION_VECTOR for best accuracy and applies a low-pass filter.
 */
@Singleton
class HeadingSensorManager @Inject constructor(
    @ApplicationContext private val context: Context
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val _heading = MutableStateFlow(0f)
    val heading: StateFlow<Float> = _heading.asStateFlow()

    private var isListening = false
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    // Low-pass filter alpha (0.1 = heavy smoothing, 1.0 = no smoothing)
    private val alpha = 0.1f
    private var smoothedHeading = 0f

    fun startListening() {
        if (isListening) return
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationSensor != null) {
            // SENSOR_DELAY_GAME is a good balance for UI updates (approx 20ms)
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
            isListening = true
        }
    }

    fun stopListening() {
        if (!isListening) return
        sensorManager.unregisterListener(this)
        isListening = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientation)

            // orientation[0] is azimuth in radians (-pi to pi)
            val azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
            
            // Normalize to 0-360
            val targetHeading = (azimuthDeg + 360) % 360

            // Apply Low-Pass Filter
            // Handle wrap-around (359 -> 1)
            var delta = targetHeading - smoothedHeading
            if (delta < -180) delta += 360
            if (delta > 180) delta -= 360

            smoothedHeading += delta * alpha
            
            // Normalize smoothed result
            smoothedHeading = (smoothedHeading + 360) % 360

            _heading.value = smoothedHeading
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
