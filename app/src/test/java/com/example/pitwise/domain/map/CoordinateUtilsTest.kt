package com.example.pitwise.domain.map

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

class CoordinateUtilsTest {

    // Helper for approximate double equality
    private fun assertClose(expected: Double, actual: Double, delta: Double = 0.01) {
        assertEquals(expected, actual, delta)
    }

    @Test
    fun `standard UTM conversion - Equator`() {
        // Equator, Central Meridian of Zone 50 (117E)
        // Should be exactly 500,000 E, 0 N
        val result = CoordinateUtils.latLngToUtm(0.0, 117.0)
        
        assertEquals(50, result.zone)
        assertClose(500000.0, result.easting)
        assertClose(0.0, result.northing)
        assertEquals('N', result.letter) // 0 is considered North usually, or M? Letter logic handles bands.
    }

    @Test
    fun `standard UTM conversion - Southern Hemisphere`() {
        // 10 degrees South, 117E (Zone 50)
        // Northing should be 10,000,000 - (distance from equator)
        // 10 deg approx 1105km.
        // Precise calc using online converters: 500000E, 8894169.8N
        
        val result = CoordinateUtils.latLngToUtm(-10.0, 117.0)
        
        assertEquals(50, result.zone)
        assertClose(500000.0, result.easting)
        // Verify offset is applied (should be ~8.8M)
        assert(result.northing > 8000000.0 && result.northing < 9500000.0) {
            "Northing ${result.northing} out of expected southern range"
        }
    }

    @Test
    fun `forced zone - crossing boundary`() {
        // Point at 119.9 degrees (Zone 50 edge)
        // Point at 120.1 degrees (Zone 51 start)
        
        // If we FORCE Zone 50 for the point in Zone 51 (120.1), 
        // Easting should continue increasing past 833km, not reset to ~166km
        
        val normalScale = CoordinateUtils.latLngToUtm(0.0, 119.99) // Zone 50
        val normalCross = CoordinateUtils.latLngToUtm(0.0, 120.01) // Zone 51
        
        assertEquals(50, normalScale.zone)
        assertEquals(51, normalCross.zone)
        
        // Force 120.01 into Zone 50
        val forced = CoordinateUtils.latLngToUtm(0.0, 120.01, forceZone = 50)
        
        assertEquals(50, forced.zone)
        // Easting should be > 800,000 (wide), not ~160,000 (reset)
        // 120.1 is 3.1 degrees from CM 117. Approx 344km from CM. 
        // 500k + 344k = 844k.
        assert(forced.easting > 800000.0) 
    }

    @Test
    fun `forced hemisphere - Southern point forced North`() {
        // 1 degree South. Normal: ~9,889,000 N.
        // Forced North: Should be negative northing? Or just raw math?
        // Standard UTM defines North as 0 at equator, South as 10,000,000 at equator.
        // If we force 'N' on a southern point, we expect negative northing (standard math without +10M shift).
        
        val result = CoordinateUtils.latLngToUtm(-1.0, 117.0, forceHemisphere = 'N')
        
        // 1 deg is ~110km. So -110,000.
        assert(result.northing < 0) 
        assertClose(-110574.0, result.northing, 1000.0)
    }

    @Test
    fun `forced hemisphere - Northern point forced South`() {
        // 1 degree North. Normal: ~110,000 N.
        // Forced South: Should add 10,000,000. So ~10,110,000.
        
        val result = CoordinateUtils.latLngToUtm(1.0, 117.0, forceHemisphere = 'S')
        
        assert(result.northing > 10000000.0)
        assertClose(10110574.0, result.northing, 1000.0)
    }
}
