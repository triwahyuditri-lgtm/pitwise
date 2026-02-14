package com.example.pitwise.domain.geopdf

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GeoPdfTransformEngineTest {

    private lateinit var engine: GeoPdfTransformEngine

    @Before
    fun setUp() {
        engine = GeoPdfTransformEngine()
    }

    /**
     * Build a synthetic GeoPdfMetadata matching the structure of mes.pdf:
     * - UTM Zone 50N
     * - 4 control points (corners)
     * - Normalized LPTS (0–1)
     * - Page size 612×792 (letter)
     */
    private fun buildSyntheticMetadata(): GeoPdfMetadata {
        return GeoPdfMetadata(
            projection = ProjectionInfo(
                name = "WGS_1984_UTM_Zone_50N",
                type = ProjectionType.UTM,
                datum = "D_WGS_1984",
                utmZone = 50,
                isNorthernHemisphere = true,
                centralMeridian = 117.0,
                scaleFactor = 0.9996,
                falseEasting = 500000.0,
                falseNorthing = 0.0,
                latitudeOfOrigin = 0.0
            ),
            gpts = listOf(
                // lat, lng pairs from mes.pdf GPTS
                WorldPoint(0.2671, 115.8834),     // SW
                WorldPoint(0.27995, 115.8834),    // NW
                WorldPoint(0.27995, 115.89643),   // NE
                WorldPoint(0.2671, 115.89643)     // SE
            ),
            lpts = listOf(
                // Normalized pixel coords from mes.pdf LPTS
                PixelPoint(0.0, 1.0),   // SW → bottom-left
                PixelPoint(0.0, 0.0),   // NW → top-left
                PixelPoint(1.0, 0.0),   // NE → top-right
                PixelPoint(1.0, 1.0)    // SE → bottom-right
            ),
            lptsNormalized = true,
            bounds = BoundingBox(
                minLat = 0.2671,
                minLng = 115.8834,
                maxLat = 0.27995,
                maxLng = 115.89643
            ),
            pageWidth = 612.0,
            pageHeight = 792.0
        )
    }

    // ── Initialization ──

    @Test
    fun `engine is not initialized before calling initialize`() {
        assertFalse(engine.isInitialized)
    }

    @Test
    fun `engine initializes successfully with valid metadata`() {
        val metadata = buildSyntheticMetadata()
        val result = engine.initialize(metadata)
        assertTrue("Should return Valid", result is GeoPdfValidationResult.Valid)
        assertTrue(engine.isInitialized)
    }

    @Test
    fun `engine fails with insufficient control points`() {
        val metadata = buildSyntheticMetadata().copy(
            gpts = listOf(WorldPoint(0.0, 0.0), WorldPoint(1.0, 1.0)),
            lpts = listOf(PixelPoint(0.0, 0.0), PixelPoint(1.0, 1.0))
        )
        val result = engine.initialize(metadata)
        assertTrue("Should return InsufficientControlPoints", result is GeoPdfValidationResult.InsufficientControlPoints)
        assertFalse(engine.isInitialized)
    }

    // ── GPS to Pixel ──

    @Test
    fun `gpsToPixel returns null when not initialized`() {
        assertNull(engine.gpsToPixel(0.27, 115.89))
    }

    @Test
    fun `gpsToPixel returns values within page bounds for point inside extent`() {
        val metadata = buildSyntheticMetadata()
        engine.initialize(metadata)

        // A GPS point roughly in the center of the map extent
        val centerLat = (0.2671 + 0.27995) / 2.0
        val centerLng = (115.8834 + 115.89643) / 2.0

        val pixel = engine.gpsToPixel(centerLat, centerLng)
        assertNotNull("Pixel should not be null for valid GPS", pixel)
        pixel!!

        // Pixel should be roughly in the center of the page
        assertTrue(
            "Pixel X should be roughly center of 612-wide page (got ${pixel.x})",
            pixel.x in 200.0..420.0
        )
        assertTrue(
            "Pixel Y should be roughly center of 792-tall page (got ${pixel.y})",
            pixel.y in 250.0..550.0
        )
    }

    @Test
    fun `gpsToPixel at control point corner maps to correct quadrant`() {
        val metadata = buildSyntheticMetadata()
        engine.initialize(metadata)

        // NW corner: lat=0.27995, lng=115.8834
        val nwPixel = engine.gpsToPixel(0.27995, 115.8834)
        assertNotNull(nwPixel)
        nwPixel!!

        // SE corner: lat=0.2671, lng=115.89643
        val sePixel = engine.gpsToPixel(0.2671, 115.89643)
        assertNotNull(sePixel)
        sePixel!!

        // NW should be LEFT of SE
        assertTrue(
            "NW X (${nwPixel.x}) should be less than SE X (${sePixel.x})",
            nwPixel.x < sePixel.x
        )
        // NW should be ABOVE (smaller Y) than SE (in screen coords, y increases downward)
        assertTrue(
            "NW Y (${nwPixel.y}) should be less than SE Y (${sePixel.y})",
            nwPixel.y < sePixel.y
        )
    }

    // ── GPS to Screen ──

    @Test
    fun `gpsToScreen applies zoom and pan`() {
        val metadata = buildSyntheticMetadata()
        engine.initialize(metadata)

        val centerLat = (0.2671 + 0.27995) / 2.0
        val centerLng = (115.8834 + 115.89643) / 2.0

        val scale = 2f
        val offsetX = 100f
        val offsetY = 50f

        val screen = engine.gpsToScreen(centerLat, centerLng, scale, offsetX, offsetY)
        assertNotNull(screen)
        screen!!

        // Screen coordinates should incorporate scale and offset
        val pixel = engine.gpsToPixel(centerLat, centerLng)!!
        val expectedX = (pixel.x.toFloat() * scale) + offsetX
        val expectedY = (pixel.y.toFloat() * scale) + offsetY
        assertEquals(expectedX, screen.x, 0.1f)
        assertEquals(expectedY, screen.y, 0.1f)
    }

    @Test
    fun `gpsToScreen returns null when not initialized`() {
        assertNull(engine.gpsToScreen(0.0, 0.0, 1f, 0f, 0f))
    }

    // ── Debug Info ──

    @Test
    fun `debug info contains all pipeline stages`() {
        val metadata = buildSyntheticMetadata()
        engine.initialize(metadata)

        val debug = engine.getDebugInfo(0.2735, 115.89)
        assertNotNull(debug)
        debug!!

        assertEquals(0.2735, debug.rawLat, 0.0001)
        assertEquals(115.89, debug.rawLng, 0.0001)
        assertTrue("Projected X should be non-zero", debug.projectedX != 0.0)
        assertTrue("Projected Y should be non-zero", debug.projectedY != 0.0)
        assertTrue("Pixel X should be non-zero", debug.pixelX != 0.0)
        assertTrue("Pixel Y should be non-zero", debug.pixelY != 0.0)
        assertEquals("UTM", debug.crsType)
        assertEquals(50, debug.utmZone)
        assertEquals("D_WGS_1984", debug.datum)
        assertTrue("Affine matrix string should not be empty", debug.affineMatrix.isNotEmpty())
    }

    @Test
    fun `debug info returns null when not initialized`() {
        assertNull(engine.getDebugInfo(0.0, 0.0))
    }

    // ── Reset ──

    @Test
    fun `reset clears initialized state`() {
        val metadata = buildSyntheticMetadata()
        engine.initialize(metadata)
        assertTrue(engine.isInitialized)

        engine.reset()
        assertFalse(engine.isInitialized)
        assertNull(engine.gpsToPixel(0.27, 115.89))
    }
}
