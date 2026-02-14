package com.example.pitwise.domain.dxf

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses DXF content into a unified DxfModel.
 * Supports: LINE, LWPOLYLINE, POLYLINE, POINT.
 * Respeects: Layers, Colors (ACI, True Color), Visibility.
 */
@Singleton
class DxfParser @Inject constructor() {

    fun parse(content: String): DxfModel {
        val lines = content.lines().map { it.trim() }
        val layers = mutableMapOf<String, DxfLayer>()
        
        val dLines = mutableListOf<DxfEntity.Line>()
        val dPolylines = mutableListOf<DxfEntity.Polyline>()
        val dPoints = mutableListOf<DxfEntity.Point>()
        val dInserts = mutableListOf<DxfEntity.Insert>()
        val blockDefinitions = mutableMapOf<String, List<DxfEntity>>()

        var i = 0
        while (i < lines.size - 1) {
            val code = lines[i]
            val value = lines.getOrNull(i + 1) ?: ""

            if (code == "0" && value.equals("SECTION", ignoreCase = true)) {
                i += 2
                val sectionName = lines.getOrNull(i + 1) ?: ""
                when (sectionName.uppercase()) {
                    "TABLES" -> {
                        i += 2
                        i = parseTables(lines, i, layers)
                    }
                    "BLOCKS" -> {
                        i += 2
                        i = parseBlocks(lines, i, layers, blockDefinitions)
                    }
                    "ENTITIES" -> {
                        i += 2
                        i = parseEntities(lines, i, layers, dLines, dPolylines, dPoints, dInserts)
                    }
                    else -> i += 2
                }
            } else {
                i += 2
            }
        }

        // Explode Inserts into primitives
        val explodedEntities = explode(dLines + dPolylines + dPoints + dInserts, blockDefinitions)

        // Compute bounds
        var minX = Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var minZ = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        var maxZ = -Double.MAX_VALUE
        
        // Clear original lists and repopulate with exploded
        dLines.clear(); dPolylines.clear(); dPoints.clear()

        explodedEntities.forEach { e ->
            when (e) {
                is DxfEntity.Line -> dLines.add(e)
                is DxfEntity.Polyline -> dPolylines.add(e)
                is DxfEntity.Point -> dPoints.add(e)
                else -> {} // Inserts should be gone
            }
        }

        fun updateBounds(x: Double, y: Double, z: Double) {
            minX = minOf(minX, x); minY = minOf(minY, y); minZ = minOf(minZ, z)
            maxX = maxOf(maxX, x); maxY = maxOf(maxY, y); maxZ = maxOf(maxZ, z)
        }

        dLines.forEach { l ->
            updateBounds(l.start.x, l.start.y, l.start.z)
            updateBounds(l.end.x, l.end.y, l.end.z)
        }
        dPolylines.forEach { p ->
            p.vertices.forEach { v -> updateBounds(v.x, v.y, v.z) }
        }
        dPoints.forEach { p ->
             updateBounds(p.x, p.y, p.z)
        }

        if (minX == Double.MAX_VALUE) {
            minX = 0.0; minY = 0.0; minZ = 0.0
            maxX = 0.0; maxY = 0.0; maxZ = 0.0
        }

        // Diagnostic logging per spec
        android.util.Log.d("DXF_PARSER", "Parsed: lines=${dLines.size} polylines=${dPolylines.size} points=${dPoints.size}")
        android.util.Log.d("DXF_PARSER", "Bounds: X[$minX..$maxX] Y[$minY..$maxY] Z[$minZ..$maxZ]")
        if (dLines.size + dPolylines.size + dPoints.size == 0) {
            android.util.Log.w("DXF_PARSER", "WARNING: Zero entities parsed — potential parsing failure")
        }

        return DxfModel(
            lines = dLines,
            polylines = dPolylines,
            points = dPoints,
            layers = layers,
            bounds = DxfBounds(minX, minY, minZ, maxX, maxY, maxZ)
        )
    }

