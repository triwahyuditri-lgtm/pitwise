package com.example.pitwise.domain.transform

import kotlin.math.abs

/**
 * Immutable 2D Affine Matrix.
 *
 * | a  b  tx |
 * | c  d  ty |
 * | 0  0  1  |
 *
 * Used for high-performance coordinate parsing.
 */
data class AffineMatrix(
    val a: Double,
    val b: Double,
    val tx: Double,
    val c: Double,
    val d: Double,
    val ty: Double
) {
    /**
     * Apply this matrix to a point (x, y).
     *
     * x' = a*x + b*y + tx
     * y' = c*x + d*y + ty
     */
    fun map(x: Double, y: Double): Pair<Double, Double> {
        return Pair(
            a * x + b * y + tx,
            c * x + d * y + ty
        )
    }

    /**
     * Compute the inverse of this matrix.
     * Returns null if the determinant is zero (singular matrix).
     */
    fun invert(): AffineMatrix? {
        val det = a * d - b * c
        if (abs(det) < 1e-12) return null

        val invDet = 1.0 / det

        // Inverse 2x2 part
        // | d  -b |
        // |-c   a | * (1/det)
        val ia = d * invDet
        val ib = -b * invDet
        val ic = -c * invDet
        val id = a * invDet

        // Inverse translation
        // - (ia*tx + ib*ty)
        // - (ic*tx + id*ty)
        val itx = -(ia * tx + ib * ty)
        val ity = -(ic * tx + id * ty)

        return AffineMatrix(ia, ib, itx, ic, id, ity)
    }

    companion object {
        val IDENTITY = AffineMatrix(1.0, 0.0, 0.0, 0.0, 1.0, 0.0)
    }
}
