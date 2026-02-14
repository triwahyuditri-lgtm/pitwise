package com.example.pitwise.domain.map

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MapTransformEngineTest {

    private lateinit var engine: MapTransformEngine

    @Before
    fun setUp() {
        engine = MapTransformEngine()
    }

    // ── Initial State ──

    @Test
    fun `initial state is identity`() {
        assertEquals(1f, engine.scale)
        assertEquals(0f, engine.offsetX)
        assertEquals(0f, engine.offsetY)
    }

    // ── Pan ──

    @Test
    fun `applyPan updates offset`() {
        engine.applyPan(100f, 50f)
        assertEquals(100f, engine.offsetX)
        assertEquals(50f, engine.offsetY)
    }

    @Test
    fun `applyPan is additive`() {
        engine.applyPan(10f, 20f)
        engine.applyPan(30f, 40f)
        assertEquals(40f, engine.offsetX)
        assertEquals(60f, engine.offsetY)
    }

    // ── Zoom ──

    @Test
    fun `applyZoom increases scale`() {
        engine.applyZoom(2f, 0f, 0f)
        assertEquals(2f, engine.scale, 0.001f)
    }

    @Test
    fun `applyZoom clamps to max scale`() {
        engine.applyZoom(200f, 0f, 0f) // Way over max
        assertEquals(MapTransformEngine.MAX_SCALE, engine.scale, 0.01f)
    }

    @Test
    fun `applyZoom clamps to min scale`() {
        engine.applyZoom(0.00001f, 0f, 0f) // Way under min
        assertEquals(MapTransformEngine.MIN_SCALE, engine.scale, 0.0001f)
    }

    // ── World <-> Screen ──

    @Test
    fun `worldToScreen at identity is direct mapping with Y flip`() {
        engine.flipY = true
        val offset = engine.worldToScreen(100.0, 200.0)
        assertEquals(100f, offset.x, 0.1f)
        assertEquals(-200f, offset.y, 0.1f) // Y flipped
    }

    @Test
    fun `worldToScreen without flip is direct`() {
        engine.flipY = false
        val offset = engine.worldToScreen(100.0, 200.0)
        assertEquals(100f, offset.x, 0.1f)
        assertEquals(200f, offset.y, 0.1f)
    }

    @Test
    fun `screenToWorld is inverse of worldToScreen`() {
        engine.flipY = true
        engine.setState(2f, 50f, 100f)

        val screen = engine.worldToScreen(10.0, 20.0)
        val (wx, wy) = engine.screenToWorld(screen.x, screen.y)

        assertEquals(10.0, wx, 0.01)
        assertEquals(20.0, wy, 0.01)
    }

    @Test
    fun `screenToWorld inverse works without flip`() {
        engine.flipY = false
        engine.setState(3f, -10f, 30f)

        val screen = engine.worldToScreen(50.0, 75.0)
        val (wx, wy) = engine.screenToWorld(screen.x, screen.y)

        assertEquals(50.0, wx, 0.01)
        assertEquals(75.0, wy, 0.01)
    }

    // ── setState ──

    @Test
    fun `setState clamps scale`() {
        engine.setState(999f, 0f, 0f)
        assertEquals(MapTransformEngine.MAX_SCALE, engine.scale, 0.01f)

        engine.setState(-1f, 0f, 0f)
        assertEquals(MapTransformEngine.MIN_SCALE, engine.scale, 0.0001f)
    }

    // ── Zoom All ──

    @Test
    fun `zoomAll fits bounding box to canvas`() {
        engine.flipY = true
        engine.zoomAll(
            minX = 0.0, minY = 0.0,
            maxX = 1000.0, maxY = 500.0,
            canvasW = 1080f, canvasH = 720f
        )

        // After zoom all the scale should fit the content
        assertTrue(engine.scale > 0f)
        assertTrue(engine.scale < MapTransformEngine.MAX_SCALE)
    }

    @Test
    fun `zoomAll with zero-size bounding box does nothing`() {
        engine.setState(5f, 100f, 200f)
        engine.zoomAll(0.0, 0.0, 0.0, 0.0, 1080f, 720f)
        // State should be unchanged
        assertEquals(5f, engine.scale, 0.01f)
    }

    @Test
    fun `zoomAllPdf fits bitmap to canvas`() {
        engine.flipY = false
        engine.zoomAllPdf(2000, 1500, 1080f, 720f)

        assertTrue(engine.scale > 0f)
        assertTrue(engine.scale < MapTransformEngine.MAX_SCALE)
    }
}