    private fun parseTables(
        lines: List<String>, 
        startIndex: Int, 
        layers: MutableMap<String, DxfLayer>
    ): Int {
        var i = startIndex
        while (i < lines.size - 1) {
            val code = lines[i]
            val value = lines[i + 1]

            if (code == "0" && value.equals("ENDSEC", ignoreCase = true)) {
                return i + 2
            }

            if (code == "0" && value.equals("TABLE", ignoreCase = true)) {
                // Check if it's LAYER table
                // Usually followed by 2 -> LAYER
                i += 2
                if (i < lines.size - 1 && lines[i] == "2" && lines[i+1].equals("LAYER", ignoreCase = true)) {
                   i += 2
                   i = parseLayerTable(lines, i, layers)
                }
            } else {
                i += 2
            }
        }
        return i
    }

    private fun parseLayerTable(
        lines: List<String>,
        startIndex: Int,
        layers: MutableMap<String, DxfLayer>
    ): Int {
        var i = startIndex
        
        while (i < lines.size - 1) {
            val code = lines[i]
            val value = lines[i + 1]

            if (code == "0" && value.equals("ENDTAB", ignoreCase = true)) return i + 2
            
            if (code == "0" && value.equals("LAYER", ignoreCase = true)) {
                i += 2
                // Parse single layer
                var name = ""
                var color = 7 // Default white
                var flags = 0
                
                while (i < lines.size - 1) {
                    val c = lines[i]
                    val v = lines[i+1]
                    
                    if (c == "0") break // Next entity or end
                    
                    when (c) {
                        "2" -> name = v
                        "62" -> color = v.toIntOrNull() ?: 7
                        "70" -> flags = v.toIntOrNull() ?: 0
                    }
                    i += 2
                }
                
                // If color is negative, layer is off (though usually 62 is color, negative means off in layer table too?)
                // Actually in Layer Table, 62 is color. If negative, it means layer is off. 
                // Wait, standard says: 
                // "Color number (if negative, layer is off)"
                
                val isVisible = color >= 0 && (flags and 1 == 0) // Bit 1 of 70 is frozen
                val absColor = if (color < 0) -color else color
                
                layers[name] = DxfLayer(name, absColor, isVisible)
            } else {
                i += 2
            }
        }
        return i
    }

