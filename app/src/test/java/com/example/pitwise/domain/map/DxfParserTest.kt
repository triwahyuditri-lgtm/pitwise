package com.example.pitwise.domain.map

import com.example.pitwise.domain.dxf.DxfEntity
import com.example.pitwise.domain.dxf.DxfParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DxfParserTest {

    private lateinit var parser: DxfParser

    @Before
    fun setUp() {
        parser = DxfParser()
    }

    @Test
    fun `parse LINE with Z coordinates`() {
        val dxf = """
            0
            SECTION
            2
            ENTITIES
            0
            LINE
            10
            100.0
            20
            200.0
            30
            50.5
            11
            300.0
            21
            400.0
            31
            75.3
            0
            ENDSEC
        """.trimIndent()

        val result = parser.parse(dxf)
        assertEquals(1, result.lines.size)

        val line = result.lines[0]
        assertEquals(100.0, line.start.x, 0.001)
        assertEquals(200.0, line.start.y, 0.001)
        assertEquals(50.5, line.start.z, 0.001)
        assertEquals(300.0, line.end.x, 0.001)
        assertEquals(400.0, line.end.y, 0.001)
        assertEquals(75.3, line.end.z, 0.001)
    }

    @Test
    fun `parse LINE without Z defaults to 0`() {
        val dxf = """
            0
            SECTION
            2
            ENTITIES
            0
            LINE
            10
            100.0
            20
            200.0
            11
            300.0
            21
            400.0
            0
            ENDSEC
        """.trimIndent()

        val result = parser.parse(dxf)
        val line = result.lines[0]
        assertEquals(0.0, line.start.z, 0.001)
        assertEquals(0.0, line.end.z, 0.001)
    }

    @Test
    fun `parse POINT with Z coordinate`() {
        val dxf = """
            0
            SECTION
            2
            ENTITIES
            0
            POINT
            10
            50.0
            20
            60.0
            30
            42.5
            0
            ENDSEC
        """.trimIndent()

        val result = parser.parse(dxf)
        assertEquals(1, result.points.size)
        val point = result.points[0]
        assertEquals(50.0, point.x, 0.001)
        assertEquals(60.0, point.y, 0.001)
        assertEquals(42.5, point.z, 0.001)
    }

    @Test
    fun `parse LWPOLYLINE with elevation (code 38)`() {
        val dxf = """
            0
            SECTION
            2
            ENTITIES
            0
            LWPOLYLINE
            38
            100.0
            70
            0
            10
            1.0
            20
            2.0
            10
            3.0
            20
            4.0
            0
            ENDSEC
        """.trimIndent()

        val result = parser.parse(dxf)
        assertEquals(1, result.polylines.size)
        val poly = result.polylines[0]
        assertEquals(2, poly.vertices.size)
        // All vertices should inherit the elevation
        assertEquals(100.0, poly.vertices[0].z, 0.001)
        assertEquals(100.0, poly.vertices[1].z, 0.001)
    }

    @Test
    fun `parse POLYLINE with per-VERTEX Z`() {
        val dxf = """
            0
            SECTION
            2
            ENTITIES
            0
            POLYLINE
            70
            0
            0
            VERTEX
            10
            10.0
            20
            20.0
            30
            30.0
            0
            VERTEX
            10
            40.0
            20
            50.0
            30
            60.0
            0
            SEQEND
            0
            ENDSEC
        """.trimIndent()

        val result = parser.parse(dxf)
        assertEquals(1, result.polylines.size)
        val poly = result.polylines[0]
        assertEquals(2, poly.vertices.size)
        assertEquals(30.0, poly.vertices[0].z, 0.001)
        assertEquals(60.0, poly.vertices[1].z, 0.001)
    }

    @Test
    fun `parse POLYLINE VERTEX without Z inherits polyline elevation`() {
        val dxf = """
            0
            SECTION
            2
            ENTITIES
            0
            POLYLINE
            70
            0
            30
            99.0
            0
            VERTEX
            10
            10.0
            20
            20.0
            0
            VERTEX
            10
            40.0
            20
            50.0
            0
            SEQEND
            0
            ENDSEC
        """.trimIndent()

        val result = parser.parse(dxf)
        assertEquals(1, result.polylines.size)
        val poly = result.polylines[0]
        assertEquals(99.0, poly.vertices[0].z, 0.001)
        assertEquals(99.0, poly.vertices[1].z, 0.001)
    }

    @Test
    fun `bounding box includes Z range`() {
        val dxf = """
            0
            SECTION
            2
            ENTITIES
            0
            POINT
            10
            0.0
            20
            0.0
            30
            10.0
            0
            POINT
            10
            100.0
            20
            100.0
            30
            50.0
            0
            ENDSEC
        """.trimIndent()

        val result = parser.parse(dxf)
        assertEquals(10.0, result.bounds.minZ, 0.001)
        assertEquals(50.0, result.bounds.maxZ, 0.001)
    }

    @Test
    fun `parse multiple entity types`() {
        val dxf = """
            0
            SECTION
            2
            ENTITIES
            0
            POINT
            10
            1.0
            20
            2.0
            30
            3.0
            0
            LINE
            10
            10.0
            20
            20.0
            30
            30.0
            11
            40.0
            21
            50.0
            31
            60.0
            0
            LWPOLYLINE
            70
            1
            10
            100.0
            20
            200.0
            10
            300.0
            20
            400.0
            0
            ENDSEC
        """.trimIndent()

        val result = parser.parse(dxf)
        assertEquals(1, result.points.size)
        assertEquals(1, result.lines.size)
        assertEquals(1, result.polylines.size)

        val poly = result.polylines[0]
        assertTrue(poly.isClosed)
    }

    @Test
    fun `empty entities section returns default bounding box`() {
        val dxf = """
            0
            SECTION
            2
            ENTITIES
            0
            ENDSEC
        """.trimIndent()

        val result = parser.parse(dxf)
        assertTrue(result.lines.isEmpty())
        assertTrue(result.polylines.isEmpty())
        assertTrue(result.points.isEmpty())
        assertEquals(0.0, result.bounds.minX, 0.001)
        assertEquals(0.0, result.bounds.minZ, 0.001)
    }

    @Test
    fun `parse LWPOLYLINE with per-vertex Z code 30 after code 20`() {
        // This tests the deferred vertex assembly fix:
        // DXF files may have code 30 (Z) after code 20 (Y)
        val dxf = """
            0
            SECTION
            2
            ENTITIES
            0
            LWPOLYLINE
            70
            0
            10
            100.0
            20
            200.0
            30
            50.5
            10
            300.0
            20
            400.0
            30
            75.3
            0
            ENDSEC
        """.trimIndent()

        val result = parser.parse(dxf)
        assertEquals(1, result.polylines.size)
        val poly = result.polylines[0]
        assertEquals(2, poly.vertices.size)
        // Each vertex should have its own Z value
        assertEquals(50.5, poly.vertices[0].z, 0.001)
        assertEquals(75.3, poly.vertices[1].z, 0.001)
    }

    @Test
    fun `parse LWPOLYLINE with Z before Y still works`() {
        // Ensure normal ordering (10, 30, 20) also works
        val dxf = """
            0
            SECTION
            2
            ENTITIES
            0
            LWPOLYLINE
            70
            0
            10
            1.0
            30
            10.0
            20
            2.0
            10
            3.0
            30
            20.0
            20
            4.0
            0
            ENDSEC
        """.trimIndent()

        val result = parser.parse(dxf)
        assertEquals(1, result.polylines.size)
        val poly = result.polylines[0]
        assertEquals(2, poly.vertices.size)
        assertEquals(10.0, poly.vertices[0].z, 0.001)
        assertEquals(20.0, poly.vertices[1].z, 0.001)
    }
}
