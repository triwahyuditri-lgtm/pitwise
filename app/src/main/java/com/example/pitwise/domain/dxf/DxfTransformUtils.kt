package com.example.pitwise.domain.dxf

import kotlin.math.cos
import kotlin.math.sin

/**
 * Geometric utilities for DXF transformation logic.
 */
object DxfTransformUtils {

    fun transform(
        vertex: DxfVertex,
        scaleX: Double,
        scaleY: Double,
        scaleZ: Double,
        rotationDegrees: Double,
        translateX: Double,
        translateY: Double,
        translateZ: Double
    ): DxfVertex {
        // 1. Scale
        var x = vertex.x * scaleX
        var y = vertex.y * scaleY
        var z = vertex.z * scaleZ

        // 2. Rotate (Z-axis rotation only, standard for 2D DXF Inserts)
        if (rotationDegrees != 0.0) {
            val rad = Math.toRadians(rotationDegrees)
            val cos = cos(rad)
            val sin = sin(rad)
            val rx = x * cos - y * sin
            val ry = x * sin + y * cos
            x = rx
            y = ry
        }

        // 3. Translate
        x += translateX
        y += translateY
        z += translateZ

        return DxfVertex(x, y, z)
    }

    // Helper for applying same transform to a list of vertices
    fun transformVertices(
        vertices: List<DxfVertex>,
        scaleX: Double, scaleY: Double, scaleZ: Double,
        rotation: Double,
        tx: Double, ty: Double, tz: Double
    ): List<DxfVertex> {
        return vertices.map { transform(it, scaleX, scaleY, scaleZ, rotation, tx, ty, tz) }
    }
}
