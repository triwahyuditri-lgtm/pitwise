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
import com.example.pitwise.domain.map.DxfEntity
import com.example.pitwise.domain.map.DxfFile
import com.example.pitwise.domain.map.DxfParser
import com.example.pitwise.domain.map.SnapResult
import com.example.pitwise.domain.map.SnapType
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
import com.example.pitwise.domain.map.PlotSubMode
import com.example.pitwise.domain.map.PdfRendererEngine
import com.example.pitwise.domain.geopdf.GeoPdfDebugInfo
import com.example.pitwise.domain.geopdf.GeoPdfRepository
import com.example.pitwise.domain.geopdf.GeoPdfValidationResult
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import javax.inject.Inject
import kotlin.math.sqrt

// ════════════════════════════════════════════════════
// UI State
// ════════════════════════════════════════════════════

data class MapUiState(
    // Map source data
    val mapEntry: MapEntry? = null,
    val dxfFile: DxfFile? = null,
    val pdfBitmap: Bitmap? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,

    // Layer toggles
    val showPdfLayer: Boolean = true,
    val showDxfLayer: Boolean = true,
    val showGpsLayer: Boolean = true,

    // Mode
    val currentMode: MapMode = MapMode.VIEW,
    val measureSubMode: MeasureSubMode = MeasureSubMode.DISTANCE,
    val plotSubMode: PlotSubMode = PlotSubMode.POINT,

    // Mode-specific state
    val collectedPoints: List<MapVertex> = emptyList(),
    val idPointMarker: MapVertex? = null,
    val liveMeasurement: LiveMeasurement = LiveMeasurement(),

    // ID Point inspector state
    val idPointSnapped: Boolean = false,
    val idPointLayer: String? = null,
    val idPointSnapType: SnapType = SnapType.VERTEX,

    // Map type
    val isDxfMap: Boolean = false,

    // Measure finish panel
    val showMeasureFinishPanel: Boolean = false,

    // Plot selection
    val selectedAnnotation: MapAnnotation? = null,
    val selectedAnnotationId: Long? = null,
    val showDetailSheet: Boolean = false,

    // GPS
    val gpsLat: Double? = null,
    val gpsLng: Double? = null,
    val gpsLocalX: Double? = null,
    val gpsLocalY: Double? = null,
    val gpsAccuracy: Float? = null,
    val isGpsTracking: Boolean = false,
    val permissionDenied: Boolean = false,

    // Target Mode (Reticle)
    val isTargetMode: Boolean = false,

    // Coordinate display
    val coordinateFormat: CoordinateFormat = CoordinateFormat.UTM,
    val coordinateText: String = "", // Legacy single line, keep for fallback
    val coordinateZone: String = "",
    val coordinateEasting: String = "",
    val coordinateNorthing: String = "",

    // Tap info (for VIEW mode tooltip + bottom panel)
    val tapLocalX: Double? = null,
    val tapLocalY: Double? = null,
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
    val geoPdfDebugInfo: GeoPdfDebugInfo? = null
)

// ════════════════════════════════════════════════════
// ViewModel
// ════════════════════════════════════════════════════

