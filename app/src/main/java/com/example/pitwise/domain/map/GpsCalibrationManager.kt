package com.example.pitwise.domain.map

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val Context.calibrationDataStore by preferencesDataStore(name = "gps_calibration")

/**
 * Handles WGS84 (lat/lng) → Local Grid (X,Y) coordinate transformation.
 *
 * Calibration requires the user to stand at a known patok (survey marker),
 * provide GPS lat/lng and the known local X,Y coordinates.
 * With 2+ calibration points, we compute an affine transform (offset + rotation + scale).
 */
@Singleton
class GpsCalibrationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_OFFSET_X = doublePreferencesKey("cal_offset_x")
        private val KEY_OFFSET_Y = doublePreferencesKey("cal_offset_y")
        private val KEY_ROTATION = doublePreferencesKey("cal_rotation")
        private val KEY_SCALE = doublePreferencesKey("cal_scale")
        private val KEY_ORIGIN_LAT = doublePreferencesKey("cal_origin_lat")
        private val KEY_ORIGIN_LNG = doublePreferencesKey("cal_origin_lng")


    }

    data class CalibrationData(
        val offsetX: Double = 0.0,
        val offsetY: Double = 0.0,
        val rotation: Double = 0.0,  // radians
        val scale: Double = 1.0,
        val originLat: Double = 0.0,
        val originLng: Double = 0.0,
        val isCalibrated: Boolean = false
    )

    data class CalibrationPoint(
        val lat: Double,
        val lng: Double,
        val localX: Double,
        val localY: Double
    )

    val calibrationFlow: Flow<CalibrationData> = context.calibrationDataStore.data.map { prefs ->
        val ox = prefs[KEY_OFFSET_X]
        if (ox != null) {
            CalibrationData(
                offsetX = ox,
                offsetY = prefs[KEY_OFFSET_Y] ?: 0.0,
                rotation = prefs[KEY_ROTATION] ?: 0.0,
                scale = prefs[KEY_SCALE] ?: 1.0,
                originLat = prefs[KEY_ORIGIN_LAT] ?: 0.0,
                originLng = prefs[KEY_ORIGIN_LNG] ?: 0.0,
                isCalibrated = true
            )
        } else {
            CalibrationData()
        }
    }

    /**
     * Calibrate using a single point (translation only, no rotation).
     */
    suspend fun calibrateSinglePoint(point: CalibrationPoint) {
        // Convert GPS to UTM (Projected)
        val utm = CoordinateUtils.latLngToUtm(point.lat, point.lng)
        val meterX = utm.easting
        val meterY = utm.northing

        val offsetX = point.localX - meterX
        val offsetY = point.localY - meterY

        context.calibrationDataStore.edit { prefs ->
            prefs[KEY_OFFSET_X] = offsetX
            prefs[KEY_OFFSET_Y] = offsetY
            prefs[KEY_ROTATION] = 0.0
            prefs[KEY_SCALE] = 1.0
            prefs[KEY_ORIGIN_LAT] = point.lat
            prefs[KEY_ORIGIN_LNG] = point.lng
        }
    }

    /**
     * Calibrate using two points (translation + rotation + scale).
     */
    suspend fun calibrateTwoPoints(p1: CalibrationPoint, p2: CalibrationPoint) {
        // Convert GPS to UTM (Projected)
        val u1 = CoordinateUtils.latLngToUtm(p1.lat, p1.lng)
        val u2 = CoordinateUtils.latLngToUtm(p2.lat, p2.lng)
        
        val gpsX1 = u1.easting
        val gpsY1 = u1.northing
        val gpsX2 = u2.easting
        val gpsY2 = u2.northing

        val gpsDx = gpsX2 - gpsX1
        val gpsDy = gpsY2 - gpsY1
        val gpsAngle = atan2(gpsDy, gpsDx)
        val gpsDist = sqrt(gpsDx * gpsDx + gpsDy * gpsDy)

        val localDx = p2.localX - p1.localX
        val localDy = p2.localY - p1.localY
        val localAngle = atan2(localDy, localDx)
        val localDist = sqrt(localDx * localDx + localDy * localDy)

        val rotation = localAngle - gpsAngle
        val scale = if (gpsDist > 0.001) localDist / gpsDist else 1.0

        // Compute offset using P1 as reference
        // Transform P1 (UTM) -> Rotated/Scaled -> Check difference with Local P1
        val transformedX = (gpsX1 * scale * cos(rotation)) - (gpsY1 * scale * sin(rotation))
        val transformedY = (gpsX1 * scale * sin(rotation)) + (gpsY1 * scale * cos(rotation))
        
        // This math was slightly wrong in previous version (rotation matrix application).
        // Correct affine transform:
        // x' = x*s*cos(r) - y*s*sin(r) + tx
        // y' = x*s*sin(r) + y*s*cos(r) + ty
        // So tx = localX - (x*s*cos(r) - y*s*sin(r))
        
        val term1 = (gpsX1 * scale * cos(rotation)) - (gpsY1 * scale * sin(rotation))
        val term2 = (gpsX1 * scale * sin(rotation)) + (gpsY1 * scale * cos(rotation))
        
        val offsetX = p1.localX - term1
        val offsetY = p1.localY - term2

        context.calibrationDataStore.edit { prefs ->
            prefs[KEY_OFFSET_X] = offsetX
            prefs[KEY_OFFSET_Y] = offsetY
            prefs[KEY_ROTATION] = rotation
            prefs[KEY_SCALE] = scale
            prefs[KEY_ORIGIN_LAT] = p1.lat
            prefs[KEY_ORIGIN_LNG] = p1.lng
        }
    }

    /**
     * Transform a WGS84 lat/lng to local grid coordinates using current calibration.
     */
    fun transformToLocal(lat: Double, lng: Double, calibration: CalibrationData): Pair<Double, Double> {
        val utm = CoordinateUtils.latLngToUtm(lat, lng)
        
        if (!calibration.isCalibrated) {
            // No calibration — default to raw UTM coordinates
            return Pair(utm.easting, utm.northing)
        }

        val meterX = utm.easting
        val meterY = utm.northing

        val s = calibration.scale
        val r = calibration.rotation
        
        // Apply affine transform
        val localX = (meterX * s * cos(r)) - (meterY * s * sin(r)) + calibration.offsetX
        val localY = (meterX * s * sin(r)) + (meterY * s * cos(r)) + calibration.offsetY

        return Pair(localX, localY)
    }

    /**
     * Reset calibration to defaults.
     */
    suspend fun resetCalibration() {
        context.calibrationDataStore.edit { it.clear() }
    }
}
