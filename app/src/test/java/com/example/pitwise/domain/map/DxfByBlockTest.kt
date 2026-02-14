package com.example.pitwise.domain.map

import com.example.pitwise.domain.dxf.DxfColorResolver
import com.example.pitwise.domain.dxf.DxfParser
import org.junit.Assert.assertEquals
import org.junit.Test

class DxfByBlockTest {

    @Test
    fun `explode propagates INSERT color to ByBlock entities`() {
        // 1. Define Block "TEST" with:
        //    - Line 1: Color 0 (ByBlock)
        //    - Line 2: Color 1 (Red, explicit)
        // 2. Insert "TEST" with Color 3 (Green)
        // 3. Expect:
        //    - Exploded Line 1: Color 3 (Green)
        //    - Exploded Line 2: Color 1 (Red)
        
        val dxf = """
            0
            SECTION
            2
            BLOCKS
            0
            BLOCK
            2
            TEST
            0
            LINE
            10
            0.0
            20
            0.0
            11
            10.0
            21
            0.0
            62
            0
            0
            LINE
            10
            0.0
            20
            10.0
            11
            10.0
            21
            10.0
            62
            1
            0
            ENDBLK
            0
            ENDSEC
            0
            SECTION
            2
            ENTITIES
            0
            INSERT
            2
            TEST
            10
            100.0
            20
            100.0
            62
            3
            0
            ENDSEC
        """.trimIndent()

        val parser = DxfParser()
        val result = parser.parse(dxf)
        
        // Should have 2 lines (exploded from 1 Insert)
        assertEquals(2, result.lines.size)
        
        // Find the lines. Order depends on internal processing, but we can check colors.
        // Color 3 (Green) is -16711936 (0xFF00FF00) in ARGB?
        // Let's use DxfColorResolver to get expected int values.
        val greenColor = DxfColorResolver.getColorFromIndex(3)
        val redColor = DxfColorResolver.getColorFromIndex(1)
        
        val lineByBlock = result.lines.find { it.color == greenColor }
        val lineExplicit = result.lines.find { it.color == redColor }
        
        if (lineByBlock == null) {
            // Debug failure
            println("Available colors: ${result.lines.map { it.color }}")
        }

        assertEquals("Should find one line with Green color (inherited)", greenColor, lineByBlock?.color)
        assertEquals("Should find one line with Red color (explicit)", redColor, lineExplicit?.color)
    }

    @Test
    fun `nested block explosion handles ByBlock recursively`() {
        // Block INNER: Line ByBlock
        // Block OUTER: Insert INNER (Color ByBlock)
        // Insert OUTER (Color Blue)
        // Expect: Line becomes Blue.
        
        // 1. INNER definition
        // 2. OUTER definition containing INSERT INNER (Color 0)
        // 3. INSERT OUTER (Color 5 - Blue)
        
        val dxf = """
            0
            SECTION
            2
            BLOCKS
            0
            BLOCK
            2
            INNER
            0
            LINE
            10
            0.0
            20
            0.0
            11
            1.0
            21
            1.0
            62
            0
            0
            ENDBLK
            0
            BLOCK
            2
            OUTER
            0
            INSERT
            2
            INNER
            10
            0.0
            20
            0.0
            62
            0
            0
            ENDBLK
            0
            ENDSEC
            0
            SECTION
            2
            ENTITIES
            0
            INSERT
            2
            OUTER
            10
            50.0
            20
            50.0
            62
            5
            0
            ENDSEC
        """.trimIndent()
        
        val parser = DxfParser()
        val result = parser.parse(dxf)
        
        assertEquals(1, result.lines.size)
        val blueColor = DxfColorResolver.getColorFromIndex(5)
        
        assertEquals(blueColor, result.lines[0].color)
    }
}
