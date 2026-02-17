package com.example.pitwise.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "equipment_hauler")
data class EquipmentHauler(
    @PrimaryKey
    @ColumnInfo(name = "remote_id")
    val remoteId: Int,

    val category: String = "Hauler",

    @ColumnInfo(name = "class_name")
    val className: String = "",

    val material: String = "",

    @ColumnInfo(name = "vessel_lcm")
    val vesselLcm: Double = 0.0,

    @ColumnInfo(name = "vessel_bcm_or_ton")
    val vesselBcmOrTon: Double = 0.0,

    @ColumnInfo(name = "spotting_sec")
    val spottingSec: Double = 0.0,

    @ColumnInfo(name = "travel_sec")
    val travelSec: Double = 200.0,

    @ColumnInfo(name = "queueing_time_sec")
    val queueingTimeSec: Double = 0.0,

    @ColumnInfo(name = "dumping_sec")
    val dumpingSec: Double = 0.0,

    @ColumnInfo(name = "return_kmh")
    val returnKmh: Double = 0.0,

    @ColumnInfo(name = "cycle_time_sec")
    val cycleTimeSec: Double = 0.0,

    @ColumnInfo(name = "cycletime_min")
    val cycleTimeMin: Double = 0.0,

    @ColumnInfo(name = "specific_gravity")
    val specificGravity: Double = 1.0,

    val productivity: Double = 0.0,

    @ColumnInfo(name = "synced_at")
    val syncedAt: Long = System.currentTimeMillis()
)
