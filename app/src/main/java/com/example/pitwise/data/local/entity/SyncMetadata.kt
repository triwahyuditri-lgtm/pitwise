package com.example.pitwise.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks the last sync timestamp for each remote table.
 * Used to determine if a fresh sync is needed.
 */
@Entity(tableName = "sync_metadata")
data class SyncMetadata(
    @PrimaryKey
    @ColumnInfo(name = "table_name")
    val tableName: String,

    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long = 0L,

    @ColumnInfo(name = "record_count")
    val recordCount: Int = 0
)
