package com.example.pitwise.ui.screen.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pitwise.data.local.entity.MapAnnotation
import com.example.pitwise.data.local.entity.MapEntry
import com.example.pitwise.domain.map.CoordinateFormat
import com.example.pitwise.domain.map.CoordinateUtils
import com.example.pitwise.domain.dxf.SimpleDxfModel
import com.example.pitwise.domain.dxf.SimpleDxfParser
import com.example.pitwise.domain.map.GpsCalibrationManager
import com.example.pitwise.domain.map.LiveMeasurement
import com.example.pitwise.domain.map.MapMode
import com.example.pitwise.domain.map.MapModeController
import com.example.pitwise.domain.map.MapPoint
import com.example.pitwise.domain.map.MapRepository
import com.example.pitwise.domain.map.MapSerializationUtils
import com.example.pitwise.domain.map.MapTransformEngine
import com.example.pitwise.domain.map.MapVertex
import com.example.pitwise.domain.map.MeasureSubMode
import com.example.pitwise.domain.map.PdfRendererEngine
import com.example.pitwise.domain.transform.ScreenToWorldTransform 
import com.example.pitwise.domain.geopdf.GeoPdfDebugInfo
import com.example.pitwise.domain.geopdf.GeoPdfRepository
import com.example.pitwise.domain.geopdf.GeoPdfValidationResult
import com.example.pitwise.domain.sensor.HeadingSensorManager
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.sqrt
import android.util.Log

// ════════════════════════════════════════════════════
// UI State
// ════════════════════════════════════════════════════

data class MapUiState(
    // Map source data
    val mapEntry: MapEntry? = null,
    val dxfModel: SimpleDxfModel? = null,
    val pdfBitmap: Bitmap? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,

    // Layer toggles
    val showPdfLayer: Boolean = true,
    val showDxfLayer: Boolean = true,
    val showGpsLayer: Boolean = true,

    // Transform state (from engine)
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,

    // Mode
    val currentMode: MapMode = MapMode.VIEW,
    val measureSubMode: MeasureSubMode = MeasureSubMode.DISTANCE,

    // Mode-specific state
    val collectedPoints: List<MapVertex> = emptyList(),
    val idPointMarker: MapVertex? = null,
    val liveMeasurement: LiveMeasurement = LiveMeasurement(),

    // GPS
    val gpsLat: Double? = null,
    val gpsLng: Double? = null,
    val gpsLocalX: Double? = null,
    val gpsLocalY: Double? = null,
    val gpsHeading: Float = 0f,
    val gpsAccuracy: Float? = null,
    val isGpsTracking: Boolean = false,
    val permissionDenied: Boolean = false,

    // Coordinate display
    val coordinateFormat: CoordinateFormat = CoordinateFormat.UTM,
    val coordinateText: String = "",

    // Tap info (for VIEW mode tooltip + bottom panel)
    val tapLocalX: Double? = null,
    val tapLocalY: Double? = null,
    val tapProjectedX: Double? = null,
    val tapProjectedY: Double? = null,
    val tapZ: Double? = null,
    val showTapTooltip: Boolean = false,

    // Persisted annotations
    val annotations: List<MapAnnotation> = emptyList(),

    // Canvas size (for zoom all)
    val canvasWidth: Float = 0f,
    val canvasHeight: Float = 0f,

    // Zoom All trigger
    val zoomAllTrigger: Int = 0,

    // Center on GPS trigger
    val centerOnGpsTrigger: Int = 0,

    // GeoPDF
    val isGeoPdf: Boolean = false,
    val geoPdfDebugInfo: GeoPdfDebugInfo? = null,

    // Snap State
    val isSnapped: Boolean = false
)

// ════════════════════════════════════════════════════
// ViewModel
// ════════════════════════════════════════════════════