    private fun parseBlocks(
        lines: List<String>,
        startIndex: Int,
        layers: Map<String, DxfLayer>,
        blockDefinitions: MutableMap<String, List<DxfEntity>>
    ): Int {
        var i = startIndex
        while (i < lines.size - 1) {
            val code = lines[i]; val value = lines[i+1]

            if (code == "0" && value.equals("ENDSEC", ignoreCase = true)) return i + 2

            if (code == "0" && value.equals("BLOCK", ignoreCase = true)) {
                // Start of a BLOCK definition
                i += 2
                var blockName = ""
                // Parse Block Header
                while(i < lines.size - 1) {
                    val c = lines[i]; val v = lines[i+1]
                    if (c == "0") break // End of header (should be next entity or ENDBLK)
                    if (c == "2") blockName = v // Block Name
                    if (c == "3") blockName = v // Block Name (Common)
                    i += 2
                }

                // Parse Block Entities
                val bLines = mutableListOf<DxfEntity.Line>()
                val bPolylines = mutableListOf<DxfEntity.Polyline>()
                val bPoints = mutableListOf<DxfEntity.Point>()
                val bInserts = mutableListOf<DxfEntity.Insert>()

                // Call parseEntities but stop at ENDBLK
                // We need a version of parseEntities that stops at ENDBLK?
                // Or we can just use the loop here directly.
                // Let's reuse parseEntities but modify it to accept a stopCondition? 
                // Or just loop here. Duplicate for safety/simplicity first.
                
                // Actually, duplicate logic is safer for now to avoid refactoring parseEntities signature too much.
                
                 while (i < lines.size - 1) {
                    val c = lines[i]; val v = lines[i+1]
                    
                    if (c == "0" && v.equals("ENDBLK", ignoreCase = true)) {
                        i += 2
                        break
                    }
                    
                    if (c == "0") {
                        // Entity inside block
                         when (v.uppercase()) {
                            "LINE" -> {
                                i += 2
                                val (e, nextI) = parseLine(lines, i, layers); if (e!=null) bLines.add(e); i = nextI
                            }
                            "LWPOLYLINE" -> {
                                i += 2
                                val (e, nextI) = parseLwPolyline(lines, i, layers); if (e!=null) bPolylines.add(e); i = nextI
                            }
                            "POLYLINE" -> {
                                i += 2
                                val (e, nextI) = parsePolyline(lines, i, layers); if (e!=null) bPolylines.add(e); i = nextI
                            }
                            "POINT" -> {
                                i += 2
                                val (e, nextI) = parsePoint(lines, i, layers); if (e!=null) bPoints.add(e); i = nextI
                            }
                            "CIRCLE" -> {
                                i += 2
                                val (e, nextI) = parseCircle(lines, i, layers); if (e!=null) bPolylines.add(e.toPolyline()); i = nextI
                            }
                            "ARC" -> {
                                i += 2
                                val (e, nextI) = parseArc(lines, i, layers); if (e!=null) bPolylines.add(e.toPolyline()); i = nextI
                            }
                            "INSERT" -> {
                                i += 2
                                val (e, nextI) = parseInsert(lines, i, layers); if (e!=null) bInserts.add(e); i = nextI
                            }
                            else -> i += 2
                        }
                    } else {
                        i += 2
                    }
                }
                
                if (blockName.isNotEmpty()) {
                    blockDefinitions[blockName] = bLines + bPolylines + bPoints + bInserts
                }
            } else {
                i += 2
            }
        }
        return i
    }

    private fun parseEntities(
        lines: List<String>,
        startIndex: Int,
        layers: Map<String, DxfLayer>,
        dLines: MutableList<DxfEntity.Line>,
        dPolylines: MutableList<DxfEntity.Polyline>,
        dPoints: MutableList<DxfEntity.Point>,
        dInserts: MutableList<DxfEntity.Insert>
    ): Int {
        var i = startIndex
        while (i < lines.size - 1) {
            val code = lines[i]
            val value = lines[i + 1]

            if (code == "0" && value.equals("ENDSEC", ignoreCase = true)) return i + 2

            if (code == "0") {
                when (value.uppercase()) {
                    "LINE" -> {
                        i += 2
                        val (entity, nextI) = parseLine(lines, i, layers)
                        if (entity != null) dLines.add(entity)
                        i = nextI
                    }
                    "LWPOLYLINE" -> {
                        i += 2
                        val (entity, nextI) = parseLwPolyline(lines, i, layers)
                        if (entity != null) dPolylines.add(entity)
                        i = nextI
                    }
                    "POLYLINE" -> {
                        i += 2
                        val (entity, nextI) = parsePolyline(lines, i, layers)
                        if (entity != null) dPolylines.add(entity)
                        i = nextI
                    }
                    "POINT" -> {
                        i += 2
                        val (entity, nextI) = parsePoint(lines, i, layers)
                        if (entity != null) dPoints.add(entity)
                        i = nextI
                    }
                    "CIRCLE" -> {
                        i += 2
                        val (entity, nextI) = parseCircle(lines, i, layers)
                        // Add to dLines/Polylines? We might need a generic list or convert to Polyline
                        // For now, let's treat Circle as a list of lines or add a new Entity type.
                        // DxfEntity definition likely needs updates.
                        // If we can't update DxfEntity easily, we can approximate as Polyline.
                        if (entity != null) dPolylines.add(entity.toPolyline())
                        i = nextI
                    }
                    "ARC" -> {
                        i += 2
                        val (entity, nextI) = parseArc(lines, i, layers)
                        if (entity != null) dPolylines.add(entity.toPolyline())
                        i = nextI
                    }
                    "INSERT" -> {
                        i += 2
                        val (entity, nextI) = parseInsert(lines, i, layers)
                        if (entity != null) dInserts.add(entity)
                        i = nextI
                    }
                    else -> i += 2 // Skip unsupported
                }
            } else {
                i += 2
            }
        }
        return i
    }
    
