package com.example.pitwise.domain.dxf

data class DxfLayer(
    val name: String,
    val colorIndex: Int = 7, // Default to White
    val isVisible: Boolean = true
)
