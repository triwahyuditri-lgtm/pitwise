package com.example.pitwise.domain.map

import com.example.pitwise.domain.dxf.DxfEntity
import com.example.pitwise.domain.dxf.DxfParser
import org.junit.Assert.assertEquals
import org.junit.Test

class ReproductionDxfTest {

    @Test
    fun `parse case insensitive keywords`() {
        // Mixed case keywords to simulate non-standard DXF generators
        val dxf = """
            0
            Section
            2
            Entities
            0
            Line
            10
            100.0
            20
            200.0
            11
            300.0
            21
            400.0
            0
            EndSec
        """.trimIndent()

        val parser = DxfParser()
        val result = parser.parse(dxf)
        
        assertEquals(1, result.lines.size)
        val line = result.lines[0]
        assertEquals(100.0, line.start.x, 0.001)
    }

    @Test
    fun `parse weird casing`() {
        val dxf = """
            0
            SECTION
            2
            ENTITIES
            0
            LwPolyLine
            10
            10.0
            20
            10.0
            10
            20.0
            20
            20.0
            0
            ENDSEC
        """.trimIndent()

        val parser = DxfParser()
        val result = parser.parse(dxf)
        
        assertEquals(1, result.polylines.size)
    }
}