    // --- Entity Parsers ---
    
    // Helper to resolve color at the end of parsing an entity
    private fun ensureColor(
        layerName: String, 
        colorIndex: Int?, 
        trueColor: Int?, 
        layers: Map<String, DxfLayer>
    ): Pair<Int, Boolean>? {
        // If entity explicitly says "invisible" via negative color, we skip it?
        // User says: "If group 62 value negative: Entity is invisible -> skip rendering."
        if (colorIndex != null && colorIndex < 0) return null
        
        // Check layer visibility
        // "If layer off: Skip rendering."
        val layer = layers[layerName]
        // If layer definition exists and is not visible, return null
        // If layer lacks definition, assume visible? Defaults to White if not found.
        if (layer != null && !layer.isVisible) return null
        
        val layerColor = layer?.colorIndex ?: 7
        
        val resolved = DxfColorResolver.resolveColor(trueColor, colorIndex, layerColor)
        val isByBlock = (colorIndex == 0)
        
        return resolved to isByBlock
    }

    private fun parseLine(
        lines: List<String>, 
        index: Int, 
        layers: Map<String, DxfLayer>
    ): Pair<DxfEntity.Line?, Int> {
        var i = index
        var x1 = 0.0; var y1 = 0.0; var z1 = 0.0
        var x2 = 0.0; var y2 = 0.0; var z2 = 0.0
        var layer = "0"
        var colorIndex: Int? = null
        var trueColor: Int? = null

        while (i < lines.size - 1) {
            val c = lines[i]
            val v = lines[i+1]
            if (c == "0") break
            
            when (c) {
                "8" -> layer = v
                "62" -> colorIndex = v.toIntOrNull()
                "420" -> trueColor = v.toIntOrNull()
                "10" -> x1 = v.toDoubleOrNull() ?: 0.0
                "20" -> y1 = v.toDoubleOrNull() ?: 0.0
                "30" -> z1 = v.toDoubleOrNull() ?: 0.0
                "11" -> x2 = v.toDoubleOrNull() ?: 0.0
                "21" -> y2 = v.toDoubleOrNull() ?: 0.0
                "31" -> z2 = v.toDoubleOrNull() ?: 0.0
            }
            i += 2
        }
        
        val result = ensureColor(layer, colorIndex, trueColor, layers)
        if (result == null) return null to i
        val (color, isByBlock) = result
        
        return DxfEntity.Line(
            DxfVertex(x1, y1, z1),
            DxfVertex(x2, y2, z2),
            layer,
            color,
            isByBlock
        ) to i
    }
    
