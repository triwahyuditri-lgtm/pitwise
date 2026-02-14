package com.example.pitwise.domain.crs

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracing logger for the Coordinate Reference System pipeline.
 * Used to debug GPS misalignment issues by logging intermediate transformation steps.
 */
@Singleton
class CrsDebugLogger @Inject constructor() {

    private val TAG = "PitwiseCRS"

    fun log(stage: String, message: String) {
        Log.d(TAG, "[$stage] $message")
    }

    fun logPoint(stage: String, name: String, x: Double, y: Double, z: Double? = null) {
        val zStr = if (z != null) ", Z=%.3f".format(z) else ""
        Log.d(TAG, "[$stage] $name: (%.6f, %.6f)$zStr")
    }

    fun logTransformation(
        stage: String,
        input: Pair<Double, Double>,
        output: Pair<Double, Double>,
        params: String
    ) {
        Log.d(TAG, "[$stage] Transform: (${input.first}, ${input.second}) -> (${output.first}, ${output.second}) | Params: $params")
    }

    fun error(stage: String, message: String, e: Throwable? = null) {
        Log.e(TAG, "[$stage] ERROR: $message", e)
    }
}
