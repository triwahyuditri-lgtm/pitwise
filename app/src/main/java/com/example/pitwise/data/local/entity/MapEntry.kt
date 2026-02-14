package com.example.pitwise.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a saved map file (PDF or DXF) in the local database.
 * Only one map can be active at a time.
 */
@Entity(tableName = "maps")
data class MapEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    /** "PDF" or "DXF" */
    val type: String,

    /** SAF content URI string */
    val uri: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_opened")
    val lastOpened: Long = 0,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = false
)
