package com.example.pitwise.ui.screen.home

import androidx.lifecycle.ViewModel
import com.example.pitwise.domain.map.MapRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Lightweight ViewModel to expose MapRepository to HomeScreen
 * for checking loaded map count.
 */
@HiltViewModel
class HomeStatusHelper @Inject constructor(
    val mapRepository: MapRepository
) : ViewModel()
