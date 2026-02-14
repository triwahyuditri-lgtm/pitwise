package com.example.pitwise.domain.snap

import org.junit.Assert.*
import org.junit.Test

class SnapEngineTest {

    @Test
    fun testSnapExactMatch() {
        val vertices = listOf(DxfVertex(10.0, 10.0))
        val index = GridSpatialIndex(cellSize = 10.0)
        index.build(vertices)
        
        val engine = SnapEngine(index, snapThresholdMeters = 2.0)
        
        val result = engine.findVertex(10.0, 10.0)
        assertNotNull(result)
        assertEquals(10.0, result!!.vertex.x, 0.001)
        assertEquals(0.0, result.distance, 0.001)
    }

    @Test
    fun testSnapIdeallyWithinThreshold() {
        val vertices = listOf(DxfVertex(10.0, 10.0))
        val index = GridSpatialIndex()
        index.build(vertices)
        val engine = SnapEngine(index, snapThresholdMeters = 2.0)

        // 1.5m away -> Should snap
        val result = engine.findVertex(11.5, 10.0)
        assertNotNull("Should snap within threshold", result)
        assertEquals(1.5, result!!.distance, 0.001)
    }

    @Test
    fun testSnapOutsideThreshold() {
        val vertices = listOf(DxfVertex(10.0, 10.0))
        val index = GridSpatialIndex()
        index.build(vertices)
        val engine = SnapEngine(index, snapThresholdMeters = 2.0)

        // 2.5m away -> Should NOT snap
        val result = engine.findVertex(12.5, 10.0)
        assertNull("Should not snap outside threshold", result)
    }

    @Test
    fun testNearestNeighbor() {
        // Two points: A at (10,10), B at (12,10)
        val vertices = listOf(
            DxfVertex(10.0, 10.0),
            DxfVertex(12.0, 10.0)
        )
        val index = GridSpatialIndex()
        index.build(vertices)
        val engine = SnapEngine(index, snapThresholdMeters = 5.0)

        // Query at (10.5, 10) -> Closer to A (dist 0.5) vs B (dist 1.5)
        val result = engine.findVertex(10.5, 10.0)
        assertNotNull(result)
        assertEquals(10.0, result!!.vertex.x, 0.001)
    }
    
    @Test
    fun testLargeDatasetPerformance() {
        // Generate 100k points in a 1000x1000 grid
        val vertices = mutableListOf<DxfVertex>()
        for (i in 0 until 1000) {
            for (j in 0 until 100) {
                vertices.add(DxfVertex(i.toDouble(), j.toDouble()))
            }
        }
        
        val index = GridSpatialIndex()
        val engine = SnapEngine(index) // Default 2m threshold
        
        val startTime = System.currentTimeMillis()
        engine.updateVertices(vertices)
        val buildTime = System.currentTimeMillis() - startTime
        println("Build time for 100k points: ${buildTime}ms")
        
        // Query
        val qStart = System.currentTimeMillis()
        val res = engine.findVertex(500.1, 50.1)
        val qTime = System.currentTimeMillis() - qStart
        println("Query time: ${qTime}ms")
        
        assertNotNull(res)
        assertEquals(500.0, res!!.vertex.x, 0.001)
        assertEquals(50.0, res.vertex.y, 0.001)
        
        assertTrue("Build should be fast (<500ms)", buildTime < 500)
        assertTrue("Query should be instant (<1ms)", qTime < 5)
    }
}
