package com.example.pitwise.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks imported base data versions.
 * Prevents duplicate imports and supports future version upgrades.
 */
@Entity(tableName = "base_unit_version")
data class BaseUnitVersion(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "version_number")
    val versionNumber: Int,

    @ColumnInfo(name = "imported_at")
    val importedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "record_count")
    val recordCount: Int = 0
)
