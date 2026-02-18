package com.example.pitwise.ui.screen.basemap

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pitwise.data.local.entity.MapAnnotation
import com.example.pitwise.domain.map.AnnotationExporter
import com.example.pitwise.domain.map.BaseMapTileProvider
import com.example.pitwise.domain.map.BaseMapType
import com.example.pitwise.domain.map.CoordinateFormat
import com.example.pitwise.domain.map.CoordinateUtils
import com.example.pitwise.domain.map.ExportFormat
import com.example.pitwise.domain.map.MapMode
import com.example.pitwise.domain.map.MapPoint
import com.example.pitwise.domain.map.MapSerializationUtils
import com.example.pitwise.domain.map.MeasureSubMode
import com.example.pitwise.domain.map.PlotSubMode
import com.example.pitwise.domain.map.MapRepository
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.math.*

// ════════════════════════════════════════════════════
// UI State
// ════════════════════════════════════════════════════

data class BaseMapUiState(
    // Map tiles
    val mapType: BaseMapType = BaseMapType.SATELLITE,
    val centerLat: Double = -3.0,
    val centerLng: Double = 116.0,
    val zoom: Int = 5,
    val gpsLat: Double? = null,
    val gpsLng: Double? = null,
    val gpsAccuracy: Float? = null,
    val isGpsTracking: Boolean = false,
    val showTypeSelector: Boolean = false,
    val tiles: Map<String, Bitmap> = emptyMap(),
    val canvasWidth: Float = 0f,
    val canvasHeight: Float = 0f,

    // Mode system
    val currentMode: MapMode = MapMode.VIEW,
    val measureSubMode: MeasureSubMode = MeasureSubMode.DISTANCE,
    val plotSubMode: PlotSubMode = PlotSubMode.POINT,
    val collectedPoints: List<Pair<Double, Double>> = emptyList(), // lat,lng pairs
    val liveDistance: Double = 0.0,       // meters
    val liveArea: Double = 0.0,           // m²
    val livePerimeter: Double = 0.0,      // meters

    // Coordinates
    val coordFormat: CoordinateFormat = CoordinateFormat.LAT_LNG,

    // Annotations
    val annotations: List<MapAnnotation> = emptyList(),
    val selectedAnnotation: MapAnnotation? = null,
    val showExportMenu: Boolean = false,
    val exportResult: File? = null,

    // Navigation events
    val navigateToCalc: Pair<String, Double>? = null,

    // Snackbar
    val snackbarMessage: String? = null
)

// ════════════════════════════════════════════════════
// ViewModel
// ════════════════════════════════════════════════════

