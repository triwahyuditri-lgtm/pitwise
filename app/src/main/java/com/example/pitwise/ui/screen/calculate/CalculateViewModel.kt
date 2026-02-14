package com.example.pitwise.ui.screen.calculate

import android.content.Context
import androidx.lifecycle.ViewModel
import com.example.pitwise.domain.calculator.CalculatorRouter
import com.example.pitwise.domain.calculator.CoalTonnageCalculator
import com.example.pitwise.domain.calculator.CutFillCalculator
import com.example.pitwise.domain.calculator.HaulingCycleCalculator
import com.example.pitwise.domain.calculator.MeasurementType
import com.example.pitwise.domain.calculator.ObVolumeCalculator
import com.example.pitwise.domain.calculator.RoadGradeCalculator
import com.example.pitwise.domain.model.CoalTonnageInput
import com.example.pitwise.domain.model.CutFillInput
import com.example.pitwise.domain.model.CutFillOutput
import com.example.pitwise.domain.model.HaulingCycleInput
import com.example.pitwise.domain.model.HaulingCycleOutput
import com.example.pitwise.domain.model.ObVolumeInput
import com.example.pitwise.domain.model.ObVolumeOutput
import com.example.pitwise.domain.model.CoalTonnageOutput
import com.example.pitwise.domain.model.RoadGradeInput
import com.example.pitwise.domain.model.RoadGradeOutput
import com.example.pitwise.domain.model.ShareCardData
import com.example.pitwise.domain.reporting.ShareCardGenerator
import com.example.pitwise.domain.reporting.ShareIntentHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

enum class CalculatorType {
    OB_VOLUME, COAL_TONNAGE, HAULING_CYCLE, ROAD_GRADE, CUT_FILL
}

data class CalculateUiState(
    val selectedCalculator: CalculatorType? = null,
    val obResult: ObVolumeOutput? = null,
    val coalResult: CoalTonnageOutput? = null,
    val haulingResult: HaulingCycleOutput? = null,
    val gradeResult: RoadGradeOutput? = null,
    val cutFillResult: CutFillOutput? = null,

    // ── Measurement context from Map ──
    val measurementType: MeasurementType? = null,
    val measurementValue: Double = 0.0,
    val availableCalculators: List<CalculatorType> = CalculatorRouter.getAvailableCalculators(null),
    val isContextual: Boolean = false,

    // Auto-fill from measurement
    val prefillArea: Double = 0.0,
    val prefillDistance: Double = 0.0,

    // Track if user modified the prefilled value
    val isModifiedFromMap: Boolean = false
)

@HiltViewModel
class CalculateViewModel @Inject constructor(
    private val obCalc: ObVolumeCalculator,
    private val coalCalc: CoalTonnageCalculator,
    private val haulingCalc: HaulingCycleCalculator,
    private val gradeCalc: RoadGradeCalculator,
    private val cutFillCalc: CutFillCalculator,
    private val shareCardGenerator: ShareCardGenerator,
    private val shareIntentHelper: ShareIntentHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalculateUiState())
    val uiState: StateFlow<CalculateUiState> = _uiState.asStateFlow()

    /**
     * Set measurement context from Map.
     * Filters available calculators and pre-fills the relevant input.
     */
    fun setMeasurementContext(typeStr: String?, value: Double) {
        if (typeStr == null || value <= 0.0) return

        val type = try {
            MeasurementType.valueOf(typeStr.uppercase())
        } catch (_: IllegalArgumentException) {
            return
        }

        val available = CalculatorRouter.getAvailableCalculators(type)

        _uiState.value = _uiState.value.copy(
            measurementType = type,
            measurementValue = value,
            availableCalculators = available,
            isContextual = true,
            prefillArea = if (type == MeasurementType.AREA) value else 0.0,
            prefillDistance = if (type == MeasurementType.DISTANCE) value else 0.0,
            isModifiedFromMap = false
        )
    }

    fun selectCalculator(type: CalculatorType) {
        _uiState.value = _uiState.value.copy(
            selectedCalculator = type,
            obResult = null,
            coalResult = null,
            haulingResult = null,
            gradeResult = null,
            cutFillResult = null
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedCalculator = null,
            obResult = null,
            coalResult = null,
            haulingResult = null,
            gradeResult = null,
            cutFillResult = null
        )
    }

    /**
     * Mark that the user has manually modified the pre-filled input.
     */
    fun markModifiedFromMap() {
        if (_uiState.value.isContextual && !_uiState.value.isModifiedFromMap) {
            _uiState.value = _uiState.value.copy(isModifiedFromMap = true)
        }
    }

    /**
     * Clear the measurement context (user pressed "Clear source").
     */
    fun clearMeasurementContext() {
        _uiState.value = _uiState.value.copy(
            measurementType = null,
            measurementValue = 0.0,
            isContextual = false,
            prefillArea = 0.0,
            prefillDistance = 0.0,
            availableCalculators = CalculatorRouter.getAvailableCalculators(null),
            isModifiedFromMap = false
        )
    }

    fun calculateOb(area: Double, thickness: Double, swellFactor: Double, density: Double) {
        val input = ObVolumeInput(area, thickness, swellFactor, density)
        val result = obCalc.calculate(input)
        _uiState.value = _uiState.value.copy(obResult = result)
    }

    fun calculateCoal(area: Double, seamThickness: Double, density: Double, recovery: Double) {
        val input = CoalTonnageInput(area, seamThickness, density, recovery)
        val result = coalCalc.calculate(input)
        _uiState.value = _uiState.value.copy(coalResult = result)
    }

    fun calculateHauling(distance: Double, speedLoaded: Double, speedEmpty: Double, loadTime: Double, dumpTime: Double, vessel: Double) {
        val input = HaulingCycleInput(distance, speedLoaded, speedEmpty, loadTime, dumpTime, vessel)
        val result = haulingCalc.calculate(input)
        _uiState.value = _uiState.value.copy(haulingResult = result)
    }

    fun calculateGrade(horizontalDist: Double, elevStart: Double, elevEnd: Double) {
        val input = RoadGradeInput(horizontalDist, elevStart, elevEnd)
        val result = gradeCalc.calculate(input)
        _uiState.value = _uiState.value.copy(gradeResult = result)
    }

    fun calculateCutFill(existingElev: Double, targetElev: Double, width: Double, length: Double) {
        val input = CutFillInput(existingElev, targetElev, width, length)
        val result = cutFillCalc.calculate(input)
        _uiState.value = _uiState.value.copy(cutFillResult = result)
    }

    fun shareResult(context: Context, title: String, lines: List<Pair<String, String>>) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val data = ShareCardData(
            title = title,
            resultLines = lines,
            timestamp = timestamp,
            shift = "Day",
            unitName = "Field Unit",
            aiRecommendation = null
        )
        val bitmap = shareCardGenerator.generate(data)
        val intent = shareIntentHelper.createChooserIntent(context, bitmap, "PITWISE Calculation Result")
        context.startActivity(intent)
    }
}
