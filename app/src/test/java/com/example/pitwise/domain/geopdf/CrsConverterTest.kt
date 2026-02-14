package com.example.pitwise.domain.geopdf

import org.junit.Assert.*
import org.junit.Test

class CrsConverterTest {

    // ── UTM Zone 50N (Kalimantan mining region) ──

    @Test
    fun `WGS84 to UTM Zone 50N for Kalimantan coordinates`() {
        val projection = ProjectionInfo(
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
        )

        val converter = CrsConverter.create(projection)
        assertNotNull("Converter should be created for UTM", converter)
        converter!!

        // Test with mes.pdf GPTS corner: lat=0.2671, lng=115.8834
        val result = converter.convert(0.2671, 115.8834)

        // UTM easting should be reasonable (within UTM zone bounds)
        assertTrue(
            "Easting should be reasonable UTM value (got ${result.x})",
            result.x in 100000.0..999999.0
        )
        // Northing for 0.2671°N should be positive (north of equator)
        assertTrue(
            "Northing should be positive for northern hemisphere (got ${result.y})",
            result.y > 0.0
        )
    }

    @Test
    fun `UTM converter produces consistent forward results`() {
        val projection = ProjectionInfo(
            name = "WGS_1984_UTM_Zone_50N",
            type = ProjectionType.UTM,
            datum = "D_WGS_1984",
            utmZone = 50,
            isNorthernHemisphere = true
        )

        val converter = CrsConverter.create(projection)!!

        // Two slightly different GPS positions should produce different UTM coords
        val p1 = converter.convert(0.2671, 115.8834)
        val p2 = converter.convert(0.27995, 115.89643)

        assertTrue("Different GPS → different easting", p1.x != p2.x)
        assertTrue("Different GPS → different northing", p1.y != p2.y)
    }

    @Test
    fun `UTM converter for southern hemisphere`() {
        val projection = ProjectionInfo(
            name = "WGS_1984_UTM_Zone_50S",
            type = ProjectionType.UTM,
            datum = "D_WGS_1984",
            utmZone = 50,
            isNorthernHemisphere = false
        )

        val converter = CrsConverter.create(projection)
        assertNotNull(converter)
        converter!!

        // CoordinateUtils handles this via latLngToUtm
        val result = converter.convert(-0.5, 117.0)
        assertTrue("Easting should be positive (got ${result.x})", result.x > 0)
        // Northing for southern hemisphere point should be non-zero
        assertTrue("Northing should be non-zero (got ${result.y})", result.y != 0.0)
    }

    // ── Geographic (no projection) ──

    @Test
    fun `geographic CRS returns lng as x and lat as y`() {
        val projection = ProjectionInfo(
            name = "WGS84",
            type = ProjectionType.GEOGRAPHIC,
            datum = "D_WGS_1984"
        )

        val converter = CrsConverter.create(projection)
        assertNotNull(converter)
        converter!!

        val result = converter.convert(0.2671, 115.8834)
        assertEquals(115.8834, result.x, 0.00001) // lng → x
        assertEquals(0.2671, result.y, 0.00001)   // lat → y
    }

    // ── Converter creation ──

    @Test
    fun `converter is created for UTM type`() {
        val proj = ProjectionInfo(
            name = "UTM_Test",
            type = ProjectionType.UTM,
            datum = "D_WGS_1984",
            utmZone = 50,
            isNorthernHemisphere = true
        )
        assertNotNull(CrsConverter.create(proj))
    }

    @Test
    fun `converter is created for GEOGRAPHIC type`() {
        val proj = ProjectionInfo(
            name = "GEO_Test",
            type = ProjectionType.GEOGRAPHIC,
            datum = "D_WGS_1984"
        )
        assertNotNull(CrsConverter.create(proj))
    }

    @Test
    fun `converter returns null for UNKNOWN type`() {
        val proj = ProjectionInfo(
            name = "Unknown",
            type = ProjectionType.UNKNOWN,
            datum = "D_WGS_1984"
        )
        assertNull(CrsConverter.create(proj))
    }
}
