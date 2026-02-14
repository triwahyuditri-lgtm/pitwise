package com.example.pitwise.domain.geopdf

import kotlin.math.abs

/**
 * 2D affine transformation matrix for mapping between coordinate spaces.
 *
 * Matrix form:
 * ```
 * | a  b  tx |   | x |   | a*x + b*y + tx |
 * | c  d  ty | × | y | = | c*x + d*y + ty |
 * | 0  0  1  |   | 1 |   |       1        |
 * ```
 *
 * Used to map projected coordinates (UTM) → PDF pixel coordinates.
 */
data class GeoAffineTransform(
    val a: Double,
    val b: Double,
    val tx: Double,
    val c: Double,
    val d: Double,
    val ty: Double
) {

    /**
     * Apply forward transform: input → output.
     * For the GeoPDF pipeline: projected (UTM) → pixel (PDF).
     */
    fun transform(x: Double, y: Double): Pair<Double, Double> {
        val outX = a * x + b * y + tx
        val outY = c * x + d * y + ty
        return Pair(outX, outY)
    }

    /**
     * Apply inverse transform: output → input.
     * For the GeoPDF pipeline: pixel (PDF) → projected (UTM).
     */
    fun inverse(x: Double, y: Double): Pair<Double, Double>? {
        val det = a * d - b * c
        if (abs(det) < 1e-12) return null // Non-invertible

        val invA = d / det
        val invB = -b / det
        val invC = -c / det
        val invD = a / det
        val invTx = (b * ty - d * tx) / det
        val invTy = (c * tx - a * ty) / det

        val outX = invA * x + invB * y + invTx
        val outY = invC * x + invD * y + invTy
        return Pair(outX, outY)
    }

    /**
     * Format as human-readable string for debug display.
     */
    fun toDebugString(): String {
        return "| %.6f  %.6f  %.2f |\n| %.6f  %.6f  %.2f |\n| 0        0        1    |".format(
            a, b, tx, c, d, ty
        )
    }

    companion object {

        /**
         * Compute the affine transform that best maps [src] points to [dst] points.
         *
         * Uses least-squares solution for ≥3 point pairs.
         * The system of equations is:
         *   dstX = a * srcX + b * srcY + tx
         *   dstY = c * srcX + d * srcY + ty
         *
         * This is solved as two independent 3-variable least-squares problems.
         *
         * @param src Source points (e.g. projected coordinates)
         * @param dst Destination points (e.g. pixel coordinates)
         * @return The affine transform, or null if the system is underdetermined or singular.
         */
        fun fromControlPoints(
            src: List<Pair<Double, Double>>,
            dst: List<Pair<Double, Double>>
        ): GeoAffineTransform? {
            if (src.size < 3 || src.size != dst.size) return null

            val n = src.size

            // Build the normal equations for least squares: AᵀA * x = Aᵀb
            // For the X component: dstX = a * srcX + b * srcY + tx
            // For the Y component: dstY = c * srcX + d * srcY + ty

            // Accumulate AᵀA and Aᵀb
            var sumX2 = 0.0    // Σ srcX²
            var sumY2 = 0.0    // Σ srcY²
            var sumXY = 0.0    // Σ srcX·srcY
            var sumX = 0.0     // Σ srcX
            var sumY = 0.0     // Σ srcY

            var sumDstX_X = 0.0  // Σ dstX·srcX
            var sumDstX_Y = 0.0  // Σ dstX·srcY
            var sumDstX = 0.0    // Σ dstX
            var sumDstY_X = 0.0  // Σ dstY·srcX
            var sumDstY_Y = 0.0  // Σ dstY·srcY
            var sumDstY = 0.0    // Σ dstY

            for (i in 0 until n) {
                val (sx, sy) = src[i]
                val (dx, dy) = dst[i]

                sumX2 += sx * sx
                sumY2 += sy * sy
                sumXY += sx * sy
                sumX += sx
                sumY += sy

                sumDstX_X += dx * sx
                sumDstX_Y += dx * sy
                sumDstX += dx
                sumDstY_X += dy * sx
                sumDstY_Y += dy * sy
                sumDstY += dy
            }

            // Normal equation matrix (3×3, same for both X and Y components):
            // | sumX2  sumXY  sumX |   | a/c  |   | sumDstX_X / sumDstY_X |
            // | sumXY  sumY2  sumY | × | b/d  | = | sumDstX_Y / sumDstY_Y |
            // | sumX   sumY   n    |   | tx/ty|   | sumDstX   / sumDstY   |

            val matrix = arrayOf(
                doubleArrayOf(sumX2, sumXY, sumX),
                doubleArrayOf(sumXY, sumY2, sumY),
                doubleArrayOf(sumX, sumY, n.toDouble())
            )

            val rhsX = doubleArrayOf(sumDstX_X, sumDstX_Y, sumDstX)
            val rhsY = doubleArrayOf(sumDstY_X, sumDstY_Y, sumDstY)

            val solX = solveLinearSystem(matrix.map { it.clone() }.toTypedArray(), rhsX.clone())
                ?: return null
            val solY = solveLinearSystem(matrix.map { it.clone() }.toTypedArray(), rhsY.clone())
                ?: return null

            return GeoAffineTransform(
                a = solX[0], b = solX[1], tx = solX[2],
                c = solY[0], d = solY[1], ty = solY[2]
            )
        }

        /**
         * Solve a 3×3 linear system using Gaussian elimination with partial pivoting.
         *
         * @param matrix 3×3 coefficient matrix (modified in place)
         * @param rhs 3-element right-hand side vector (modified in place)
         * @return Solution vector [x0, x1, x2], or null if singular.
         */
        private fun solveLinearSystem(
            matrix: Array<DoubleArray>,
            rhs: DoubleArray
        ): DoubleArray? {
            val n = 3

            // Forward elimination with partial pivoting
            for (col in 0 until n) {
                // Find pivot
                var maxVal = abs(matrix[col][col])
                var maxRow = col
                for (row in col + 1 until n) {
                    if (abs(matrix[row][col]) > maxVal) {
                        maxVal = abs(matrix[row][col])
                        maxRow = row
                    }
                }

                if (maxVal < 1e-12) return null // Singular

                // Swap rows
                if (maxRow != col) {
                    val tmpRow = matrix[col]
                    matrix[col] = matrix[maxRow]
                    matrix[maxRow] = tmpRow
                    val tmpRhs = rhs[col]
                    rhs[col] = rhs[maxRow]
                    rhs[maxRow] = tmpRhs
                }

                // Eliminate below
                for (row in col + 1 until n) {
                    val factor = matrix[row][col] / matrix[col][col]
                    for (j in col until n) {
                        matrix[row][j] -= factor * matrix[col][j]
                    }
                    rhs[row] -= factor * rhs[col]
                }
            }

            // Back substitution
            val result = DoubleArray(n)
            for (i in n - 1 downTo 0) {
                var sum = rhs[i]
                for (j in i + 1 until n) {
                    sum -= matrix[i][j] * result[j]
                }
                if (abs(matrix[i][i]) < 1e-12) return null
                result[i] = sum / matrix[i][i]
            }

            return result
        }
    }
}
