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

    var plotSubMode: PlotSubMode = PlotSubMode.POINT
        private set

    private val _collectedPoints = mutableListOf<MapVertex>()
    val collectedPoints: List<MapVertex> get() = _collectedPoints.toList()

    var idPointMarker: MapVertex? = null
        private set

    /**
     * Optional coordinate converter for measurement calculations.
     *
     * When set (e.g., for PDF maps), converts world coordinates (bitmap pixels)
     * to projected CRS coordinates (e.g., UTM easting/northing in meters)
     * before computing distances and areas.
     *
     * When null (e.g., DXF maps), raw world coordinates are used directly
     * (already in real-world units like meters).
     */
    var coordinateConverter: ((Double, Double) -> Pair<Double, Double>?)? = null

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

    /**
     * Set the plot sub-mode (Point, Line, or Polygon).
     * Clears collected points when switching.
     */
    fun setPlotSubMode(subMode: PlotSubMode) {
        plotSubMode = subMode
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
     * Convert collected points through the coordinate converter for measurement.
     *
     * For PDF maps: pixel coords → projected CRS (UTM meters)
     * For DXF maps: raw world coords (already in meters)
     */
    private fun convertedPoints(): List<Pair<Double, Double>> {
        val converter = coordinateConverter
            ?: return _collectedPoints.map { Pair(it.x, it.y) }
        return _collectedPoints.mapNotNull { converter(it.x, it.y) }
    }

    /**
     * Compute total polyline distance from collected points.
     * Uses coordinate converter if set (for PDF geo-measurement).
     */
    fun computeDistance(): Double {
        val pts = convertedPoints()
        if (pts.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until pts.size - 1) {
            val dx = pts[i + 1].first - pts[i].first
            val dy = pts[i + 1].second - pts[i].second
            total += sqrt(dx * dx + dy * dy)
        }
        return total
    }

    /**
     * Compute polygon area using Shoelace formula from collected points.
     * Uses coordinate converter if set (for PDF geo-measurement).
     */
    fun computeArea(): Double {
        val pts = convertedPoints()
        if (pts.size < 3) return 0.0
        var sum = 0.0
        for (i in pts.indices) {
            val j = (i + 1) % pts.size
            sum += pts[i].first * pts[j].second
            sum -= pts[j].first * pts[i].second
        }
        return abs(sum) / 2.0
    }

    /**
     * Compute polygon perimeter (closed loop distance).
     * Uses coordinate converter if set (for PDF geo-measurement).
     */
    fun computePerimeter(): Double {
        val pts = convertedPoints()
        if (pts.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until pts.size - 1) {
            val dx = pts[i + 1].first - pts[i].first
            val dy = pts[i + 1].second - pts[i].second
            total += sqrt(dx * dx + dy * dy)
        }
        // Add closing segment
        if (pts.size >= 3) {
            val first = pts.first()
            val last = pts.last()
            val dx = first.first - last.first
            val dy = first.second - last.second
            total += sqrt(dx * dx + dy * dy)
        }
        return total
    }

    /**
     * Check if the polygon is effectively closed
     * (last point is within threshold of first point).
     * Note: uses raw world coords for UI snap detection (not geo-converted).
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
