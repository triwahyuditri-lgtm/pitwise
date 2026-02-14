package com.example.pitwise.domain.dxf

/**
 * Unified model for the parsed DXF file.
 */
data class DxfModel(
    val lines: List<DxfEntity.Line> = emptyList(),
    val polylines: List<DxfEntity.Polyline> = emptyList(),
    val points: List<DxfEntity.Point> = emptyList(),
    val layers: Map<String, DxfLayer> = emptyMap(),
    val bounds: DxfBounds
)

data class DxfBounds(
    val minX: Double,
    val minY: Double,
    val minZ: Double,
    val maxX: Double,
    val maxY: Double,
    val maxZ: Double
)
