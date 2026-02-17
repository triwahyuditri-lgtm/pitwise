package com.example.pitwise.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "equipment_loader")
data class EquipmentLoader(
    @PrimaryKey
    @ColumnInfo(name = "remote_id")
    val remoteId: Int,

    val category: String = "Digger",

    @ColumnInfo(name = "class_name")
    val className: String,

    val material: String,

    @ColumnInfo(name = "bucket_cap_lcm")
    val bucketCapLcm: Double,

    @ColumnInfo(name = "bucket_cap_bcm")
    val bucketCapBcm: Double,

    @ColumnInfo(name = "fill_factor")
    val fillFactor: Double,

    @ColumnInfo(name = "swell_factor")
    val swellFactor: Double,

    @ColumnInfo(name = "job_eff")
    val jobEff: Double,

    val sg: Double,

    @ColumnInfo(name = "cycle_time_sec")
    val cycleTimeSec: Double,

    val productivity: Double,

    @ColumnInfo(name = "synced_at")
    val syncedAt: Long = System.currentTimeMillis()
)
