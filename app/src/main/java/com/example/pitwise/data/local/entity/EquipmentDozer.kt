package com.example.pitwise.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "equipment_dozer")
data class EquipmentDozer(
    @PrimaryKey
    @ColumnInfo(name = "remote_id")
    val remoteId: Int,

    val category: String = "Bulldozer",

    @ColumnInfo(name = "class_name")
    val className: String,

    val material: String,

    @ColumnInfo(name = "blade_width_m")
    val bladeWidthM: Double,

    @ColumnInfo(name = "blade_height_m")
    val bladeHeightM: Double,

    @ColumnInfo(name = "blade_volume_m3")
    val bladeVolumeM3: Double,

    @ColumnInfo(name = "length_m")
    val lengthM: Double,

    @ColumnInfo(name = "jarak_dozing_m")
    val jarakDozingM: Double,

    @ColumnInfo(name = "speed_forward_kmh")
    val speedForwardKmh: Double,

    @ColumnInfo(name = "speed_reverse_kmh")
    val speedReverseKmh: Double,

    @ColumnInfo(name = "shifting_min")
    val shiftingMin: Double,

    @ColumnInfo(name = "positioning_min")
    val positioningMin: Double,

    @ColumnInfo(name = "cycle_time_min")
    val cycleTimeMin: Double,

    @ColumnInfo(name = "job_eff")
    val jobEff: Double,

    @ColumnInfo(name = "blade_factor")
    val bladeFactor: Double,

    val productivity: Double,

    @ColumnInfo(name = "synced_at")
    val syncedAt: Long = System.currentTimeMillis()
)