    private fun parseLwPolyline(
        lines: List<String>, 
        index: Int,
        layers: Map<String, DxfLayer>
    ): Pair<DxfEntity.Polyline?, Int> {
        var i = index
        var layer = "0"
        var colorIndex: Int? = null
        var trueColor: Int? = null
        var isClosed = false
        val vertices = mutableListOf<DxfVertex>()
        var elevation = 0.0
        
        // Deferred vertex assembly: collect X, Y, Z separately
        // and finalize the vertex when the next vertex starts (code 10) or entity ends (code 0).
        var pendingX: Double? = null
        var pendingY: Double? = null
        var pendingZ: Double? = null
        
        fun finalizePendingVertex() {
            if (pendingX != null && pendingY != null) {
                vertices.add(DxfVertex(pendingX!!, pendingY!!, pendingZ ?: elevation))
                pendingX = null
                pendingY = null
                pendingZ = null
            }
        }
        
        while (i < lines.size - 1) {
            val c = lines[i]
            val v = lines[i+1]
            if (c == "0") {
                finalizePendingVertex()
                break
            }
            
            when (c) {
                "8" -> layer = v
                "62" -> colorIndex = v.toIntOrNull()
                "420" -> trueColor = v.toIntOrNull()
                "70" -> isClosed = (v.toIntOrNull() ?: 0) and 1 == 1
                "38" -> elevation = v.toDoubleOrNull() ?: 0.0
                "10" -> {
                    // New vertex starting — finalize previous if any
                    finalizePendingVertex()
                    pendingX = v.toDoubleOrNull() ?: 0.0
                }
                "20" -> {
                    pendingY = v.toDoubleOrNull() ?: 0.0
                }
                "30" -> {
                    pendingZ = v.toDoubleOrNull() ?: 0.0
                }
            }
            i += 2
        }
        
        val result = ensureColor(layer, colorIndex, trueColor, layers)
        if (result == null || vertices.isEmpty()) return null to i
        val (color, isByBlock) = result
        
        return DxfEntity.Polyline(vertices, isClosed, layer, color, isByBlock) to i
    }
    
    private fun parsePolyline(
        lines: List<String>, 
        index: Int,
        layers: Map<String, DxfLayer>
    ): Pair<DxfEntity.Polyline?, Int> {
        var i = index
        var layer = "0"
        var colorIndex: Int? = null
        var trueColor: Int? = null
        var isClosed = false
        var polylineZ = 0.0 // Elevation for 2D polylines
        val vertices = mutableListOf<DxfVertex>()
        
        // Header
        while (i < lines.size - 1) {
            val c = lines[i]
            val v = lines[i+1]
            if (c == "0") break // Could be SEQEND or VERTEX
            
            when (c) {
                "8" -> layer = v
                "62" -> colorIndex = v.toIntOrNull()
                "420" -> trueColor = v.toIntOrNull()
                "70" -> isClosed = (v.toIntOrNull() ?: 0) and 1 == 1
                // 30 might be elevation in some contexts or Z for 3D polyline start
                "30" -> polylineZ = v.toDoubleOrNull() ?: 0.0 
            }
            i += 2
        }
        
        // Vertices
        while (i < lines.size - 1) {
            val c = lines[i]
            val v = lines[i+1]
            
            if (c == "0" && v.equals("SEQEND", ignoreCase = true)) {
                i += 2
                break
            }
            
            if (c == "0" && v.equals("VERTEX", ignoreCase = true)) {
                i += 2
                // Parse Vertex
                var vx = 0.0
                var vy = 0.0
                var vz = polylineZ // Default to polyline elevation
                var hasZ = false
                
                while (i < lines.size - 1) {
                    val vc = lines[i]
                    val vv = lines[i+1]
                    if (vc == "0") break
                    
                    when (vc) {
                        "10" -> vx = vv.toDoubleOrNull() ?: 0.0
                        "20" -> vy = vv.toDoubleOrNull() ?: 0.0
                        "30" -> {
                            vz = vv.toDoubleOrNull() ?: 0.0
                            hasZ = true
                        }
                    }
                    i += 2
                }
                vertices.add(DxfVertex(vx, vy, vz))
            } else {
                i += 2
            }
        }
        
        val result = ensureColor(layer, colorIndex, trueColor, layers)
        if (result == null || vertices.isEmpty()) return null to i
        val (color, isByBlock) = result
        
        return DxfEntity.Polyline(vertices, isClosed, layer, color, isByBlock) to i
    }
    
    // Internal temp classes for Circle/Arc parsing before conversion
    private data class TempCircle(val cx: Double, val cy: Double, val r: Double, val layer: String, val color: Int, val isByBlock: Boolean) {
        fun toPolyline(): DxfEntity.Polyline {
            val vertices = mutableListOf<DxfVertex>()
            val segments = 36
            for (i in 0 until segments) {
                val angle = 2 * Math.PI * i / segments
                vertices.add(DxfVertex(cx + r * Math.cos(angle), cy + r * Math.sin(angle), 0.0))
            }
            vertices.add(vertices.first()) // Close it
            return DxfEntity.Polyline(vertices, true, layer, color, isByBlock)
        }
    }

