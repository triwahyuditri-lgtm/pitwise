package com.example.pitwise.ui.screen.unitsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pitwise.data.local.entity.UnitBrand
import com.example.pitwise.data.local.entity.UnitModel
import com.example.pitwise.data.local.entity.UnitSpec
import com.example.pitwise.domain.repository.UnitRepository
import com.example.pitwise.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UnitSettingsUiState(
    val brands: List<UnitBrand> = emptyList(),
    val models: List<UnitModel> = emptyList(),
    val selectedBrand: UnitBrand? = null,
    val selectedModel: UnitModel? = null,
    val selectedSpec: UnitSpec? = null,
    val currentRole: String = "GUEST",
    val isLoading: Boolean = false,
    val message: String? = null,
    val selectedCategory: String = "EXCA" // "EXCA" or "DT"
)

@HiltViewModel
class UnitSettingsViewModel @Inject constructor(
    private val unitRepository: UnitRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UnitSettingsUiState())
    val uiState: StateFlow<UnitSettingsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Get current role
            val session = userRepository.getCurrentSessionSync()
            val role = session?.role ?: "GUEST"

            // Load brands and models
            combine(
                unitRepository.getAllBrands(),
                unitRepository.getAllModels()
            ) { brands, models ->
                Pair(brands, models)
            }.collect { (brands, models) ->
                _uiState.value = _uiState.value.copy(
                    brands = brands,
                    models = models,
                    currentRole = role,
                    isLoading = false
                )
            }
        }
    }

    fun selectCategory(category: String) {
        _uiState.value = _uiState.value.copy(
            selectedCategory = category,
            selectedBrand = null,
            selectedModel = null,
            selectedSpec = null
        )
    }

    fun selectBrand(brand: UnitBrand) {
        _uiState.value = _uiState.value.copy(
            selectedBrand = brand,
            selectedModel = null,
            selectedSpec = null
        )
    }

    fun selectModel(model: UnitModel) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selectedModel = model)
            // Load spec for selected model
            unitRepository.getSpecByModel(model.id).collect { spec ->
                _uiState.value = _uiState.value.copy(selectedSpec = spec)
            }
        }
    }

    fun copyToLocalOverride() {
        val modelId = _uiState.value.selectedModel?.id ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val newModelId = unitRepository.copyToLocalOverride(modelId)
            if (newModelId > 0) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Copied to local override (ID: $newModelId)"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Failed to copy"
                )
            }
        }
    }

    fun saveSpec(spec: UnitSpec) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            unitRepository.saveLocalSpec(spec)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                selectedSpec = spec,
                message = "Spec saved"
            )
        }
    }

    fun publishToGlobal() {
        if (_uiState.value.currentRole != "SUPERADMIN") {
            _uiState.value = _uiState.value.copy(message = "Only superadmin can publish")
            return
        }

        val modelId = _uiState.value.selectedModel?.id ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = unitRepository.publishToGlobal(modelId)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        message = "Published to global successfully"
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        message = "Publish failed: ${error.message}"
                    )
                }
            )
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
