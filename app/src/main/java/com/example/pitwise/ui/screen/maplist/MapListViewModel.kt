package com.example.pitwise.ui.screen.maplist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pitwise.data.local.entity.MapEntry
import com.example.pitwise.domain.map.MapRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapListViewModel @Inject constructor(
    private val mapRepository: MapRepository
) : ViewModel() {

    val maps: StateFlow<List<MapEntry>> = mapRepository.allMaps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addMap(name: String, type: String, uri: String) {
        viewModelScope.launch {
            val id = mapRepository.addMap(name, type, uri)
            // Auto-activate if it's the first map
            if (maps.value.isEmpty() || maps.value.size == 1) {
                mapRepository.setActive(id)
            }
        }
    }

    fun deleteMap(map: MapEntry) {
        viewModelScope.launch {
            mapRepository.deleteMap(map)
        }
    }

    fun renameMap(mapId: Long, name: String) {
        viewModelScope.launch {
            mapRepository.renameMap(mapId, name)
        }
    }

    fun setActive(mapId: Long) {
        viewModelScope.launch {
            mapRepository.setActive(mapId)
        }
    }
}