@HiltViewModel
class MapViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val mapRepository: MapRepository,
    private val dxfParser: DxfParser,
    private val pdfRenderer: PdfRendererEngine,
    private val gpsCalibrationManager: GpsCalibrationManager,
    private val geoPdfRepository: GeoPdfRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val mapId: Long = savedStateHandle.get<Long>("mapId") ?: -1L

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    // ── Engines ──
    val transformEngine = MapTransformEngine()
    private val modeController = MapModeController()

    private var locationCallback: LocationCallback? = null
    private var coordinateDebounceJob: Job? = null

    init {
        if (mapId > 0) {
            loadMap(mapId)
            loadAnnotations(mapId)
        }
        startGpsTracking()
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

                _uiState.value = _uiState.value.copy(
                    mapEntry = entry,
                    isDxfMap = entry.type == "DXF"
                )

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
                _uiState.value = _uiState.value.copy(
                    dxfFile = dxf,
                    isLoading = false
                )
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
        // Debounced coordinate update (not per-frame)
        scheduleCoordinateUpdate()
        // User interaction breaks GPS lock
        if (_uiState.value.isTargetMode) {
            _uiState.value = _uiState.value.copy(isTargetMode = false)
        }
    }

    fun onZoom(factor: Float, focusX: Float, focusY: Float) {
        transformEngine.applyZoom(factor, focusX, focusY)
        // Debounced coordinate update (not per-frame)
        scheduleCoordinateUpdate()
        // User interaction breaks GPS lock
        if (_uiState.value.isTargetMode) {
             _uiState.value = _uiState.value.copy(isTargetMode = false)
        }
    }

    fun onDoubleTapZoom(x: Float, y: Float) {
        transformEngine.applyDoubleTapZoom(x, y)
        scheduleCoordinateUpdate()
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

        val dxf = state.dxfFile
        val pdf = state.pdfBitmap

        when {
            dxf != null -> {
                transformEngine.zoomAll(
                    dxf.minX, dxf.minY,
                    dxf.maxX, dxf.maxY,
                    cw, ch
                )
            }
            pdf != null -> {
                transformEngine.zoomAllPdf(pdf.width, pdf.height, cw, ch)
            }
        }
        syncTransformState()
    }

    /**
     * Debounce coordinate bar updates — only compute after 150ms of no gesture.
     */
    private fun scheduleCoordinateUpdate() {
        coordinateDebounceJob?.cancel()
        coordinateDebounceJob = viewModelScope.launch {
            delay(150)
            updateCenterCoordinate()
        }
    }

    private fun syncTransformState() {
        updateCenterCoordinate()
    }

    private fun updateCenterCoordinate() {
        val w = _uiState.value.canvasWidth
        val h = _uiState.value.canvasHeight
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f

        viewModelScope.launch {
            if (geoPdfRepository.hasValidGeoPdf) {
                // Get WGS84 from screen center
                val gps = geoPdfRepository.screenToGps(
                    cx, cy,
                    transformEngine.scale,
                    transformEngine.offsetX, 
                    transformEngine.offsetY
                )
                
                if (gps != null) {
                    val (lat, lng) = gps
                    val format = _uiState.value.coordinateFormat
                    val full = CoordinateUtils.format(lat, lng, format)
                    
                    val (zone, east, north) = when (format) {
                        CoordinateFormat.UTM -> {
                            val parts = full.split(" ")
                             // CoordinateUtils format: "48 M 123456 E 9876543 N"
                            if (parts.size >= 6) {
                                Triple("${parts[0]} ${parts[1]}", "${parts[2]} ${parts[3]}", "${parts[4]} ${parts[5]}")
                            } else {
                                Triple("", "", full)
                            }
                        }
                        else -> Triple("", "", full)
                    }

                    _uiState.value = _uiState.value.copy(
                        coordinateText = full,
                        coordinateZone = zone,
                        coordinateEasting = east,
                        coordinateNorthing = north
                    )
                }
            } else {
                 // Fallback: Clear coordinates or show center of DXF?
                 // For now, leaving empty or preserving last known if we assume valid map.
            }
        }
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

    /**
     * Add a point at the screen center (crosshair position).
     * Used when "Add Point" button is tapped in crosshair mode.
     */
    fun addCrosshairPoint() {
        val state = _uiState.value
        val centerX = state.canvasWidth / 2f
        val centerY = state.canvasHeight / 2f
        val (worldX, worldY) = transformEngine.screenToWorld(centerX, centerY)
        val z = findNearestZ(worldX, worldY, state.dxfFile)

        when (modeController.currentMode) {
            MapMode.MEASURE -> {
                modeController.addPoint(worldX, worldY, z)
                syncModeState()
            }
            MapMode.PLOT -> {
                when (modeController.plotSubMode) {
                    PlotSubMode.POINT -> createPointAnnotation(worldX, worldY, z)
                    PlotSubMode.LINE, PlotSubMode.POLYGON -> {
                        modeController.addPoint(worldX, worldY, z)
                        syncModeState()
                    }
                }
            }
            else -> {}
        }
    }

    private fun syncModeState() {
        _uiState.value = _uiState.value.copy(
            currentMode = modeController.currentMode,
            measureSubMode = modeController.measureSubMode,
            plotSubMode = modeController.plotSubMode,
            collectedPoints = modeController.collectedPoints,
            idPointMarker = modeController.idPointMarker,
            liveMeasurement = modeController.getLiveMeasurement()
        )
    }

    // ════════════════════════════════════════════════════
    // Plot Sub-Mode
    // ════════════════════════════════════════════════════

    fun setPlotSubMode(subMode: PlotSubMode) {
        modeController.setPlotSubMode(subMode)
        syncModeState()
    }

    // ════════════════════════════════════════════════════
    // Map Tap Handling
    // ════════════════════════════════════════════════════

    fun onMapTap(screenX: Float, screenY: Float) {
        val (worldX, worldY) = transformEngine.screenToWorld(screenX, screenY)
        val z = findNearestZ(worldX, worldY, _uiState.value.dxfFile)

        when (modeController.currentMode) {
            MapMode.VIEW -> {
                // Try to select existing annotation first
                val hit = hitTestAnnotation(worldX, worldY)
                if (hit != null) {
                    selectAnnotation(hit)
                } else {
                    deselectAnnotation()
                    // Show temporary coordinate tooltip (auto-dismiss in 3s)
                    _uiState.value = _uiState.value.copy(
                        tapLocalX = worldX,
                        tapLocalY = worldY,
                        tapZ = z,
                        showTapTooltip = true
                    )
                    // Auto-dismiss after 3 seconds
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(3000)
                        if (_uiState.value.showTapTooltip) {
                            _uiState.value = _uiState.value.copy(showTapTooltip = false)
                        }
                    }
                }
            }
            MapMode.PLOT -> {
                // Try selection first
                val hit = hitTestAnnotation(worldX, worldY)
                if (hit != null) {
                    selectAnnotation(hit)
                    return
                }

                when (modeController.plotSubMode) {
                    PlotSubMode.POINT -> {
                        // Immediately create a POINT object
                        createPointAnnotation(worldX, worldY, z)
                    }
                    PlotSubMode.LINE, PlotSubMode.POLYGON -> {
                        // Multi-point collection
                        modeController.addPoint(worldX, worldY, z)
                        syncModeState()
                    }
                }
            }
            MapMode.MEASURE -> {
                // Try to select existing measurement annotation first
                val hit = hitTestAnnotation(worldX, worldY)
                if (hit != null) {
                    selectAnnotation(hit)
                    return
                }
                // In DXF, snap measurement points to geometry
                if (_uiState.value.isDxfMap) {
                    val snap = snapToDxfEntity(worldX, worldY, screenX, screenY)
                    if (snap != null) {
                        modeController.addPoint(snap.worldX, snap.worldY, snap.z)
                    } else {
                        modeController.addPoint(worldX, worldY, z)
                    }
                } else {
                    modeController.addPoint(worldX, worldY, z)
                }
                syncModeState()
            }
            MapMode.ID_POINT -> {
                // Snap to nearest DXF entity (vertex or segment)
                val snap = snapToDxfEntity(worldX, worldY, screenX, screenY)
                val finalX = snap?.worldX ?: worldX
                val finalY = snap?.worldY ?: worldY
                val finalZ = snap?.z ?: z
                val layer = snap?.layer
                val snapped = snap != null

                _uiState.value = _uiState.value.copy(
                    tapLocalX = finalX,
                    tapLocalY = finalY,
                    tapZ = finalZ,
                    idPointMarker = MapVertex(finalX, finalY, finalZ),
                    idPointSnapped = snapped,
                    idPointLayer = layer,
                    idPointSnapType = snap?.type ?: SnapType.VERTEX,
                    showTapTooltip = true
                )
            }
        }
    }

    fun dismissTapTooltip() {
        _uiState.value = _uiState.value.copy(showTapTooltip = false)
    }

    /**
     * Snap to the nearest DXF entity (vertex or segment) within 20px screen tolerance.
     * Tries vertex snap first, falls back to segment projection.
     */
    private fun snapToDxfEntity(
        worldX: Double, worldY: Double,
        screenX: Float, screenY: Float
    ): SnapResult? {
        val dxf = _uiState.value.dxfFile ?: return null
        val snapThresholdPx = 20f
        var bestDist = Float.MAX_VALUE
        var bestResult: SnapResult? = null

        try {
            // Pass 1: Vertex snap (higher priority)
            for (entity in dxf.entities) {
                val layer = when (entity) {
                    is DxfEntity.Point -> entity.layer
                    is DxfEntity.Line -> entity.layer
                    is DxfEntity.Polyline -> entity.layer
                }
                val candidates: List<Triple<Double, Double, Double>> = when (entity) {
                    is DxfEntity.Point -> listOf(Triple(entity.x, entity.y, entity.z))
                    is DxfEntity.Line -> listOf(
                        Triple(entity.x1, entity.y1, entity.z1),
                        Triple(entity.x2, entity.y2, entity.z2)
                    )
                    is DxfEntity.Polyline -> entity.vertices.map { Triple(it.x, it.y, it.z) }
                }
                for ((vx, vy, vz) in candidates) {
                    val s = transformEngine.worldToScreen(vx, vy)
                    val dx = s.x - screenX
                    val dy = s.y - screenY
                    val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    if (dist < snapThresholdPx && dist < bestDist) {
                        bestDist = dist
                        bestResult = SnapResult(vx, vy, vz, SnapType.VERTEX, layer.ifEmpty { null })
                    }
                }
            }

            // If vertex snap found, return it
            if (bestResult != null) return bestResult

            // Pass 2: Segment snap (project point onto nearest line segment)
            bestDist = Float.MAX_VALUE
            for (entity in dxf.entities) {
                val layer = when (entity) {
                    is DxfEntity.Point -> continue
                    is DxfEntity.Line -> entity.layer
                    is DxfEntity.Polyline -> entity.layer
                }
                val segments: List<Pair<Pair<Double, Double>, Pair<Double, Double>>> = when (entity) {
                    is DxfEntity.Line -> listOf(
                        Pair(Pair(entity.x1, entity.y1), Pair(entity.x2, entity.y2))
                    )
                    is DxfEntity.Polyline -> {
                        val segs = mutableListOf<Pair<Pair<Double, Double>, Pair<Double, Double>>>()
                        for (i in 0 until entity.vertices.size - 1) {
                            segs.add(Pair(
                                Pair(entity.vertices[i].x, entity.vertices[i].y),
                                Pair(entity.vertices[i + 1].x, entity.vertices[i + 1].y)
                            ))
                        }
                        if (entity.closed && entity.vertices.size > 2) {
                            segs.add(Pair(
                                Pair(entity.vertices.last().x, entity.vertices.last().y),
                                Pair(entity.vertices.first().x, entity.vertices.first().y)
                            ))
                        }
                        segs
                    }
                    else -> continue
                }
                for ((p1, p2) in segments) {
                    val proj = projectPointOnSegment(worldX, worldY, p1.first, p1.second, p2.first, p2.second)
                    val s = transformEngine.worldToScreen(proj.first, proj.second)
                    val dx = s.x - screenX
                    val dy = s.y - screenY
                    val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    if (dist < snapThresholdPx && dist < bestDist) {
                        bestDist = dist
                        bestResult = SnapResult(proj.first, proj.second, null, SnapType.SEGMENT, layer.ifEmpty { null })
                    }
                }
            }
        } catch (_: Exception) {
            // Safety net — never crash on snap
        }

        return bestResult
    }

    /**
     * Project point (px, py) onto line segment (ax, ay)→(bx, by).
     * Returns the closest point on the segment.
     */
    private fun projectPointOnSegment(
        px: Double, py: Double,
        ax: Double, ay: Double,
        bx: Double, by: Double
    ): Pair<Double, Double> {
        val abx = bx - ax
        val aby = by - ay
        val lenSq = abx * abx + aby * aby
        if (lenSq < 1e-12) return Pair(ax, ay) // Degenerate segment
        val t = ((px - ax) * abx + (py - ay) * aby) / lenSq
        val clamped = t.coerceIn(0.0, 1.0)
        return Pair(ax + clamped * abx, ay + clamped * aby)
    }

    // ════════════════════════════════════════════════════
    // Measure Finish Panel Actions
    // ════════════════════════════════════════════════════

    /** Save the current measurement as a permanent annotation. */
    fun saveMeasurement() {
        val points = modeController.collectedPoints
        when (modeController.measureSubMode) {
            MeasureSubMode.DISTANCE -> {
                if (points.size >= 2) saveMeasureLineAnnotation(points)
            }
            MeasureSubMode.AREA -> {
                if (points.size >= 3) saveMeasureAreaAnnotation(points)
            }
        }
        modeController.clearPoints()
        syncModeState()
        _uiState.value = _uiState.value.copy(showMeasureFinishPanel = false)
    }

    /** Clear the measurement without saving. */
    fun clearMeasurement() {
        modeController.clearPoints()
        syncModeState()
        _uiState.value = _uiState.value.copy(showMeasureFinishPanel = false)
    }

    /** Dismiss the finish panel (stays in measurement for further editing). */
    fun dismissMeasureFinishPanel() {
        _uiState.value = _uiState.value.copy(showMeasureFinishPanel = false)
    }

    // ════════════════════════════════════════════════════
    // Annotation Persistence (for Plot & Measure results)
    // ════════════════════════════════════════════════════

    fun finishAndSave() {
        val points = modeController.collectedPoints
        if (points.isEmpty()) return

        when (modeController.currentMode) {
            MapMode.PLOT -> {
                when (modeController.plotSubMode) {
                    PlotSubMode.POINT -> { /* Points are saved immediately on tap */ }
                    PlotSubMode.LINE -> {
                        if (points.size >= 2) saveLineAnnotation(points)
                    }
                    PlotSubMode.POLYGON -> {
                        if (points.size >= 3) savePolygonAnnotation(points)
                    }
                }
            }
            MapMode.MEASURE -> {
                // Show finish choice panel instead of auto-saving
                // Return early — do NOT clear points yet
                _uiState.value = _uiState.value.copy(showMeasureFinishPanel = true)
                return
            }
            else -> {}
        }

        modeController.clearPoints()
        syncModeState()
    }

    private fun createPointAnnotation(worldX: Double, worldY: Double, z: Double?) {
        viewModelScope.launch {
            val count = mapRepository.countPointAnnotations(mapId)
            val name = "Placemark ${count + 1}"
            val json = MapSerializationUtils.pointsToJson(listOf(MapPoint(worldX, worldY)))
            val id = mapRepository.insertAnnotation(
                MapAnnotation(
                    mapId = mapId,
                    type = "POINT",
                    pointsJson = json,
                    name = name,
                    elevation = z,
                    layer = "Default"
                )
            )
            // Auto-select the newly created point
            val created = mapRepository.getAnnotationById(id)
            if (created != null) {
                selectAnnotation(created)
            }
        }
    }

    private fun saveLineAnnotation(points: List<MapVertex>) {
        viewModelScope.launch {
            val mapPoints = points.map { MapPoint(it.x, it.y) }
            val json = MapSerializationUtils.pointsToJson(mapPoints)
            val distance = modeController.computeDistance()
            val id = mapRepository.insertAnnotation(
                MapAnnotation(
                    mapId = mapId,
                    type = "LINE",
                    pointsJson = json,
                    name = "Line",
                    distance = distance
                )
            )
            val created = mapRepository.getAnnotationById(id)
            if (created != null) selectAnnotation(created)
        }
    }

    private fun savePolygonAnnotation(points: List<MapVertex>) {
        viewModelScope.launch {
            val mapPoints = points.map { MapPoint(it.x, it.y) }
            val json = MapSerializationUtils.pointsToJson(mapPoints)
            val distance = modeController.computePerimeter()
            val area = modeController.computeArea()
            val id = mapRepository.insertAnnotation(
                MapAnnotation(
                    mapId = mapId,
                    type = "POLYGON",
                    pointsJson = json,
                    name = "Area",
                    distance = distance,
                    area = area
                )
            )
            val created = mapRepository.getAnnotationById(id)
            if (created != null) selectAnnotation(created)
        }
    }

    /**
     * Save a measurement-mode LINE with auto-naming "Distance N".
     */
    private fun saveMeasureLineAnnotation(points: List<MapVertex>) {
        viewModelScope.launch {
            val mapPoints = points.map { MapPoint(it.x, it.y) }
            val json = MapSerializationUtils.pointsToJson(mapPoints)
            val distance = modeController.computeDistance()
            val existing = _uiState.value.annotations.count {
                it.type == "LINE" && it.name.startsWith("Distance")
            }
            val name = "Distance ${existing + 1}"
            val id = mapRepository.insertAnnotation(
                MapAnnotation(
                    mapId = mapId,
                    type = "LINE",
                    pointsJson = json,
                    name = name,
                    description = "%.2f m".format(distance),
                    distance = distance
                )
            )
            val created = mapRepository.getAnnotationById(id)
            if (created != null) selectAnnotation(created)
        }
    }

    /**
     * Save a measurement-mode POLYGON with auto-naming "Area N".
     */
    private fun saveMeasureAreaAnnotation(points: List<MapVertex>) {
        viewModelScope.launch {
            val mapPoints = points.map { MapPoint(it.x, it.y) }
            val json = MapSerializationUtils.pointsToJson(mapPoints)
            val distance = modeController.computePerimeter()
            val area = modeController.computeArea()
            val existing = _uiState.value.annotations.count {
                it.type == "POLYGON" && it.name.startsWith("Area")
            }
            val name = "Area ${existing + 1}"
            val id = mapRepository.insertAnnotation(
                MapAnnotation(
                    mapId = mapId,
                    type = "POLYGON",
                    pointsJson = json,
                    name = name,
                    description = "%.2f m² | P: %.2f m".format(area, distance),
                    distance = distance,
                    area = area
                )
            )
            val created = mapRepository.getAnnotationById(id)
            if (created != null) selectAnnotation(created)
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
            if (_uiState.value.selectedAnnotationId == annotation.id) {
                deselectAnnotation()
            }
        }
    }

    fun parseAnnotationPoints(json: String): List<MapPoint> {
        return MapSerializationUtils.parseJsonToPoints(json)
    }

    // ════════════════════════════════════════════════════
    // Annotation Selection & Detail Sheet
    // ════════════════════════════════════════════════════

    fun selectAnnotation(annotation: MapAnnotation) {
        _uiState.value = _uiState.value.copy(
            selectedAnnotation = annotation,
            selectedAnnotationId = annotation.id,
            showDetailSheet = true,
            showTapTooltip = false
        )
    }

    fun deselectAnnotation() {
        _uiState.value = _uiState.value.copy(
            selectedAnnotation = null,
            selectedAnnotationId = null,
            showDetailSheet = false
        )
    }

    fun dismissDetailSheet() {
        _uiState.value = _uiState.value.copy(showDetailSheet = false)
    }

    fun updateAnnotationDetails(
        annotationId: Long,
        name: String? = null,
        description: String? = null,
        layer: String? = null
    ) {
        viewModelScope.launch {
            val existing = mapRepository.getAnnotationById(annotationId) ?: return@launch
            val updated = existing.copy(
                name = name ?: existing.name,
                description = description ?: existing.description,
                layer = layer ?: existing.layer
            )
            mapRepository.updateAnnotation(updated)
            _uiState.value = _uiState.value.copy(selectedAnnotation = updated)
        }
    }

    fun deleteSelectedAnnotation() {
        val selected = _uiState.value.selectedAnnotation ?: return
        viewModelScope.launch {
            mapRepository.deleteAnnotation(selected)
            deselectAnnotation()
        }
    }

    /**
     * Hit-test: find the annotation closest to the given world coordinate.
     * Returns null if nothing is within the tap threshold.
     */
    private fun hitTestAnnotation(worldX: Double, worldY: Double): MapAnnotation? {
        val annotations = _uiState.value.annotations
        if (annotations.isEmpty()) return null

        // Threshold in world units (adjusted by scale for consistent screen-size tap target)
        val threshold = 20.0 / transformEngine.scale

        var closest: MapAnnotation? = null
        var closestDist = Double.MAX_VALUE

        for (ann in annotations) {
            val points = MapSerializationUtils.parseJsonToPoints(ann.pointsJson)
            for (pt in points) {
                val dx = pt.x - worldX
                val dy = pt.y - worldY
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < threshold && dist < closestDist) {
                    closestDist = dist
                    closest = ann
                }
            }
        }
        return closest
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
        val gpsX = _uiState.value.gpsLocalX
        val gpsY = _uiState.value.gpsLocalY
        if (gpsX != null && gpsY != null) {
            centerOnMapPoint(gpsX, gpsY)
        }
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

    fun toggleTargetMode() {
        val newMode = !_uiState.value.isTargetMode
        _uiState.value = _uiState.value.copy(isTargetMode = newMode)
        
        // If enabling target mode, immediately center on GPS if known
        if (newMode) {
            val lat = _uiState.value.gpsLat
            val lng = _uiState.value.gpsLng
            if (lat != null && lng != null) {
                // Trigger center
                centerOnGps() 
            }
        }
    }

    private suspend fun updateGpsPosition(lat: Double, lng: Double, accuracy: Float?) {
        val format = _uiState.value.coordinateFormat
        
        // Format coordinates
        val (zone, east, north) = when (format) {
            CoordinateFormat.UTM -> {
               // We need a proper utils helper that returns parts. 
               // optimizing: just parse the formatted string or update CoordinateUtils later.
               // For now, let's assume CoordinateUtils.format returns a single string and we parse it 
               // OR we just use the existing CoordinateUtils and split manually if it's standard format.
               // Let's rely on CoordinateUtils.format for now and improve split in UI or here.
               // Actually, Avenza style wants Zone, Easting, Northing separate. 
               // I'll do a quick partial parsing or update CoordinateUtils. 
               // For safety, I'll do basic splitting here based on known format "Zone X, E:..., N:..."
               val full = CoordinateUtils.format(lat, lng, format)
               val parts = full.split(", ")
               if (parts.size >= 3) Triple(parts[0], parts[1], parts[2]) else Triple("", "", full)
            }
            else -> Triple("", "", CoordinateUtils.format(lat, lng, format))
        }

        // Use GeoPDF pipeline if available, otherwise fallback to GpsCalibrationManager
        if (geoPdfRepository.hasValidGeoPdf) {
            val pixel = geoPdfRepository.gpsToPixel(lat, lng)
            val debugInfo = geoPdfRepository.getDebugInfo(lat, lng)
            
            _uiState.value = _uiState.value.copy(
                gpsLat = lat,
                gpsLng = lng,
                gpsLocalX = pixel?.x,
                gpsLocalY = pixel?.y,
                gpsAccuracy = accuracy,
                geoPdfDebugInfo = debugInfo
            )
            
            // Auto-center if Target Mode is active
            if (_uiState.value.isTargetMode && pixel != null) {
                 centerOnMapPoint(pixel.x, pixel.y)
            }
            
        } else {
            val calibrationData = gpsCalibrationManager.calibrationFlow.first()
            val (localX, localY) = gpsCalibrationManager.transformToLocal(lat, lng, calibrationData)
            
            _uiState.value = _uiState.value.copy(
                gpsLat = lat,
                gpsLng = lng,
                gpsLocalX = localX,
                gpsLocalY = localY,
                gpsAccuracy = accuracy,
                geoPdfDebugInfo = null
            )
            
            // Auto-center if Target Mode is active
             if (_uiState.value.isTargetMode) {
                 centerOnMapPoint(localX, localY)
            }
        }
    }

    private fun centerOnMapPoint(x: Double, y: Double) {
        // Calculate pan required to center this point
        val canvasW = _uiState.value.canvasWidth
        val canvasH = _uiState.value.canvasHeight
        if (canvasW > 0 && canvasH > 0) {
            val targetScreen = transformEngine.worldToScreen(x, y)
             val dx = canvasW / 2f - targetScreen.x
             val dy = canvasH / 2f - targetScreen.y
             // Apply pan immediately
             transformEngine.applyPan(dx, dy)
             syncTransformState()
        }
    }

    // ════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════

    private fun findNearestZ(x: Double, y: Double, dxf: DxfFile?): Double? {
        if (dxf == null) return null
        var minDist = Double.MAX_VALUE
        var bestZ: Double? = null

        for (entity in dxf.entities) {
            when (entity) {
                is DxfEntity.Point -> {
                    val d = sqrt(
                        (entity.x - x) * (entity.x - x) + (entity.y - y) * (entity.y - y)
                    )
                    if (d < minDist) {
                        minDist = d
                        bestZ = entity.z
                    }
                }
                is DxfEntity.Line -> {
                    val d1 = sqrt(
                        (entity.x1 - x) * (entity.x1 - x) + (entity.y1 - y) * (entity.y1 - y)
                    )
                    val d2 = sqrt(
                        (entity.x2 - x) * (entity.x2 - x) + (entity.y2 - y) * (entity.y2 - y)
                    )
                    if (d1 < minDist) { minDist = d1; bestZ = entity.z1 }
                    if (d2 < minDist) { minDist = d2; bestZ = entity.z2 }
                }
                is DxfEntity.Polyline -> {
                    entity.vertices.forEach { v ->
                        val d = sqrt((v.x - x) * (v.x - x) + (v.y - y) * (v.y - y))
                        if (d < minDist) { minDist = d; bestZ = v.z }
                    }
                }
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
}
