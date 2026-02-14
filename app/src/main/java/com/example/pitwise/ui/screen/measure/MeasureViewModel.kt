package com.example.pitwise.ui.screen.measure

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pitwise.data.local.entity.MapAnnotation
import com.example.pitwise.domain.dxf.SimpleDxfModel
import com.example.pitwise.domain.dxf.SimpleDxfParser
import com.example.pitwise.domain.map.MapPoint
import com.example.pitwise.domain.map.MapRepository
import com.example.pitwise.domain.map.PdfRendererEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.sqrt

enum class MeasureMode { DISTANCE, AREA }

data class MeasureUiState(
    val mode: MeasureMode = MeasureMode.DISTANCE,
    val points: List<MapPoint> = emptyList(),
    val totalDistance: Double = 0.0,
    val totalArea: Double = 0.0,
    val isActive: Boolean = false,
    
    // Map Background
    val pdfBitmap: Bitmap? = null,
    val dxfModel: SimpleDxfModel? = null,
    val isLoadingMap: Boolean = false
)

@HiltViewModel
class MeasureViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mapRepository: MapRepository,
    private val pdfRenderer: PdfRendererEngine,
    private val dxfParser: SimpleDxfParser
) : ViewModel() {

    private val _uiState = MutableStateFlow(MeasureUiState())
    val uiState: StateFlow<MeasureUiState> = _uiState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val mapAnnotations: StateFlow<List<MapAnnotation>> = mapRepository.activeMap
        .flatMapLatest { map ->
            if (map != null) mapRepository.getAnnotationsForMap(map.id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Observe active map changes to load content
        viewModelScope.launch {
            mapRepository.activeMap.collectLatest { mapEntry ->
                if (mapEntry != null) {
                    loadMapContent(mapEntry.uri, mapEntry.type)
                } else {
                    _uiState.value = _uiState.value.copy(pdfBitmap = null, dxfModel = null)
                }
            }
        }
    }

    private suspend fun loadMapContent(uriString: String, type: String) {
        _uiState.value = _uiState.value.copy(isLoadingMap = true)
        try {
            val uri = Uri.parse(uriString)
            if (type == "PDF") {
                val data = pdfRenderer.renderFirstPage(uri)
                _uiState.value = _uiState.value.copy(pdfBitmap = data?.bitmap, dxfModel = null, isLoadingMap = false)
            } else if (type == "DXF") {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (content != null) {
                    val dxf = dxfParser.parse(content)
                    _uiState.value = _uiState.value.copy(dxfModel = dxf, pdfBitmap = null, isLoadingMap = false)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.value = _uiState.value.copy(isLoadingMap = false)
        }
    }

    fun setMode(mode: MeasureMode) {
        _uiState.value = _uiState.value.copy(mode = mode, isActive = true)
    }

    fun addPoint(x: Double, y: Double) {
        val current = _uiState.value
        val newPoints = current.points + MapPoint(x, y)

        val distance = computePolylineLength(newPoints)
        val area = if (current.mode == MeasureMode.AREA && newPoints.size >= 3) {
            computePolygonArea(newPoints)
        } else 0.0

        _uiState.value = current.copy(
            points = newPoints,
            totalDistance = distance,
            totalArea = area
        )
    }

    fun undoLastPoint() {
        val current = _uiState.value
        if (current.points.isEmpty()) return
        val newPoints = current.points.dropLast(1)

        val distance = computePolylineLength(newPoints)
        val area = if (current.mode == MeasureMode.AREA && newPoints.size >= 3) {
            computePolygonArea(newPoints)
        } else 0.0

        _uiState.value = current.copy(
            points = newPoints,
            totalDistance = distance,
            totalArea = area
        )
    }

    fun reset() {
        // Keep the map, just reset points
        val current = _uiState.value
        _uiState.value = current.copy(
            points = emptyList(),
            totalDistance = 0.0,
            totalArea = 0.0,
            isActive = true
        )
    }

    private fun computePolylineLength(points: List<MapPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until points.size - 1) {
            val dx = points[i + 1].x - points[i].x
            val dy = points[i + 1].y - points[i].y
            total += sqrt(dx * dx + dy * dy)
        }
        return total
    }

    private fun computePolygonArea(points: List<MapPoint>): Double {
        // Shoelace formula
        if (points.size < 3) return 0.0
        var sum = 0.0
        for (i in points.indices) {
            val j = (i + 1) % points.size
            sum += points[i].x * points[j].y
            sum -= points[j].x * points[i].y
        }
        return abs(sum) / 2.0
    }
}