@HiltViewModel
class BaseMapViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val tileProvider: BaseMapTileProvider,
    private val mapRepository: MapRepository,
    private val annotationExporter: AnnotationExporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(BaseMapUiState())
    val uiState: StateFlow<BaseMapUiState> = _uiState.asStateFlow()

    private var locationCallback: LocationCallback? = null
    private var tileLoadJob: Job? = null
    private var debounceJob: Job? = null

    companion object {
        const val TILE_SIZE = 256
        private const val MIN_ZOOM = 2
        private const val MAX_ZOOM = 22       // display zoom (digital zoom past tile limit)
        const val MAX_TILE_ZOOM = 18          // max zoom for actual tile data
        private const val EARTH_RADIUS = 6371000.0
    }

    init {
        startGpsTracking()
        loadAnnotations()
    }

    override fun onCleared() {
        super.onCleared()
        locationCallback?.let {
            try {
                LocationServices.getFusedLocationProviderClient(appContext)
                    .removeLocationUpdates(it)
            } catch (_: Exception) {}
        }
    }

    // ════════════════════════════════════════════════════
    // Map Type
    // ════════════════════════════════════════════════════

    fun setMapType(type: BaseMapType) {
        _uiState.value = _uiState.value.copy(mapType = type, showTypeSelector = false)
        requestTileLoad()
    }

    fun toggleTypeSelector() {
        _uiState.value = _uiState.value.copy(showTypeSelector = !_uiState.value.showTypeSelector)
    }

    fun dismissTypeSelector() {
        _uiState.value = _uiState.value.copy(showTypeSelector = false)
    }

    // ════════════════════════════════════════════════════
    // Canvas & Transform
    // ════════════════════════════════════════════════════

    fun updateCanvasSize(width: Float, height: Float) {
        val changed = _uiState.value.canvasWidth != width || _uiState.value.canvasHeight != height
        if (changed) {
            _uiState.value = _uiState.value.copy(canvasWidth = width, canvasHeight = height)
            requestTileLoad()
        }
    }

    fun onPan(dx: Float, dy: Float) {
        val state = _uiState.value
        val tileZoom = state.zoom.coerceAtMost(MAX_TILE_ZOOM)
        val overZoomScale = 1 shl (state.zoom - tileZoom)
        val scaledTileSize = TILE_SIZE * overZoomScale
        val totalTiles = (1 shl tileZoom).toDouble()
        val dLng = -dx / scaledTileSize / totalTiles * 360.0

        val latRad = Math.toRadians(state.centerLat)
        val mercY = ln(tan(PI / 4 + latRad / 2))
        val newMercY = mercY + dy / scaledTileSize / totalTiles * 2 * PI
        val newLat = Math.toDegrees(2 * atan(exp(newMercY)) - PI / 2)

        _uiState.value = state.copy(
            centerLat = newLat.coerceIn(-85.05, 85.05),
            centerLng = normalizeLng(state.centerLng + dLng)
        )
        requestTileLoad()
    }

    fun onZoom(factor: Float) {
        val state = _uiState.value
        val newZoom = if (factor > 1.05f) {
            (state.zoom + 1).coerceAtMost(MAX_ZOOM)
        } else if (factor < 0.95f) {
            (state.zoom - 1).coerceAtLeast(MIN_ZOOM)
        } else return

        if (newZoom != state.zoom) {
            _uiState.value = state.copy(zoom = newZoom)
            requestTileLoad()
        }
    }

    fun zoomIn() {
        val state = _uiState.value
        val newZoom = (state.zoom + 1).coerceAtMost(MAX_ZOOM)
        if (newZoom != state.zoom) {
            _uiState.value = state.copy(zoom = newZoom)
            requestTileLoad()
        }
    }

    fun zoomOut() {
        val state = _uiState.value
        val newZoom = (state.zoom - 1).coerceAtLeast(MIN_ZOOM)
        if (newZoom != state.zoom) {
            _uiState.value = state.copy(zoom = newZoom)
            requestTileLoad()
        }
    }

    fun centerOnGps() {
        val state = _uiState.value
        val lat = state.gpsLat ?: return
        val lng = state.gpsLng ?: return
        _uiState.value = state.copy(
            centerLat = lat, centerLng = lng,
            zoom = maxOf(state.zoom, 15)
        )
        requestTileLoad()
    }

    // ════════════════════════════════════════════════════
    // MODE SYSTEM
    // ════════════════════════════════════════════════════

    fun setMode(mode: MapMode) {
        _uiState.value = _uiState.value.copy(
            currentMode = mode,
            collectedPoints = emptyList(),
            liveDistance = 0.0, liveArea = 0.0, livePerimeter = 0.0
        )
    }

    fun setMeasureSubMode(subMode: MeasureSubMode) {
        _uiState.value = _uiState.value.copy(
            measureSubMode = subMode,
            collectedPoints = emptyList(),
            liveDistance = 0.0, liveArea = 0.0, livePerimeter = 0.0
        )
    }

    fun setPlotSubMode(subMode: PlotSubMode) {
        _uiState.value = _uiState.value.copy(
            plotSubMode = subMode,
            collectedPoints = emptyList(),
            liveDistance = 0.0, liveArea = 0.0, livePerimeter = 0.0
        )
    }

    /** Add a point at the current crosshair center (screen center → lat/lng). */
    fun addPointAtCenter() {
        val state = _uiState.value
        val lat = state.centerLat
        val lng = state.centerLng

        when (state.currentMode) {
            MapMode.VIEW -> { /* no-op in view mode */ }
            MapMode.PLOT, MapMode.MEASURE -> {
                val newPoints = state.collectedPoints + Pair(lat, lng)
                val dist = haversineDistance(newPoints)
                val area = if (newPoints.size >= 3) haversineArea(newPoints) else 0.0
                val peri = if (newPoints.size >= 3) haversinePerimeter(newPoints) else 0.0
                _uiState.value = state.copy(
                    collectedPoints = newPoints,
                    liveDistance = dist, liveArea = area, livePerimeter = peri
                )
            }
            MapMode.ID_POINT -> { /* handled separately */ }
        }
    }

    fun undoLastPoint() {
        val state = _uiState.value
        if (state.collectedPoints.isEmpty()) return
        val newPoints = state.collectedPoints.dropLast(1)
        val dist = haversineDistance(newPoints)
        val area = if (newPoints.size >= 3) haversineArea(newPoints) else 0.0
        val peri = if (newPoints.size >= 3) haversinePerimeter(newPoints) else 0.0
        _uiState.value = state.copy(
            collectedPoints = newPoints,
            liveDistance = dist, liveArea = area, livePerimeter = peri
        )
    }

    fun cancelDrawing() {
        _uiState.value = _uiState.value.copy(
            collectedPoints = emptyList(),
            liveDistance = 0.0, liveArea = 0.0, livePerimeter = 0.0
        )
    }

    /** Finish current drawing/measurement. Plot→save annotation, Measure→send to calc. */
    fun finishDrawing() {
        val state = _uiState.value
        if (state.collectedPoints.isEmpty()) return

        when (state.currentMode) {
            MapMode.MEASURE -> {
                // Navigate to Calculator with measurement result
                val calcData = when (state.measureSubMode) {
                    MeasureSubMode.DISTANCE -> if (state.liveDistance > 0) Pair("distance", state.liveDistance) else null
                    MeasureSubMode.AREA -> if (state.liveArea > 0) Pair("area", state.liveArea) else null
                }
                if (calcData != null) {
                    _uiState.value = state.copy(
                        navigateToCalc = calcData,
                        collectedPoints = emptyList(),
                        liveDistance = 0.0, liveArea = 0.0, livePerimeter = 0.0
                    )
                } else {
                    _uiState.value = state.copy(
                        collectedPoints = emptyList(),
                        liveDistance = 0.0, liveArea = 0.0, livePerimeter = 0.0,
                        snackbarMessage = "Belum ada pengukuran"
                    )
                }
            }
            MapMode.PLOT -> {
                // Save annotation to database
                viewModelScope.launch {
                    val type = when (state.plotSubMode) {
                        PlotSubMode.POINT -> "POINT"
                        PlotSubMode.LINE -> "LINE"
                        PlotSubMode.POLYGON -> "POLYGON"
                    }
                    val points = state.collectedPoints.map { MapPoint(it.second, it.first) }
                    val json = MapSerializationUtils.pointsToJson(points)
                    val count = state.annotations.size + 1

                    val annotation = MapAnnotation(
                        mapId = null,  // null = base map annotation (no FK)
                        type = type,
                        pointsJson = json,
                        name = "$type $count",
                        distance = state.liveDistance,
                        area = state.liveArea
                    )
                    mapRepository.insertAnnotation(annotation)

                    _uiState.value = _uiState.value.copy(
                        collectedPoints = emptyList(),
                        liveDistance = 0.0, liveArea = 0.0, livePerimeter = 0.0,
                        snackbarMessage = "$type disimpan"
                    )
                }
            }
            else -> {}
        }
    }

    fun consumeNavigateToCalc() {
        _uiState.value = _uiState.value.copy(navigateToCalc = null)
    }

    /** Send current measurement to calculator (Measure mode only). */
    fun getMeasurementForCalc(): Pair<String, Double>? {
        val state = _uiState.value
        if (state.currentMode != MapMode.MEASURE) return null
        return when {
            state.measureSubMode == MeasureSubMode.DISTANCE && state.liveDistance > 0 ->
                Pair("distance", state.liveDistance)
            state.measureSubMode == MeasureSubMode.AREA && state.liveArea > 0 ->
                Pair("area", state.liveArea)
            else -> null
        }
    }

    // ════════════════════════════════════════════════════
    // COORDINATES
    // ════════════════════════════════════════════════════

    fun cycleCoordFormat() {
        val next = when (_uiState.value.coordFormat) {
            CoordinateFormat.LAT_LNG -> CoordinateFormat.UTM
            CoordinateFormat.UTM -> CoordinateFormat.DMS
            CoordinateFormat.DMS -> CoordinateFormat.LAT_LNG
        }
        _uiState.value = _uiState.value.copy(coordFormat = next)
    }

    fun formatCoordinate(lat: Double, lng: Double): String {
        return CoordinateUtils.format(lat, lng, _uiState.value.coordFormat)
    }

    // ════════════════════════════════════════════════════
    // ANNOTATIONS
    // ════════════════════════════════════════════════════

    private fun loadAnnotations() {
        viewModelScope.launch {
            mapRepository.getBaseMapAnnotations().collect { annotations ->
                _uiState.value = _uiState.value.copy(annotations = annotations)
            }
        }
    }

    fun selectAnnotation(annotation: MapAnnotation?) {
        _uiState.value = _uiState.value.copy(selectedAnnotation = annotation)
    }

    fun dismissAnnotationDetail() {
        _uiState.value = _uiState.value.copy(selectedAnnotation = null)
    }

    fun updateAnnotationName(annotation: MapAnnotation, newName: String) {
        viewModelScope.launch {
            mapRepository.updateAnnotation(annotation.copy(name = newName))
        }
    }

    fun updateAnnotationColor(annotation: MapAnnotation, newColor: String) {
        viewModelScope.launch {
            mapRepository.updateAnnotation(annotation.copy(color = newColor))
        }
    }

    fun updateAnnotationDescription(annotation: MapAnnotation, newDesc: String) {
        viewModelScope.launch {
            mapRepository.updateAnnotation(annotation.copy(description = newDesc))
        }
    }

    fun deleteAnnotation(annotation: MapAnnotation) {
        viewModelScope.launch {
            mapRepository.deleteAnnotation(annotation)
            _uiState.value = _uiState.value.copy(
                selectedAnnotation = null,
                snackbarMessage = "Anotasi dihapus"
            )
        }
    }

    // ════════════════════════════════════════════════════
    // EXPORT
    // ════════════════════════════════════════════════════

    fun toggleExportMenu() {
        _uiState.value = _uiState.value.copy(showExportMenu = !_uiState.value.showExportMenu)
    }

    fun exportAnnotation(annotation: MapAnnotation, format: ExportFormat) {
        viewModelScope.launch {
            val coordToLatLng: (Double, Double) -> Pair<Double, Double>? = { x, y ->
                Pair(y, x) // x=lng, y=lat → lat, lng
            }
            val file = annotationExporter.export(
                context = appContext,
                annotation = annotation,
                format = format,
                coordToLatLng = coordToLatLng
            )
            _uiState.value = _uiState.value.copy(
                exportResult = file,
                showExportMenu = false,
                snackbarMessage = if (file != null) "Exported: ${file.name}" else "Export gagal"
            )
        }
    }

    fun exportAllAnnotations(format: ExportFormat) {
        viewModelScope.launch {
            val coordToLatLng: (Double, Double) -> Pair<Double, Double>? = { x, y ->
                Pair(y, x)
            }
            val file = annotationExporter.exportAll(
                context = appContext,
                annotations = _uiState.value.annotations,
                format = format,
                coordToLatLng = coordToLatLng
            )
            _uiState.value = _uiState.value.copy(
                exportResult = file,
                showExportMenu = false,
                snackbarMessage = if (file != null) "Exported: ${file.name}" else "Export gagal"
            )
        }
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    // ════════════════════════════════════════════════════
    // TILE LOADING — parallel + debounced
    // ════════════════════════════════════════════════════

    private fun requestTileLoad() {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(80) // Debounce gestures
            loadVisibleTiles()
        }
    }

    private fun loadVisibleTiles() {
        tileLoadJob?.cancel()
        tileLoadJob = viewModelScope.launch {
            val state = _uiState.value
            if (state.canvasWidth <= 0 || state.canvasHeight <= 0) return@launch

            val displayZoom = state.zoom
            // Clamp the actual tile fetch zoom to MAX_TILE_ZOOM (overzoom beyond that)
            val tileZoom = displayZoom.coerceAtMost(MAX_TILE_ZOOM)
            val overZoomScale = 1 shl (displayZoom - tileZoom) // 1 at Z18, 2 at Z19, 4 at Z20...
            val scaledTileSize = TILE_SIZE * overZoomScale

            val totalTiles = 1 shl tileZoom
            val centerTileX = lngToTileX(state.centerLng, tileZoom)
            val centerTileY = latToTileY(state.centerLat, tileZoom)

            val tilesH = (state.canvasWidth / scaledTileSize / 2).toInt() + 2
            val tilesV = (state.canvasHeight / scaledTileSize / 2).toInt() + 2

            val tileXCenter = centerTileX.toInt()
            val tileYCenter = centerTileY.toInt()

            // Build list of needed tiles
            val neededTiles = mutableListOf<Triple<Int, Int, String>>() // x, y, key
            for (dx in -tilesH..tilesH) {
                for (dy in -tilesV..tilesV) {
                    val tx = ((tileXCenter + dx) % totalTiles + totalTiles) % totalTiles
                    val ty = tileYCenter + dy
                    if (ty < 0 || ty >= totalTiles) continue
                    val key = "$tileZoom/$tx/$ty"
                    if (_uiState.value.tiles[key] == null) {
                        neededTiles.add(Triple(tx, ty, key))
                    }
                }
            }

            // Load tiles in parallel (batch of 8 at a time)
            neededTiles.chunked(8).forEach { batch ->
                val jobs = batch.map { (tx, ty, key) ->
                    launch {
                        val bitmap = tileProvider.getTile(state.mapType, tileZoom, tx, ty)
                        if (bitmap != null) {
                            val current = _uiState.value.tiles.toMutableMap()
                            current[key] = bitmap
                            _uiState.value = _uiState.value.copy(tiles = current)
                        }

                        // Load label overlay for HYBRID
                        if (state.mapType == BaseMapType.HYBRID) {
                            val labelKey = "labels/$key"
                            val labelBmp = tileProvider.getLabelTile(tileZoom, tx, ty)
                            if (labelBmp != null) {
                                val current = _uiState.value.tiles.toMutableMap()
                                current[labelKey] = labelBmp
                                _uiState.value = _uiState.value.copy(tiles = current)
                            }
                        }
                    }
                }
                jobs.forEach { it.join() }
            }
        }
    }

    // ════════════════════════════════════════════════════
    // GPS
    // ════════════════════════════════════════════════════

    private fun startGpsTracking() {
        val fine = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) return

        try {
            val client = LocationServices.getFusedLocationProviderClient(appContext)
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
                .setMinUpdateIntervalMillis(1000)
                .build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { loc ->
                        _uiState.value = _uiState.value.copy(
                            gpsLat = loc.latitude,
                            gpsLng = loc.longitude,
                            gpsAccuracy = loc.accuracy,
                            isGpsTracking = true
                        )
                    }
                }
            }
            client.requestLocationUpdates(request, locationCallback!!, appContext.mainLooper)
        } catch (_: SecurityException) {}
    }

    fun toggleGpsTracking() {
        if (_uiState.value.isGpsTracking) return
        startGpsTracking()
    }

    // ════════════════════════════════════════════════════
    // COORDINATE UTILITIES (Web Mercator)
    // ════════════════════════════════════════════════════

    fun lngToTileX(lng: Double, zoom: Int): Double {
        return (lng + 180.0) / 360.0 * (1 shl zoom)
    }

    fun latToTileY(lat: Double, zoom: Int): Double {
        val latRad = Math.toRadians(lat)
        return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * (1 shl zoom)
    }

    fun gpsToScreen(lat: Double, lng: Double): Pair<Float, Float>? {
        val state = _uiState.value
        if (state.canvasWidth <= 0) return null
        val tileZoom = state.zoom.coerceAtMost(MAX_TILE_ZOOM)
        val overZoomScale = 1 shl (state.zoom - tileZoom)
        val scaledTileSize = TILE_SIZE * overZoomScale
        val centerTileX = lngToTileX(state.centerLng, tileZoom)
        val centerTileY = latToTileY(state.centerLat, tileZoom)
        val gpsTileX = lngToTileX(lng, tileZoom)
        val gpsTileY = latToTileY(lat, tileZoom)
        return Pair(
            state.canvasWidth / 2f + ((gpsTileX - centerTileX) * scaledTileSize).toFloat(),
            state.canvasHeight / 2f + ((gpsTileY - centerTileY) * scaledTileSize).toFloat()
        )
    }

    // ════════════════════════════════════════════════════
    // HAVERSINE MATH (geodesic distance/area)
    // ════════════════════════════════════════════════════

    private fun haversineDistanceBetween(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS * asin(sqrt(a))
    }

    private fun haversineDistance(points: List<Pair<Double, Double>>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until points.size - 1) {
            total += haversineDistanceBetween(
                points[i].first, points[i].second,
                points[i + 1].first, points[i + 1].second
            )
        }
        return total
    }

    private fun haversinePerimeter(points: List<Pair<Double, Double>>): Double {
        if (points.size < 3) return 0.0
        var total = haversineDistance(points)
        total += haversineDistanceBetween(
            points.last().first, points.last().second,
            points.first().first, points.first().second
        )
        return total
    }

    /** Spherical excess area (simplified for small polygons). */
    private fun haversineArea(points: List<Pair<Double, Double>>): Double {
        if (points.size < 3) return 0.0
        // Use Shoelace on projected coordinates (approximate for small areas)
        val centerLat = points.map { it.first }.average()
        val cosLat = cos(Math.toRadians(centerLat))
        val projected = points.map { (lat, lng) ->
            Pair(Math.toRadians(lng) * EARTH_RADIUS * cosLat, Math.toRadians(lat) * EARTH_RADIUS)
        }
        var sum = 0.0
        for (i in projected.indices) {
            val j = (i + 1) % projected.size
            sum += projected[i].first * projected[j].second
            sum -= projected[j].first * projected[i].second
        }
        return abs(sum) / 2.0
    }

    private fun normalizeLng(lng: Double): Double {
        var l = lng
        while (l > 180) l -= 360
        while (l < -180) l += 360
        return l
    }
}
