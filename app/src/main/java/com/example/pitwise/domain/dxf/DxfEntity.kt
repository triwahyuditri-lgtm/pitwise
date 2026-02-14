package com.example.pitwise.domain.dxf

/**
 * Base class for all DXF entities.
 */
sealed class DxfEntity {
    abstract val layer: String
    abstract val color: Int

    data class Point(
        val x: Double,
        val y: Double,
        val z: Double = 0.0,
        override val layer: String,
        override val color: Int
    ) : DxfEntity()

    data class Line(
        val start: DxfVertex,
        val end: DxfVertex,
        override val layer: String,
        override val color: Int
    ) : DxfEntity()

    data class Polyline(
        val vertices: List<DxfVertex>,
        val isClosed: Boolean,
        override val layer: String,
        override val color: Int
    ) : DxfEntity()

    data class Insert(
        val blockName: String,
        val insertionPoint: DxfVertex,
        val scaleX: Double = 1.0,
        val scaleY: Double = 1.0,
        val scaleZ: Double = 1.0,
        val rotation: Double = 0.0,
        override val layer: String,
        override val color: Int
    ) : DxfEntity()

    fun transform(
        scaleX: Double, scaleY: Double, scaleZ: Double,
        rotation: Double,
        tx: Double, ty: Double, tz: Double
    ): DxfEntity {
        return when (this) {
            is Point -> {
                val v = DxfVertex(x, y, z)
                val tv = DxfTransformUtils.transform(v, scaleX, scaleY, scaleZ, rotation, tx, ty, tz)
                copy(x = tv.x, y = tv.y, z = tv.z)
            }
            is Line -> {
                val ts = DxfTransformUtils.transform(start, scaleX, scaleY, scaleZ, rotation, tx, ty, tz)
                val te = DxfTransformUtils.transform(end, scaleX, scaleY, scaleZ, rotation, tx, ty, tz)
                copy(start = ts, end = te)
            }
            is Polyline -> {
                val tvs = vertices.map { DxfTransformUtils.transform(it, scaleX, scaleY, scaleZ, rotation, tx, ty, tz) }
                copy(vertices = tvs)
            }
            is Insert -> {
                // Transforming an Insert is complex (nested transform).
                // Usually we explode Inserts, so we might not need to transform the Insert itself 
                // unless we keep the hierarchy.
                // For now, let's just transform its insertion point and adjusts scales/rotation?
                // Actually, if we explode, we won't call transform on Insert directly, 
                // we call it on the *content* of the block.
                // But if we do need to transform an Insert (e.g. nested block):
                // The new insertion point is transformed.
                // Scale multiplies? Rotation adds?
                val tip = DxfTransformUtils.transform(insertionPoint, scaleX, scaleY, scaleZ, rotation, tx, ty, tz)
                copy(
                    insertionPoint = tip,
                    scaleX = this.scaleX * scaleX,
                    scaleY = this.scaleY * scaleY,
                    scaleZ = this.scaleZ * scaleZ,
                    rotation = this.rotation + rotation
                )
            }
        }
    }
}

data class DxfVertex(
    val x: Double,
    val y: Double,
    val z: Double = 0.0
)
