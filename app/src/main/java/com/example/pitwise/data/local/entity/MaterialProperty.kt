package com.example.pitwise.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "material_property")
data class MaterialProperty(
    @PrimaryKey
    @ColumnInfo(name = "remote_id")
    val remoteId: Int,

    val material: String,

    val insitu: Double = 1.0,

    @ColumnInfo(name = "fill_factor")
    val fillFactor: Double,

    @ColumnInfo(name = "swell_factor")
    val swellFactor: Double,

    val sg: Double,

    @ColumnInfo(name = "job_eff")
    val jobEff: Double,

    @ColumnInfo(name = "synced_at")
    val syncedAt: Long = System.currentTimeMillis()
)