    private data class TempArc(val cx: Double, val cy: Double, val r: Double, val startAngle: Double, val endAngle: Double, val layer: String, val color: Int, val isByBlock: Boolean) {
        fun toPolyline(): DxfEntity.Polyline {
            val vertices = mutableListOf<DxfVertex>()
            var start = startAngle
            var end = endAngle
            if (end < start) end += 360.0
            
            val sweep = end - start
            val segments = maxOf(4, (sweep / 10).toInt())
            
            for (i in 0..segments) {
                val angle = Math.toRadians(start + (sweep * i / segments))
                vertices.add(DxfVertex(cx + r * Math.cos(angle), cy + r * Math.sin(angle), 0.0))
            }
            return DxfEntity.Polyline(vertices, false, layer, color, isByBlock)
        }
    }

    private fun parseCircle(
        lines: List<String>, index: Int, layers: Map<String, DxfLayer>
    ): Pair<TempCircle?, Int> {
        var i = index
        var cx = 0.0; var cy = 0.0; var r = 0.0
        var layer = "0"; var colorIndex: Int? = null; var trueColor: Int? = null
        
        while (i < lines.size - 1) {
            val c = lines[i]; val v = lines[i+1]
            if (c == "0") break
            when (c) {
                "8" -> layer = v
                "62" -> colorIndex = v.toIntOrNull()
                "420" -> trueColor = v.toIntOrNull()
                "10" -> cx = v.toDoubleOrNull() ?: 0.0
                "20" -> cy = v.toDoubleOrNull() ?: 0.0
                "40" -> r = v.toDoubleOrNull() ?: 0.0
            }
            i += 2
        }
        val result = ensureColor(layer, colorIndex, trueColor, layers) ?: return null to i
        val (color, isByBlock) = result
        return TempCircle(cx, cy, r, layer, color, isByBlock) to i
    }

    private fun parseArc(
        lines: List<String>, index: Int, layers: Map<String, DxfLayer>
    ): Pair<TempArc?, Int> {
        var i = index
        var cx = 0.0; var cy = 0.0; var r = 0.0
        var startAngle = 0.0; var endAngle = 0.0
        var layer = "0"; var colorIndex: Int? = null; var trueColor: Int? = null
        
        while (i < lines.size - 1) {
            val c = lines[i]; val v = lines[i+1]
            if (c == "0") break
            when (c) {
                "8" -> layer = v
                "62" -> colorIndex = v.toIntOrNull()
                "420" -> trueColor = v.toIntOrNull()
                "10" -> cx = v.toDoubleOrNull() ?: 0.0
                "20" -> cy = v.toDoubleOrNull() ?: 0.0
                "40" -> r = v.toDoubleOrNull() ?: 0.0
                "50" -> startAngle = v.toDoubleOrNull() ?: 0.0
                "51" -> endAngle = v.toDoubleOrNull() ?: 0.0
            }
            i += 2
        }
        val result = ensureColor(layer, colorIndex, trueColor, layers) ?: return null to i
        val (color, isByBlock) = result
        return TempArc(cx, cy, r, startAngle, endAngle, layer, color, isByBlock) to i
    }



    private fun parsePoint(
        lines: List<String>, 
        index: Int,
        layers: Map<String, DxfLayer>
    ): Pair<DxfEntity.Point?, Int> {
        var i = index
        var x = 0.0
        var y = 0.0
        var z = 0.0
        var layer = "0"
        var colorIndex: Int? = null
        var trueColor: Int? = null
        
        while (i < lines.size - 1) {
            val c = lines[i]
            val v = lines[i+1]
            if (c == "0") break
            
            when (c) {
                "8" -> layer = v
                "62" -> colorIndex = v.toIntOrNull()
                "420" -> trueColor = v.toIntOrNull()
                "10" -> x = v.toDoubleOrNull() ?: 0.0
                "20" -> y = v.toDoubleOrNull() ?: 0.0
                "30" -> z = v.toDoubleOrNull() ?: 0.0
            }
            i += 2
        }
        
        val result = ensureColor(layer, colorIndex, trueColor, layers)
        if (result == null) return null to i
        val (color, isByBlock) = result
        
        return DxfEntity.Point(x, y, z, layer, color, isByBlock) to i
    }

