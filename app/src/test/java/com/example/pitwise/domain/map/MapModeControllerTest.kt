package com.example.pitwise.domain.map

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MapModeControllerTest {

    private lateinit var controller: MapModeController

    @Before
    fun setUp() {
        controller = MapModeController()
    }

    // ── Initial State ──

    @Test
    fun `initial mode is VIEW`() {
        assertEquals(MapMode.VIEW, controller.currentMode)
    }

    @Test
    fun `initial points are empty`() {
        assertTrue(controller.collectedPoints.isEmpty())
    }

    @Test
    fun `initial idPointMarker is null`() {
        assertNull(controller.idPointMarker)
    }

    // ── Mode Transitions ──

    @Test
    fun `setMode changes mode`() {
        controller.setMode(MapMode.PLOT)
        assertEquals(MapMode.PLOT, controller.currentMode)
    }

    @Test
    fun `setMode clears collected points`() {
        controller.setMode(MapMode.MEASURE)
        controller.addPoint(1.0, 2.0, null)
        controller.addPoint(3.0, 4.0, null)
        assertEquals(2, controller.collectedPoints.size)

        controller.setMode(MapMode.PLOT)
        assertTrue(controller.collectedPoints.isEmpty())
    }

    @Test
    fun `setMode clears idPointMarker`() {
        controller.setMode(MapMode.ID_POINT)
        controller.addPoint(5.0, 6.0, 10.0)
        assertNotNull(controller.idPointMarker)

        controller.setMode(MapMode.VIEW)
        assertNull(controller.idPointMarker)
    }

    // ── Measure Sub-Mode ──

    @Test
    fun `setMeasureSubMode switches and clears points`() {
        controller.setMode(MapMode.MEASURE)
        controller.addPoint(1.0, 2.0, null)
        assertEquals(1, controller.collectedPoints.size)

        controller.setMeasureSubMode(MeasureSubMode.AREA)
        assertEquals(MeasureSubMode.AREA, controller.measureSubMode)
        assertTrue(controller.collectedPoints.isEmpty())
    }

    // ── Point Collection ──

    @Test
    fun `PLOT mode collects points`() {
        controller.setMode(MapMode.PLOT)
        controller.addPoint(0.0, 0.0, null)
        controller.addPoint(10.0, 0.0, null)
        controller.addPoint(10.0, 10.0, null)
        assertEquals(3, controller.collectedPoints.size)
    }

    @Test
    fun `MEASURE mode collects points`() {
        controller.setMode(MapMode.MEASURE)
        controller.addPoint(0.0, 0.0, null)
        controller.addPoint(5.0, 0.0, null)
        assertEquals(2, controller.collectedPoints.size)
    }

    @Test
    fun `ID_POINT mode replaces marker`() {
        controller.setMode(MapMode.ID_POINT)
        controller.addPoint(1.0, 2.0, 100.0)
        assertEquals(MapVertex(1.0, 2.0, 100.0), controller.idPointMarker)

        controller.addPoint(3.0, 4.0, 200.0)
        assertEquals(MapVertex(3.0, 4.0, 200.0), controller.idPointMarker)
        // Only one marker, no collected points
        assertTrue(controller.collectedPoints.isEmpty())
    }

    @Test
    fun `VIEW mode tap sets idPointMarker`() {
        controller.setMode(MapMode.VIEW)
        controller.addPoint(7.0, 8.0, null)
        assertNotNull(controller.idPointMarker)
        assertEquals(7.0, controller.idPointMarker!!.x, 0.001)
    }

    // ── Undo ──

    @Test
    fun `undoLastPoint removes last`() {
        controller.setMode(MapMode.PLOT)
        controller.addPoint(0.0, 0.0, null)
        controller.addPoint(1.0, 1.0, null)
        controller.addPoint(2.0, 2.0, null)

        controller.undoLastPoint()
        assertEquals(2, controller.collectedPoints.size)
        assertEquals(1.0, controller.collectedPoints.last().x, 0.001)
    }

    @Test
    fun `undoLastPoint on empty does nothing`() {
        controller.undoLastPoint() // Should not throw
        assertTrue(controller.collectedPoints.isEmpty())
    }

    // ── Clear ──

    @Test
    fun `clearPoints empties collected`() {
        controller.setMode(MapMode.MEASURE)
        controller.addPoint(0.0, 0.0, null)
        controller.addPoint(1.0, 1.0, null)
        controller.clearPoints()
        assertTrue(controller.collectedPoints.isEmpty())
    }

    // ── Distance Computation ──

    @Test
    fun `computeDistance with less than 2 points is zero`() {
        controller.setMode(MapMode.MEASURE)
        controller.addPoint(5.0, 5.0, null)
        assertEquals(0.0, controller.computeDistance(), 0.001)
    }

    @Test
    fun `computeDistance horizontal line`() {
        controller.setMode(MapMode.MEASURE)
        controller.addPoint(0.0, 0.0, null)
        controller.addPoint(3.0, 0.0, null)
        controller.addPoint(3.0, 4.0, null) // 3-4-5 triangle segments
        // 3.0 + 4.0 = 7.0
        assertEquals(7.0, controller.computeDistance(), 0.001)
    }

    // ── Area Computation ──

    @Test
    fun `computeArea with less than 3 points is zero`() {
        controller.setMode(MapMode.PLOT)
        controller.addPoint(0.0, 0.0, null)
        controller.addPoint(1.0, 0.0, null)
        assertEquals(0.0, controller.computeArea(), 0.001)
    }

    @Test
    fun `computeArea unit square`() {
        controller.setMode(MapMode.PLOT)
        controller.addPoint(0.0, 0.0, null)
        controller.addPoint(10.0, 0.0, null)
        controller.addPoint(10.0, 10.0, null)
        controller.addPoint(0.0, 10.0, null)
        assertEquals(100.0, controller.computeArea(), 0.01)
    }

    @Test
    fun `computeArea triangle`() {
        controller.setMode(MapMode.PLOT)
        controller.addPoint(0.0, 0.0, null)
        controller.addPoint(6.0, 0.0, null)
        controller.addPoint(0.0, 8.0, null)
        // Area = 0.5 * 6 * 8 = 24
        assertEquals(24.0, controller.computeArea(), 0.01)
    }

    // ── Perimeter ──

    @Test
    fun `computePerimeter includes closing segment`() {
        controller.setMode(MapMode.PLOT)
        controller.addPoint(0.0, 0.0, null)
        controller.addPoint(10.0, 0.0, null)
        controller.addPoint(10.0, 10.0, null)
        controller.addPoint(0.0, 10.0, null)
        // Perimeter = 10 + 10 + 10 + 10 = 40
        assertEquals(40.0, controller.computePerimeter(), 0.01)
    }

    // ── Live Measurement ──

    @Test
    fun `getLiveMeasurement returns correct data for MEASURE distance mode`() {
        controller.setMode(MapMode.MEASURE)
        controller.setMeasureSubMode(MeasureSubMode.DISTANCE)
        controller.addPoint(0.0, 0.0, null)
        controller.addPoint(3.0, 4.0, null)

        val m = controller.getLiveMeasurement()
        assertEquals(5.0, m.distance, 0.01) // 3-4-5 triangle hypotenuse
        assertEquals(2, m.pointCount)
        assertEquals(0.0, m.area, 0.01) // Distance mode, no area
    }

    @Test
    fun `getLiveMeasurement returns correct data for MEASURE area mode`() {
        controller.setMode(MapMode.MEASURE)
        controller.setMeasureSubMode(MeasureSubMode.AREA)
        controller.addPoint(0.0, 0.0, null)
        controller.addPoint(10.0, 0.0, null)
        controller.addPoint(10.0, 10.0, null)
        controller.addPoint(0.0, 10.0, null)

        val m = controller.getLiveMeasurement()
        assertEquals(100.0, m.area, 0.01)
        assertEquals(40.0, m.perimeter, 0.01)
        assertEquals(4, m.pointCount)
    }

    @Test
    fun `getLiveMeasurement in VIEW mode returns empty`() {
        controller.setMode(MapMode.VIEW)
        val m = controller.getLiveMeasurement()
        assertEquals(0, m.pointCount)
        assertEquals(0.0, m.distance, 0.001)
    }

    // ── Polygon Close Detection ──

    @Test
    fun `isPolygonClosed returns false for less than 3 points`() {
        controller.setMode(MapMode.PLOT)
        controller.addPoint(0.0, 0.0, null)
        controller.addPoint(1.0, 0.0, null)
        assertFalse(controller.isPolygonClosed())
    }

    @Test
    fun `isPolygonClosed returns true when last is near first`() {
        controller.setMode(MapMode.PLOT)
        controller.addPoint(0.0, 0.0, null)
        controller.addPoint(10.0, 0.0, null)
        controller.addPoint(10.0, 10.0, null)
        controller.addPoint(0.5, 0.5, null) // Close to (0,0)
        assertTrue(controller.isPolygonClosed(thresholdWorld = 1.0))
    }

    @Test
    fun `isPolygonClosed returns false when last is far from first`() {
        controller.setMode(MapMode.PLOT)
        controller.addPoint(0.0, 0.0, null)
        controller.addPoint(10.0, 0.0, null)
        controller.addPoint(10.0, 10.0, null)
        assertFalse(controller.isPolygonClosed(thresholdWorld = 1.0))
    }

    // ── Coordinate Converter Tests ──

    @Test
    fun `computeDistance with coordinate converter applies conversion`() {
        // Converter that scales coordinates by 2x
        controller.coordinateConverter = { x, y -> Pair(x * 2.0, y * 2.0) }
        controller.setMode(MapMode.MEASURE)
        controller.addPoint(0.0, 0.0, null)
        controller.addPoint(3.0, 4.0, null) // raw distance = 5, converted = 10
        assertEquals(10.0, controller.computeDistance(), 0.01)
    }

    @Test
    fun `computeArea with coordinate converter applies conversion`() {
        // Converter that scales coordinates by 3x
        controller.coordinateConverter = { x, y -> Pair(x * 3.0, y * 3.0) }
        controller.setMode(MapMode.PLOT)
        controller.addPoint(0.0, 0.0, null)
        controller.addPoint(10.0, 0.0, null)
        controller.addPoint(10.0, 10.0, null)
        controller.addPoint(0.0, 10.0, null)
        // Raw area = 100, converted = 100 * 9 = 900
        assertEquals(900.0, controller.computeArea(), 0.01)
    }

    @Test
    fun `computePerimeter with coordinate converter applies conversion`() {
        // Converter that offsets coordinates (simulating UTM transform)
        controller.coordinateConverter = { x, y -> Pair(x + 376000.0, y + 30000.0) }
        controller.setMode(MapMode.PLOT)
        controller.addPoint(0.0, 0.0, null)
        controller.addPoint(10.0, 0.0, null)
        controller.addPoint(10.0, 10.0, null)
        controller.addPoint(0.0, 10.0, null)
        // Offset doesn't change distances, perimeter should still be 40
        assertEquals(40.0, controller.computePerimeter(), 0.01)
    }

    @Test
    fun `computeDistance without converter uses raw coords`() {
        // No converter set — backward compatibility
        controller.coordinateConverter = null
        controller.setMode(MapMode.MEASURE)
        controller.addPoint(0.0, 0.0, null)
        controller.addPoint(3.0, 4.0, null)
        assertEquals(5.0, controller.computeDistance(), 0.01)
    }

    @Test
    fun `converter returning null skips point`() {
        // Converter that rejects odd-indexed points
        var callCount = 0
        controller.coordinateConverter = { x, y ->
            callCount++
            if (callCount % 2 == 0) null else Pair(x, y)
        }
        controller.setMode(MapMode.MEASURE)
        controller.addPoint(0.0, 0.0, null) // call 1 → accepted
        controller.addPoint(3.0, 4.0, null) // call 2 → null, skipped
        controller.addPoint(6.0, 0.0, null) // call 3 → accepted
        // Only 2 points: (0,0) and (6,0) → distance = 6
        assertEquals(6.0, controller.computeDistance(), 0.01)
    }

    @Test
    fun `getLiveMeasurement with converter returns converted measurements`() {
        // Converter that scales by 0.5x (simulating pixel→meter scaling)
        controller.coordinateConverter = { x, y -> Pair(x * 0.5, y * 0.5) }
        controller.setMode(MapMode.MEASURE)
        controller.setMeasureSubMode(MeasureSubMode.AREA)
        controller.addPoint(0.0, 0.0, null)
        controller.addPoint(20.0, 0.0, null)
        controller.addPoint(20.0, 20.0, null)
        controller.addPoint(0.0, 20.0, null)

        val m = controller.getLiveMeasurement()
        // Raw area = 400, converted area = 400 * 0.25 = 100
        assertEquals(100.0, m.area, 0.01)
        // Raw perimeter = 80, converted perimeter = 80 * 0.5 = 40
        assertEquals(40.0, m.perimeter, 0.01)
        assertEquals(4, m.pointCount)
    }
}
