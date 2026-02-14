package com.example.pitwise.domain.geopdf

import org.junit.Assert.*
import org.junit.Test

class AffineTransformTest {

    // ── Identity Transform ──

    @Test
    fun `identity transform maps points to themselves`() {
        // Source and destination are the same → identity matrix
        val src = listOf(
            Pair(0.0, 0.0),
            Pair(1.0, 0.0),
            Pair(0.0, 1.0),
            Pair(1.0, 1.0)
        )
        val dst = listOf(
            Pair(0.0, 0.0),
            Pair(1.0, 0.0),
            Pair(0.0, 1.0),
            Pair(1.0, 1.0)
        )

        val transform = GeoAffineTransform.fromControlPoints(src, dst)
        assertNotNull(transform)
        transform!!

        val (x, y) = transform.transform(0.5, 0.5)
        assertEquals(0.5, x, 0.001)
        assertEquals(0.5, y, 0.001)
    }

    // ── Translation Only ──

    @Test
    fun `translation-only transform shifts all points`() {
        val src = listOf(
            Pair(0.0, 0.0),
            Pair(100.0, 0.0),
            Pair(0.0, 100.0)
        )
        // Shift by (+50, +30)
        val dst = listOf(
            Pair(50.0, 30.0),
            Pair(150.0, 30.0),
            Pair(50.0, 130.0)
        )

        val transform = GeoAffineTransform.fromControlPoints(src, dst)
        assertNotNull(transform)
        transform!!

        val (x, y) = transform.transform(25.0, 25.0)
        assertEquals(75.0, x, 0.001)
        assertEquals(55.0, y, 0.001)
    }

    // ── Scale ──

    @Test
    fun `scale transform multiplies coordinates`() {
        val src = listOf(
            Pair(0.0, 0.0),
            Pair(1.0, 0.0),
            Pair(0.0, 1.0)
        )
        // Scale by 2x
        val dst = listOf(
            Pair(0.0, 0.0),
            Pair(2.0, 0.0),
            Pair(0.0, 2.0)
        )

        val transform = GeoAffineTransform.fromControlPoints(src, dst)
        assertNotNull(transform)
        transform!!

        val (x, y) = transform.transform(5.0, 3.0)
        assertEquals(10.0, x, 0.001)
        assertEquals(6.0, y, 0.001)
    }

    // ── Inverse ──

    @Test
    fun `inverse is accurate round-trip`() {
        val src = listOf(
            Pair(500000.0, 9970000.0),
            Pair(501000.0, 9970000.0),
            Pair(500000.0, 9971000.0),
            Pair(501000.0, 9971000.0)
        )
        val dst = listOf(
            Pair(0.0, 800.0),
            Pair(800.0, 800.0),
            Pair(0.0, 0.0),
            Pair(800.0, 0.0)
        )

        val transform = GeoAffineTransform.fromControlPoints(src, dst)
        assertNotNull(transform)
        transform!!

        // Forward
        val (px, py) = transform.transform(500500.0, 9970500.0)

        // Inverse back to projected
        val inv = transform.inverse(px, py)
        assertNotNull(inv)
        val (wpx, wpy) = inv!!
        assertEquals(500500.0, wpx, 0.01)
        assertEquals(9970500.0, wpy, 0.01)
    }

    // ── Insufficient Points ──

    @Test
    fun `returns null with fewer than 3 points`() {
        val src = listOf(Pair(0.0, 0.0), Pair(1.0, 0.0))
        val dst = listOf(Pair(0.0, 0.0), Pair(1.0, 0.0))

        val transform = GeoAffineTransform.fromControlPoints(src, dst)
        assertNull(transform)
    }

    // ── Overdetermined (more than 3 points) ──

    @Test
    fun `overdetermined system with 4 points gives consistent result`() {
        val src = listOf(
            Pair(0.0, 0.0),
            Pair(10.0, 0.0),
            Pair(0.0, 10.0),
            Pair(10.0, 10.0)
        )
        // Translation + scale
        val dst = listOf(
            Pair(100.0, 200.0),
            Pair(200.0, 200.0),
            Pair(100.0, 300.0),
            Pair(200.0, 300.0)
        )

        val transform = GeoAffineTransform.fromControlPoints(src, dst)
        assertNotNull(transform)
        transform!!

        // Test center point
        val (x, y) = transform.transform(5.0, 5.0)
        assertEquals(150.0, x, 0.01)
        assertEquals(250.0, y, 0.01)
    }

    // ── Realistic Mining GeoPDF scenario ──

    @Test
    fun `realistic UTM to pixel transform for mining region`() {
        // Simulate mes.pdf: UTM Zone 50N coordinates → PDF pixel coordinates
        // GPTS corners (converted to UTM easting/northing) → LPTS pixel corners
        val src = listOf(
            Pair(501000.0, 29530.0),   // SW corner (UTM)
            Pair(502400.0, 29530.0),   // SE corner
            Pair(502400.0, 30960.0),   // NE corner
            Pair(501000.0, 30960.0)    // NW corner
        )
        val dst = listOf(
            Pair(0.0, 720.0),          // SW → bottom-left pixel
            Pair(1080.0, 720.0),       // SE → bottom-right pixel
            Pair(1080.0, 0.0),         // NE → top-right pixel
            Pair(0.0, 0.0)             // NW → top-left pixel
        )

        val transform = GeoAffineTransform.fromControlPoints(src, dst)
        assertNotNull(transform)
        transform!!

        // Test a point in the middle
        val (px, py) = transform.transform(501700.0, 30245.0)
        assertTrue("Pixel X should be within page bounds", px in 0.0..1080.0)
        assertTrue("Pixel Y should be within page bounds", py in 0.0..720.0)
    }
}
