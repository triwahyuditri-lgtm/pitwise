package com.example.pitwise.domain.map

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * State machine for map interaction modes.
 *
 * Manages mode transitions, point collection, and live computations
 * for Plot, Measure, and ID Point modes.
 *
 * This is a pure Kotlin class with no Android dependencies — fully unit-testable.
 */
class MapModeController {

    var currentMode: MapMode = MapMode.VIEW
        private set

    var measureSubMode: MeasureSubMode = MeasureSubMode.DISTANCE
        private set

    private val _collectedPoints = mutableListOf<MapVertex>()
    val collectedPoints: List<MapVertex> get() = _collectedPoints.toList()

    var idPointMarker: MapVertex? = null
        private set

    // ── Mode Transitions ──

    /**
     * Switch to a new mode. Clears all collected points and markers.
     */
    fun setMode(mode: MapMode) {
        currentMode = mode
        _collectedPoints.clear()
        idPointMarker = null
    }

    /**
     * Set the measure sub-mode (Distance or Area).
     * Clears collected points when switching.
     */
    fun setMeasureSubMode(subMode: MeasureSubMode) {
        measureSubMode = subMode
        _collectedPoints.clear()
    }

    // ── Point Collection ──

    /**
     * Add a point based on current mode.
     * In ID_POINT mode, replaces the previous marker.
     */
    fun addPoint(x: Double, y: Double, z: Double?) {
        val vertex = MapVertex(x, y, z)

        when (currentMode) {
            MapMode.VIEW -> {
                // In view mode, tap just sets ID point temporarily
                idPointMarker = vertex
            }
            MapMode.PLOT -> {
                _collectedPoints.add(vertex)
            }
            MapMode.MEASURE -> {
                _collectedPoints.add(vertex)
            }
            MapMode.ID_POINT -> {
                idPointMarker = vertex
            }
        }
    }

    /**
     * Remove the last collected point (undo).
     */
    fun undoLastPoint() {
        if (_collectedPoints.isNotEmpty()) {
            _collectedPoints.removeAt(_collectedPoints.lastIndex)
        }
    }

    /**
     * Clear all collected points.
     */
    fun clearPoints() {
        _collectedPoints.clear()
    }

    // ── Computations ──

    /**
     * Compute total polyline distance from collected points.
     */
    fun computeDistance(): Double {
        if (_collectedPoints.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until _collectedPoints.size - 1) {
            val dx = _collectedPoints[i + 1].x - _collectedPoints[i].x
            val dy = _collectedPoints[i + 1].y - _collectedPoints[i].y
            total += sqrt(dx * dx + dy * dy)
        }
        return total
    }

    /**
     * Compute polygon area using Shoelace formula from collected points.
     */
    fun computeArea(): Double {
        if (_collectedPoints.size < 3) return 0.0
        var sum = 0.0
        for (i in _collectedPoints.indices) {
            val j = (i + 1) % _collectedPoints.size
            sum += _collectedPoints[i].x * _collectedPoints[j].y
            sum -= _collectedPoints[j].x * _collectedPoints[i].y
        }
        return abs(sum) / 2.0
    }

    /**
     * Compute polygon perimeter (closed loop distance).
     */
    fun computePerimeter(): Double {
        if (_collectedPoints.size < 2) return 0.0
        var total = computeDistance()
        // Add closing segment
        if (_collectedPoints.size >= 3) {
            val first = _collectedPoints.first()
            val last = _collectedPoints.last()
            val dx = first.x - last.x
            val dy = first.y - last.y
            total += sqrt(dx * dx + dy * dy)
        }
        return total
    }

    /**
     * Check if the polygon is effectively closed
     * (last point is within threshold of first point).
     */
    fun isPolygonClosed(thresholdWorld: Double = 5.0): Boolean {
        if (_collectedPoints.size < 3) return false
        val first = _collectedPoints.first()
        val last = _collectedPoints.last()
        val dx = first.x - last.x
        val dy = first.y - last.y
        return sqrt(dx * dx + dy * dy) < thresholdWorld
    }

    /**
     * Get the current live measurement text.
     * Returns distance for line modes, area for polygon modes.
     */
    fun getLiveMeasurement(): LiveMeasurement {
        return when (currentMode) {
            MapMode.PLOT -> LiveMeasurement(
                area = computeArea(),
                perimeter = computePerimeter(),
                distance = computeDistance(),
                pointCount = _collectedPoints.size
            )
            MapMode.MEASURE -> when (measureSubMode) {
                MeasureSubMode.DISTANCE -> LiveMeasurement(
                    distance = computeDistance(),
                    pointCount = _collectedPoints.size
                )
                MeasureSubMode.AREA -> LiveMeasurement(
                    area = computeArea(),
                    perimeter = computePerimeter(),
                    distance = computeDistance(),
                    pointCount = _collectedPoints.size
                )
            }
            else -> LiveMeasurement()
        }
    }
}

/**
 * Live measurement data displayed in the UI.
 */
data class LiveMeasurement(
    val area: Double = 0.0,
    val perimeter: Double = 0.0,
    val distance: Double = 0.0,
    val pointCount: Int = 0
)