    private fun parseInsert(
        lines: List<String>, 
        index: Int,
        layers: Map<String, DxfLayer>
    ): Pair<DxfEntity.Insert?, Int> {
        var i = index
        var blockName = ""
        var x = 0.0; var y = 0.0; var z = 0.0
        var scaleX = 1.0; var scaleY = 1.0; var scaleZ = 1.0
        var rotation = 0.0
        var layer = "0"
        var colorIndex: Int? = null; var trueColor: Int? = null
        
        while (i < lines.size - 1) {
            val c = lines[i]; val v = lines[i+1]
            if (c == "0") break
            
            when (c) {
                "2" -> blockName = v
                "8" -> layer = v
                "62" -> colorIndex = v.toIntOrNull()
                "420" -> trueColor = v.toIntOrNull()
                "10" -> x = v.toDoubleOrNull() ?: 0.0
                "20" -> y = v.toDoubleOrNull() ?: 0.0
                "30" -> z = v.toDoubleOrNull() ?: 0.0
                "41" -> scaleX = v.toDoubleOrNull() ?: 1.0
                "42" -> scaleY = v.toDoubleOrNull() ?: 1.0
                "43" -> scaleZ = v.toDoubleOrNull() ?: 1.0
                "50" -> rotation = v.toDoubleOrNull() ?: 0.0
            }
            i += 2
        }

        // Inserts can be on invisible layers too? yes.
        val result = ensureColor(layer, colorIndex, trueColor, layers) ?: return null to i
        val (color, isByBlock) = result
        
        return DxfEntity.Insert(
            blockName,
            DxfVertex(x, y, z),
            scaleX, scaleY, scaleZ,
            rotation,
            layer,
            color,
            isByBlock
        ) to i
    }

    /**
     * Recursively explodes inserts into primitives.
     * depth limit 50 to prevent stack overflow.
     */
    private fun explode(
        entities: List<DxfEntity>,
        blocks: Map<String, List<DxfEntity>>,
        depth: Int = 0
    ): List<DxfEntity> {
        if (depth > 50) return emptyList()

        val result = mutableListOf<DxfEntity>()

        for (e in entities) {
            if (e is DxfEntity.Insert) {
                // Find block definition
                val blockEntities = blocks[e.blockName]
                if (blockEntities != null) {
                    // 1. Transform block entities by Insert's params
                    val transformed = blockEntities.map { child ->
                        val t = child.transform(
                            scaleX = e.scaleX,
                            scaleY = e.scaleY,
                            scaleZ = e.scaleZ,
                            rotation = e.rotation,
                            tx = e.insertionPoint.x,
                            ty = e.insertionPoint.y,
                            tz = e.insertionPoint.z
                        )
                        
                        // 2. Resolve ByBlock color
                        // If child is ByBlock, it takes the Insert's color.
                        val newColor = if (child.isByBlock) e.color else child.color
                        
                        // Apply new color
                        when (t) {
                            is DxfEntity.Line -> t.copy(color = newColor)
                            is DxfEntity.Polyline -> t.copy(color = newColor)
                            is DxfEntity.Point -> t.copy(color = newColor)
                            is DxfEntity.Insert -> t.copy(color = newColor)
                        }
                    }
                    // 3. Recursively explode result (if it contains nested inserts)
                    result.addAll(explode(transformed, blocks, depth + 1))
                }
            } else {
                result.add(e)
            }
        }
        return result
    }
}
