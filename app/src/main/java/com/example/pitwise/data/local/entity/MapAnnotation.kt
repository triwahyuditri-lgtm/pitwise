package com.example.pitwise.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Represents a user-drawn annotation on a map.
 * Can be a marker (single point), line (list of points), or polygon (closed shape).
 * Points are stored as JSON string for flexibility.
 */
@Entity(
    tableName = "map_annotations",
    foreignKeys = [
        ForeignKey(
            entity = MapEntry::class,
            parentColumns = ["id"],
            childColumns = ["map_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MapAnnotation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "map_id", index = true)
    val mapId: Long,

    /** "MARKER", "LINE", or "POLYGON" */
    val type: String,

    /**
     * JSON array of coordinate pairs: [[x1,y1],[x2,y2],...]
     * For MARKER: single pair [[x,y]]
     * For LINE: ordered list of points
     * For POLYGON: ordered list of vertices (auto-closed)
     */
    @ColumnInfo(name = "points_json")
    val pointsJson: String,

    val label: String = "",

    val color: String = "#00E5FF",

    /** Computed distance in meters (for LINE) */
    val distance: Double = 0.0,

    /** Computed area in m² (for POLYGON) */
    val area: Double = 0.0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
