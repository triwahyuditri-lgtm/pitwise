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
    val workingHours: String = "",
    val targetProduction: String = "",
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
                _uiState.value = _uiState.value.copy(
                    bucketCapacity = (spec.bucketCapacityM3 ?: spec.vesselCapacityM3 ?: 0.0).toString(),
                    fillFactor = (spec.fillFactorDefault ?: 0.85).toString(),
                    cycleTime = (spec.cycleTimeRefSec ?: 0.0).toString()
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
            "workingHours" -> current.copy(workingHours = value)
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
            effectiveWorkingHours = state.workingHours.toDoubleOrNull() ?: 0.0,
            targetProduction = state.targetProduction.toDoubleOrNull() ?: 0.0
        )
        val result = productivityCalc.calculate(input)
        _uiState.value = state.copy(result = result)
    }
}