@HiltViewModel
class MapViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val mapRepository: MapRepository,
    private val dxfParser: SimpleDxfParser,
    private val pdfRenderer: PdfRendererEngine,
    private val gpsCalibrationManager: GpsCalibrationManager,
    private val geoPdfRepository: GeoPdfRepository,
    private val headingSensorManager: HeadingSensorManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val mapId: Long = savedStateHandle.get<Long>("mapId") ?: -1L

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    // ── Engines ──
    val transformEngine = MapTransformEngine()
    private val modeController = MapModeController()
    private val snapEngine = com.example.pitwise.domain.snap.SnapEngine(
        com.example.pitwise.domain.snap.GridSpatialIndex()
    )

    private var locationCallback: LocationCallback? = null

    init {
        if (mapId > 0) {
            loadMap(mapId)
            loadAnnotations(mapId)
        }
        startGpsTracking()
        startHeadingTracking()
    }

    private fun startHeadingTracking() {
        headingSensorManager.startListening()
        viewModelScope.launch {
            headingSensorManager.heading.collectLatest { heading ->
                _uiState.value = _uiState.value.copy(gpsHeading = heading)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationCallback?.let {
            try {
                LocationServices.getFusedLocationProviderClient(appContext)
                    .removeLocationUpdates(it)
            } catch (_: Exception) {}
        }
        headingSensorManager.stopListening()
    }

    // ════════════════════════════════════════════════════
    // Map Loading
    // ════════════════════════════════════════════════════

    private fun loadMap(id: Long) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                val entry = mapRepository.getMapById(id)
                if (entry == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Peta tidak ditemukan"
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(mapEntry = entry)

                // Set as active and update last opened
                mapRepository.setActive(id)
                mapRepository.updateLastOpened(id)

                when (entry.type) {
                    "DXF" -> {
                        transformEngine.flipY = true
                        loadDxfFromUri(entry.uri)
                    }
                    "PDF" -> {
                        transformEngine.flipY = false
                        loadPdfFromUri(entry.uri)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Gagal memuat peta: ${e.message}"
                )
            }
        }
    }

    private suspend fun loadDxfFromUri(uriString: String) {
        try {
            val uri = Uri.parse(uriString)
            val content = appContext.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.readText()
            if (content != null) {
                val dxf = dxfParser.parse(content)
                val totalEntities = dxf.lines.size + dxf.polylines.size + dxf.points.size
                if (totalEntities == 0) {
                     _uiState.value = _uiState.value.copy(
                        dxfModel = dxf,
                        isLoading = false,
                        errorMessage = "Warning: No entities found in DXF (Version/Format?)"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        dxfModel = dxf,
                        isLoading = false
                    )
                    Log.d(TAG, "DXF parsed: lines=${dxf.lines.size} polylines=${dxf.polylines.size} points=${dxf.points.size} " +
                        "bounds=[X:${dxf.bounds.minX}..${dxf.bounds.maxX}, Y:${dxf.bounds.minY}..${dxf.bounds.maxY}]")
                }
                
                // Build Snap Index in background
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                    val vertices = mutableListOf<com.example.pitwise.domain.snap.DxfVertex>()
                    
                    dxf.points.forEach { p ->
                        vertices.add(com.example.pitwise.domain.snap.DxfVertex(p.x, p.y, p.z))
                    }
                    dxf.lines.forEach { (start, end) ->
                        vertices.add(com.example.pitwise.domain.snap.DxfVertex(start.x, start.y, start.z))
                        vertices.add(com.example.pitwise.domain.snap.DxfVertex(end.x, end.y, end.z))
                    }
                    dxf.polylines.forEach { poly ->
                        poly.forEach { v ->
                            vertices.add(com.example.pitwise.domain.snap.DxfVertex(v.x, v.y, v.z))
                        }
                    }

                    snapEngine.updateVertices(vertices)
                }

                // Auto zoom-all after loading
                triggerZoomAll()
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Gagal membaca file DXF"
                )
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Error DXF: ${e.message}"
            )
        }
    }

    private suspend fun loadPdfFromUri(uriString: String) {
        try {
            val uri = Uri.parse(uriString)
            val data = pdfRenderer.renderFirstPage(uri)
            _uiState.value = _uiState.value.copy(
                pdfBitmap = data?.bitmap,
                isLoading = false,
                errorMessage = if (data == null) "Gagal merender PDF" else null
            )
            // Auto zoom-all after loading
            if (data != null) triggerZoomAll()

            // Attempt GeoPDF metadata extraction (on IO thread, non-blocking)
            geoPdfRepository.reset()
            val geoPdfResult = geoPdfRepository.parseAndInitialize(uri)
            val isGeoPdf = geoPdfResult is GeoPdfValidationResult.Valid
            _uiState.value = _uiState.value.copy(isGeoPdf = isGeoPdf)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Error PDF: ${e.message}"
            )
        }
    }

    // ════════════════════════════════════════════════════
    // Transform & Gesture Delegates
    // ════════════════════════════════════════════════════

    fun onPan(dx: Float, dy: Float) {
        transformEngine.applyPan(dx, dy)
        syncTransformState()
    }

    fun onZoom(factor: Float, focusX: Float, focusY: Float) {
        transformEngine.applyZoom(factor, focusX, focusY)
        syncTransformState()
    }

    fun onDoubleTapZoom(x: Float, y: Float) {
        transformEngine.applyDoubleTapZoom(x, y)
        syncTransformState()
    }

    fun updateCanvasSize(width: Float, height: Float) {
        _uiState.value = _uiState.value.copy(
            canvasWidth = width,
            canvasHeight = height
        )
    }

    fun triggerZoomAll() {
        _uiState.value = _uiState.value.copy(
            zoomAllTrigger = _uiState.value.zoomAllTrigger + 1
        )
    }

    /**
     * Execute the zoom all calculation. Called from composable after canvas size is known.
     */
    fun executeZoomAll() {
        val state = _uiState.value
        val cw = state.canvasWidth
        val ch = state.canvasHeight

        if (cw <= 0f || ch <= 0f) return

        val dxf = state.dxfModel
        val pdf = state.pdfBitmap

        when {
            dxf != null -> {
                transformEngine.zoomAll(
                    dxf.bounds.minX, dxf.bounds.minY,
                    dxf.bounds.maxX, dxf.bounds.maxY,
                    cw, ch
                )
            }
            pdf != null -> {
                transformEngine.zoomAllPdf(pdf.width, pdf.height, cw, ch)
            }
        }
        syncTransformState()
        Log.d(TAG, "executeZoomAll: scale=${transformEngine.scale} offsetX=${transformEngine.offsetX} offsetY=${transformEngine.offsetY}")
    }

    private fun syncTransformState() {
        _uiState.value = _uiState.value.copy(
            scale = transformEngine.scale,
            offsetX = transformEngine.offsetX,
            offsetY = transformEngine.offsetY
        )
    }

    // ════════════════════════════════════════════════════
    // Mode Control
    // ════════════════════════════════════════════════════

    fun setMode(mode: MapMode) {
        modeController.setMode(mode)
        syncModeState()
    }

    fun setMeasureSubMode(subMode: MeasureSubMode) {
        modeController.setMeasureSubMode(subMode)
        syncModeState()
    }

    fun undoPoint() {
        modeController.undoLastPoint()
        syncModeState()
    }

    fun clearPoints() {
        modeController.clearPoints()
        syncModeState()
    }

    private fun syncModeState() {
        _uiState.value = _uiState.value.copy(
            currentMode = modeController.currentMode,
            measureSubMode = modeController.measureSubMode,
            collectedPoints = modeController.collectedPoints,
            idPointMarker = modeController.idPointMarker,
            liveMeasurement = modeController.getLiveMeasurement()
        )
    }

    // ════════════════════════════════════════════════════
    // Map Tap Handling
    // ════════════════════════════════════════════════════

    fun onMapTap(worldX: Double, worldY: Double) {
        // Query Snap Engine
        // worldX/worldY are Local (Visual) coordinates from MapRenderer tap event.
        
        var finalLocalX = worldX
        var finalLocalY = worldY
        var finalProjectedX = worldX
        var finalProjectedY = worldY
        var finalZ: Double? = null
        var isSnapped = false
        var isProjected = false

        // 1. GeoPDF Inverse Transform (Priority)
        // If GeoPDF is active, Local -> Projected (UTM/Geo)
        if (geoPdfRepository.hasValidGeoPdf) {
            val inverse = geoPdfRepository.inverseMatrix
            if (inverse != null) {
                // Apply inverse affine: Pixel -> Projected
                val (projX, projY) = inverse.map(worldX, worldY)
                finalProjectedX = projX
                finalProjectedY = projY
                isProjected = true
                // Note: We snap in Local space or Projected space?
                // SnapEngine usually built from DxfModel. 
                // If DxfModel is loaded, is it in Local or Projected coords?
                // Standard flow: DXF are displayed via "flipY" transform on top of PDF?
                // If DXF matches PDF visual, then DXF coords ARE Local.
            }
        } else {
             // Fallback/Standard: Local == Projected (unless GpsCalibration used, but for now simple)
             // Use calibration to transform if exists? 
             // gpsCalibrationManager.transformToGlobal? 
             // For now, assume 1:1 if not GeoPDF or handle basic calibration later.
        }

        // 2. DXF Snap 
        // Snap uses Local coordinates if DXF is rendered in Local space (which it is, relative to itself).
        if (_uiState.value.dxfModel != null) {
            // Dynamic Snap Radius: ~30 screen pixels
            // radiusWorld = radiusPixels / scale
            val screenTolerancePx = 30.0 
            val detectionRadius = screenTolerancePx / _uiState.value.scale

            val snapResult = snapEngine.findVertex(finalLocalX, finalLocalY, detectionRadius)
            if (snapResult != null) {
                finalLocalX = snapResult.vertex.x
                finalLocalY = snapResult.vertex.y
                finalZ = snapResult.vertex.z
                
                // If snapped, update Projected to match Snapped Local
                if (isProjected) {
                     // Recalculate projected from snapped local
                     // (Optional, if we want strict consistency)
                     val inverse = geoPdfRepository.inverseMatrix
                     if (inverse != null) {
                        val (px, py) = inverse.map(finalLocalX, finalLocalY)
                        finalProjectedX = px
                        finalProjectedY = py
                     }
                } else {
                    finalProjectedX = finalLocalX
                    finalProjectedY = finalLocalY
                }
                isSnapped = true
            }
        }

        val pointZ = finalZ

        // 3. Update State
        when (modeController.currentMode) {
            MapMode.VIEW -> {
                // Show coordinate tooltip
                // We store Local for marker (Rendering)
                // We store Projected for Tooltip (Display)
                modeController.addPoint(finalLocalX, finalLocalY, pointZ) 
                _uiState.value = _uiState.value.copy(
                    tapLocalX = finalLocalX,
                    tapLocalY = finalLocalY,
                    tapProjectedX = finalProjectedX,
                    tapProjectedY = finalProjectedY,
                    tapZ = pointZ,
                    showTapTooltip = true,
                    // Marker uses LOCAL coordinates for rendering
                    idPointMarker = MapVertex(finalLocalX, finalLocalY, pointZ),
                    isSnapped = isSnapped
                )
            }
            MapMode.PLOT -> {
                modeController.addPoint(finalLocalX, finalLocalY, pointZ)
                syncModeState()
            }
            MapMode.MEASURE -> {
                modeController.addPoint(finalLocalX, finalLocalY, pointZ)
                syncModeState()
            }
            MapMode.ID_POINT -> {
                 modeController.addPoint(finalLocalX, finalLocalY, pointZ)
                _uiState.value = _uiState.value.copy(
                    tapLocalX = finalLocalX,
                    tapLocalY = finalLocalY,
                    tapProjectedX = finalProjectedX,
                    tapProjectedY = finalProjectedY,
                    tapZ = pointZ,
                    idPointMarker = modeController.idPointMarker,
                    isSnapped = isSnapped
                )
            }
        }
    }

    fun dismissTapTooltip() {
        _uiState.value = _uiState.value.copy(showTapTooltip = false)
    }

    // ════════════════════════════════════════════════════
    // Annotation Persistence (for Plot & Measure results)
    // ════════════════════════════════════════════════════

    fun finishAndSave() {
        val state = _uiState.value
        val points = modeController.collectedPoints
        if (points.isEmpty()) return

        when (modeController.currentMode) {
            MapMode.PLOT -> {
                if (points.size >= 3) savePolygonAnnotation(points)
            }
            MapMode.MEASURE -> {
                when (modeController.measureSubMode) {
                    MeasureSubMode.DISTANCE -> {
                        if (points.size >= 2) saveLineAnnotation(points)
                    }
                    MeasureSubMode.AREA -> {
                        if (points.size >= 3) savePolygonAnnotation(points)
                    }
                }
            }
            else -> {}
        }

        modeController.clearPoints()
        syncModeState()
    }

    private fun saveLineAnnotation(points: List<MapVertex>) {
        viewModelScope.launch {
            val mapPoints = points.map { MapPoint(it.x, it.y) }
            val json = MapSerializationUtils.pointsToJson(mapPoints)
            val distance = modeController.computeDistance()
            mapRepository.insertAnnotation(
                MapAnnotation(
                    mapId = mapId,
                    type = "LINE",
                    pointsJson = json,
                    distance = distance
                )
            )
        }
    }

    private fun savePolygonAnnotation(points: List<MapVertex>) {
        viewModelScope.launch {
            val mapPoints = points.map { MapPoint(it.x, it.y) }
            val json = MapSerializationUtils.pointsToJson(mapPoints)
            val distance = modeController.computePerimeter()
            val area = modeController.computeArea()
            mapRepository.insertAnnotation(
                MapAnnotation(
                    mapId = mapId,
                    type = "POLYGON",
                    pointsJson = json,
                    distance = distance,
                    area = area
                )
            )
        }
    }

    private fun loadAnnotations(id: Long) {
        viewModelScope.launch {
            mapRepository.getAnnotationsForMap(id).collectLatest { annotations ->
                _uiState.value = _uiState.value.copy(annotations = annotations)
            }
        }
    }

    fun deleteAnnotation(annotation: MapAnnotation) {
        viewModelScope.launch {
            mapRepository.deleteAnnotation(annotation)
        }
    }

    fun parseAnnotationPoints(json: String): List<MapPoint> {
        return MapSerializationUtils.parseJsonToPoints(json)
    }

    // ════════════════════════════════════════════════════
    // Layer Toggles
    // ════════════════════════════════════════════════════

    fun togglePdfLayer() {
        _uiState.value = _uiState.value.copy(showPdfLayer = !_uiState.value.showPdfLayer)
    }

    fun toggleDxfLayer() {
        _uiState.value = _uiState.value.copy(showDxfLayer = !_uiState.value.showDxfLayer)
    }

    fun toggleGpsLayer() {
        _uiState.value = _uiState.value.copy(showGpsLayer = !_uiState.value.showGpsLayer)
    }

    // ════════════════════════════════════════════════════
    // Coordinate Format
    // ════════════════════════════════════════════════════

    fun cycleCoordinateFormat() {
        val next = when (_uiState.value.coordinateFormat) {
            CoordinateFormat.UTM -> CoordinateFormat.LAT_LNG
            CoordinateFormat.LAT_LNG -> CoordinateFormat.DMS
            CoordinateFormat.DMS -> CoordinateFormat.UTM
        }
        _uiState.value = _uiState.value.copy(coordinateFormat = next)
        val lat = _uiState.value.gpsLat
        val lng = _uiState.value.gpsLng
        if (lat != null && lng != null) {
            _uiState.value = _uiState.value.copy(
                coordinateText = CoordinateUtils.format(lat, lng, next)
            )
        }
    }

    // ════════════════════════════════════════════════════
    // GPS
    // ════════════════════════════════════════════════════

    fun centerOnGps() {
        _uiState.value = _uiState.value.copy(
            centerOnGpsTrigger = _uiState.value.centerOnGpsTrigger + 1
        )
    }

    fun toggleGpsTracking() {
        if (_uiState.value.isGpsTracking) {
            _uiState.value = _uiState.value.copy(isGpsTracking = false)
            locationCallback?.let {
                try {
                    LocationServices.getFusedLocationProviderClient(appContext)
                        .removeLocationUpdates(it)
                } catch (_: Exception) {}
            }
        } else {
            startGpsTracking()
        }
    }

    private fun startGpsTracking() {
        if (ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
            .setMinUpdateIntervalMillis(1000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    viewModelScope.launch {
                        updateGpsPosition(loc.latitude, loc.longitude, loc.accuracy)
                    }
                }
            }
        }

        try {
            fusedClient.requestLocationUpdates(request, locationCallback!!, appContext.mainLooper)
            _uiState.value = _uiState.value.copy(isGpsTracking = true)
        } catch (_: SecurityException) {}
    }

    private suspend fun updateGpsPosition(lat: Double, lng: Double, accuracy: Float?) {
        val coordText = CoordinateUtils.format(lat, lng, _uiState.value.coordinateFormat)

        // Use GeoPDF pipeline if available, otherwise fallback to GpsCalibrationManager
        if (geoPdfRepository.hasValidGeoPdf) {
            val pixel = geoPdfRepository.gpsToPixel(lat, lng)
            val debugInfo = geoPdfRepository.getDebugInfo(lat, lng)
            Log.d("GPS_PIPELINE", "GeoPDF: lat=$lat lng=$lng → pixel=(${pixel?.x}, ${pixel?.y}) " +
                "null=${pixel == null}")
            _uiState.value = _uiState.value.copy(
                gpsLat = lat,
                gpsLng = lng,
                gpsLocalX = pixel?.x,
                gpsLocalY = pixel?.y,
                gpsAccuracy = accuracy,
                coordinateText = coordText,
                geoPdfDebugInfo = debugInfo
            )
        } else {
            val calibrationData = gpsCalibrationManager.calibrationFlow.first()
            val (localX, localY) = gpsCalibrationManager.transformToLocal(lat, lng, calibrationData)
            
            // Build debug info for DXF/Local mode
            // We re-calculate UTM here just for display purposes
            val utm = CoordinateUtils.latLngToUtm(lat, lng)
            val dx = localX - utm.easting
            val dy = localY - utm.northing
            
            val debugInfo = GeoPdfDebugInfo(
                rawLat = lat,
                rawLng = lng,
                projectedX = utm.easting,
                projectedY = utm.northing,
                pixelX = localX,
                pixelY = localY,
                crsType = if (calibrationData.isCalibrated) "Local (Calibrated)" else "UTM (Raw)",
                utmZone = utm.zone,
                datum = "WGS84",
                affineMatrix = "Offset: %.1f, %.1f".format(dx, dy)
            )

            _uiState.value = _uiState.value.copy(
                gpsLat = lat,
                gpsLng = lng,
                gpsLocalX = localX,
                gpsLocalY = localY,
                gpsAccuracy = accuracy,
                coordinateText = coordText,
                geoPdfDebugInfo = debugInfo
            )
        }
    }

    // ════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════

    private fun findNearestZ(x: Double, y: Double, dxf: SimpleDxfModel?): Double? {
        if (dxf == null) return null
        var minDist = Double.MAX_VALUE
        var bestZ: Double? = null

        dxf.points.forEach { p ->
            val d = sqrt((p.x - x) * (p.x - x) + (p.y - y) * (p.y - y))
            if (d < minDist) { minDist = d; bestZ = p.z }
        }

        dxf.lines.forEach { (start, end) ->
            val d1 = sqrt((start.x - x) * (start.x - x) + (start.y - y) * (start.y - y))
            val d2 = sqrt((end.x - x) * (end.x - x) + (end.y - y) * (end.y - y))
            if (d1 < minDist) { minDist = d1; bestZ = start.z }
            if (d2 < minDist) { minDist = d2; bestZ = end.z }
        }

        dxf.polylines.forEach { poly ->
            poly.forEach { v ->
                val d = sqrt((v.x - x) * (v.x - x) + (v.y - y) * (v.y - y))
                if (d < minDist) { minDist = d; bestZ = v.z }
            }
        }

        return if (minDist < 50.0) bestZ else null
    }

    /**
     * Get the current live distance for "Send to Calculate" context.
     */
    fun getSendToCalculateData(): Pair<Double, Double> {
        val measurement = modeController.getLiveMeasurement()
        return Pair(measurement.area, measurement.distance)
    }

    companion object {
        private const val TAG = "DXF_RENDER"
    }
}
