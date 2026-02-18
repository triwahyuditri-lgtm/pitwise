package com.example.pitwise.ui.screen.productivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pitwise.domain.calculator.ProductivityCalculator
import com.example.pitwise.domain.model.ProductivityInput
import com.example.pitwise.domain.model.ProductivityResult
import com.example.pitwise.domain.model.UnitModelWithBrand
import com.example.pitwise.domain.model.UnitType
import com.example.pitwise.domain.repository.UnitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductivityUiState(
    val unitName: String = "",
    val bucketCapacity: String = "",
    val fillFactor: String = "0.85",
    val cycleTime: String = "",
    val swellFactor: String = "1.0",
    val jobEfficiency: String = "0.83",
    val effectiveWorkingHours: String = "",  // Jam kerja efektif (user input)
    val targetProduction: String = "",
    val dbProductivity: Double? = null,  // From database spec
    val result: ProductivityResult? = null,

    // Unit picker state
    val unitType: UnitType = UnitType.LOADER,
    val units: List<UnitModelWithBrand> = emptyList(),
    val materials: List<String> = emptyList(),
    val selectedMaterial: String = "",
    val showUnitPicker: Boolean = false,
    val searchQuery: String = "",
    val isLoadingUnits: Boolean = false,
    val isLoadingMaterials: Boolean = false
)

@HiltViewModel
class ProductivityViewModel @Inject constructor(
    private val productivityCalc: ProductivityCalculator,
    private val unitRepository: UnitRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductivityUiState())
    val uiState: StateFlow<ProductivityUiState> = _uiState.asStateFlow()

    init {
        loadMaterials()
        loadUnits()
    }

    private fun loadMaterials() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMaterials = true)
            val materials = unitRepository.getMaterials()
            _uiState.value = _uiState.value.copy(
                materials = materials,
                isLoadingMaterials = false
            )
        }
    }

    /**
     * Load units based on the selected UnitType.
     */
    fun loadUnits() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingUnits = true)
            try {
                // Fetch based on current type
                val units = unitRepository.getModelsByUnitType(_uiState.value.unitType)
                _uiState.value = _uiState.value.copy(
                    units = units,
                    isLoadingUnits = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingUnits = false)
            }
        }
    }

    fun setUnitType(type: UnitType) {
        if (_uiState.value.unitType != type) {
            _uiState.value = _uiState.value.copy(unitType = type, unitName = "", selectedMaterial = "")
            loadUnits()
        }
    }

    fun selectUnit(unit: UnitModelWithBrand) {
        _uiState.value = _uiState.value.copy(
            unitName = unit.modelName, // Store just the model name (e.g. "PC-200")
            showUnitPicker = false,
            searchQuery = ""
        )
        // Try to auto-fill if material is already selected
        fetchSpecs()
    }

    fun selectMaterial(material: String) {
        _uiState.value = _uiState.value.copy(selectedMaterial = material)
        fetchSpecs()
    }

    private fun fetchSpecs() {
        val state = _uiState.value
        if (state.unitName.isBlank() || state.selectedMaterial.isBlank()) return

        viewModelScope.launch {
            val spec = unitRepository.findUnitSpec(
                type = state.unitType,
                className = state.unitName,
                material = state.selectedMaterial
            )

            if (spec != null) {
                // For Hauler: use vessel_bcm_or_ton as the capacity
                // For Loader/Dozer: use bucketCapacityLcm or vesselCapacityM3
                val capacity = when (state.unitType) {
                    UnitType.HAULER -> spec.vesselCapacityTon ?: spec.vesselCapacityM3 ?: 0.0
                    else -> spec.bucketCapacityLcm ?: spec.vesselCapacityM3 ?: 0.0
                }
                // Haulers use fillFactor=1.0 (vessel_bcm_or_ton = actual payload)
                val fillFactor = when (state.unitType) {
                    UnitType.HAULER -> 1.0
                    else -> spec.fillFactorDefault ?: 0.85
                }
                _uiState.value = _uiState.value.copy(
                    bucketCapacity = capacity.toString(),
                    fillFactor = fillFactor.toString(),
                    cycleTime = (spec.cycleTimeRefSec ?: 0.0).toString(),
                    swellFactor = (spec.swellFactor ?: 1.0).toString(),
                    jobEfficiency = (spec.jobEfficiency ?: 0.83).toString(),
                    dbProductivity = spec.productivity
                )
            }
        }
    }

    fun toggleUnitPicker(show: Boolean) {
        _uiState.value = _uiState.value.copy(showUnitPicker = show, searchQuery = "")
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun updateField(field: String, value: String) {
        val current = _uiState.value
        _uiState.value = when (field) {
            "unitName" -> current.copy(unitName = value)
            "bucketCapacity" -> current.copy(bucketCapacity = value)
            "fillFactor" -> current.copy(fillFactor = value)
            "cycleTime" -> current.copy(cycleTime = value)
            "swellFactor" -> current.copy(swellFactor = value)
            "jobEfficiency" -> current.copy(jobEfficiency = value)
            "effectiveWorkingHours" -> current.copy(effectiveWorkingHours = value)
            "targetProduction" -> current.copy(targetProduction = value)
            else -> current
        }
    }

    fun calculate() {
        val state = _uiState.value
        val input = ProductivityInput(
            unitName = state.unitName.ifBlank { "Unknown Unit" },
            bucketOrVesselM3 = state.bucketCapacity.toDoubleOrNull() ?: 0.0,
            fillFactor = state.fillFactor.toDoubleOrNull() ?: 0.85,
            cycleTimeActualSec = state.cycleTime.toDoubleOrNull() ?: 0.0,
            swellFactor = state.swellFactor.toDoubleOrNull() ?: 1.0,
            jobEfficiency = state.jobEfficiency.toDoubleOrNull() ?: 0.83,
            effectiveWorkingHours = state.effectiveWorkingHours.toDoubleOrNull() ?: 0.0,
            targetProduction = state.targetProduction.toDoubleOrNull() ?: 0.0,
            dbProductivityPerHour = state.dbProductivity
        )
        val result = productivityCalc.calculate(input)
        _uiState.value = state.copy(result = result)
    }
}
