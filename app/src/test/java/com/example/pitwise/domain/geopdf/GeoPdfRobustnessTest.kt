package com.example.pitwise.domain.geopdf

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GeoPdfRobustnessTest {

    private lateinit var engine: GeoPdfTransformEngine

    @Before
    fun setUp() {
        engine = GeoPdfTransformEngine()
    }

    // ── Helpers ──

    private fun createMetadata(
        gpts: List<WorldPoint>,
        lpts: List<PixelPoint>,
        normalized: Boolean
    ): GeoPdfMetadata {
        return GeoPdfMetadata(
            projection = ProjectionInfo("WGS84", ProjectionType.GEOGRAPHIC, "WGS84"),
            gpts = gpts,
            lpts = lpts,
            lptsNormalized = normalized,
            bounds = BoundingBox(0.0, 0.0, 1.0, 1.0),
            pageWidth = 1000.0,
            pageHeight = 1000.0
        )
    }

    // ── Tests ──

    @Test
    fun `GeoAffineTransform calculates RMSE correctly`() {
        // Identity transform: x'=x, y'=y
        val transform = GeoAffineTransform(1.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        
        val src = listOf(0.0 to 0.0, 10.0 to 10.0)
        val dstExact = listOf(0.0 to 0.0, 10.0 to 10.0)
        val dstError = listOf(0.0 to 0.0, 10.0 to 12.0) // Error of 2.0 on second point

        assertEquals(0.0, transform.calculateRMSE(src, dstExact), 0.0001)
        
        // RMSE = sqrt( (0^2 + 2^2) / 2 ) = sqrt(4/2) = sqrt(2) = 1.414
        assertEquals(1.4142, transform.calculateRMSE(src, dstError), 0.001)
    }

    @Test
    fun `engine detects collinear control points (Singular Matrix) and fails`() {
        // 3 Collinear points -> Singular matrix
        val gpts = listOf(
            WorldPoint(0.0, 0.0),
            WorldPoint(1.0, 1.0),
            WorldPoint(2.0, 2.0)
        )
        val lpts = listOf(
            PixelPoint(0.0, 0.0),
            PixelPoint(100.0, 100.0),
            PixelPoint(200.0, 200.0)
        )
        val meta = createMetadata(gpts, lpts, false)

        val result = engine.initialize(meta)
        assertTrue("Should fail with ParseError for singular matrix", result is GeoPdfValidationResult.ParseError)
        assertFalse(engine.isInitialized)
    }

    @Test
    fun `engine correctly infers Top-Down Y orientation (Standard)`() {
        // Scenario: GPTS Lat decreases as Y increases (North is Top)
        // LPTS (Standard): 0=Top, 1=Bottom
        // So Lat 10 -> LPTS 0.0
        // Lat 0  -> LPTS 1.0
        
        val gpts = listOf(
            WorldPoint(10.0, 0.0), // Top Left
            WorldPoint(10.0, 10.0), // Top Right
            WorldPoint(0.0, 0.0)    // Bottom Left
        )
        // Standard normalized LPTS
        val lpts = listOf(
            PixelPoint(0.0, 0.0), 
            PixelPoint(1.0, 0.0), 
            PixelPoint(0.0, 1.0)
        )
        // Also update assertions

        val meta = createMetadata(gpts, lpts, true)

        val result = engine.initialize(meta)
        if (result !is GeoPdfValidationResult.Valid) {
            println("Initialize failed: $result")
        }
        assertTrue("Expected Valid, got $result", result is GeoPdfValidationResult.Valid)
        
        // Verify Mapping:
        // Lat 10 (Top) -> Pixel Y 0
        // Lat 0 (Bottom) -> Pixel Y 1000
        val pTop = engine.gpsToPixel(10.0, 0.0)!!
        val pBot = engine.gpsToPixel(0.0, 0.0)!!
        
        println("Top: $pTop")
        println("Bot: $pBot")

        assertEquals(0.0, pTop.y, 1.0)
        assertEquals(1000.0, pBot.y, 1.0)
    }

    @Test
    fun `engine correctly infers Bottom-Up Y orientation (Flipped)`() {
        // Scenario: GPTS Lat decreases as Y increases
        // BUT LPTS provided are "Cartesian" where 0=Bottom, 1=Top
        // Use SAME GPTS as above:
        // Lat 10 (Top)  -> Expected LPTS Y=1.0 (Top in Cartesian)
        // Lat 0 (Bottom)-> Expected LPTS Y=0.0 (Bottom in Cartesian)
        
        val gpts = listOf(
            WorldPoint(10.0, 0.0), // Top Left
            WorldPoint(10.0, 10.0), // Top Right
            WorldPoint(0.0, 0.0)    // Bottom Left
        )
        
        val lpts = listOf(
            PixelPoint(0.0, 1.0), // Top Left (Y=1)
            PixelPoint(1.0, 1.0), // Top Right (Y=1)
            PixelPoint(0.0, 0.0)  // Bottom Left (Y=0)
        )
        
        val meta = createMetadata(gpts, lpts, true)

        val result = engine.initialize(meta)
        if (result !is GeoPdfValidationResult.Valid) {
            println("Initialize failed: $result")
        }
        assertTrue("Expected Valid, got $result", result is GeoPdfValidationResult.Valid)
        
        // IF the engine correctly detected "Flipped", it should map:
        // Lat 10 (Top) -> Screen Y 0 (Top of 0-1000 page)
        // Lat 0 (Bottom) -> Screen Y 1000 (Bottom of 0-1000 page)
        
        val pTop = engine.gpsToPixel(10.0, 0.0)!!
        val pBot = engine.gpsToPixel(0.0, 0.0)!!
        
        println("Flipped Top: $pTop")
        println("Flipped Bot: $pBot")
        
        // The engine maps GPS -> Pixel (Screen Space). 
        // Screen space is ALWAYS 0=Top.
        // So GPS Top should map to Pixel 0.
        
        assertEquals(0.0, pTop.y, 1.0)
        assertEquals(1000.0, pBot.y, 1.0)
        
        // Note: If engine FAILED to detect, it would treat LPTS Y=1 (Top) as Sceen Y=1000 (Bottom).
        // Then pTop would be 1000.
    }
}
