package com.example.pitwise.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "unit_spec",
    foreignKeys = [
        ForeignKey(
            entity = UnitModel::class,
            parentColumns = ["id"],
            childColumns = ["model_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("model_id")]
)
data class UnitSpec(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "model_id")
    val modelId: Long,

    // ── Common fields ──────────────────────────────────
    @ColumnInfo(name = "material")
    val material: String? = null, // "Liq. Mud", "Mud", "Sand", "Topsoil", "Clay", "Freedig", "Ripping", "Blasted", "Coal"

    @ColumnInfo(name = "bucket_capacity_m3")
    val bucketCapacityM3: Double? = null, // BCM capacity for Loader

    @ColumnInfo(name = "bucket_capacity_lcm")
    val bucketCapacityLcm: Double? = null, // LCM capacity for Loader

    @ColumnInfo(name = "vessel_capacity_m3")
    val vesselCapacityM3: Double? = null, // Vessel LCM for Hauler

    @ColumnInfo(name = "vessel_capacity_ton")
    val vesselCapacityTon: Double? = null, // Vessel BCM or Ton for Hauler

    @ColumnInfo(name = "fill_factor_default")
    val fillFactorDefault: Double? = null,

    @ColumnInfo(name = "swell_factor")
    val swellFactor: Double? = null,

    @ColumnInfo(name = "specific_gravity")
    val specificGravity: Double? = null, // SG

    @ColumnInfo(name = "job_efficiency")
    val jobEfficiency: Double? = null,

    @ColumnInfo(name = "cycle_time_ref_sec")
    val cycleTimeRefSec: Double? = null,

    @ColumnInfo(name = "speed_loaded_kmh")
    val speedLoadedKmh: Double? = null,

    @ColumnInfo(name = "speed_empty_kmh")
    val speedEmptyKmh: Double? = null,

    @ColumnInfo(name = "engine_power_hp")
    val enginePowerHp: Double? = null,

    @ColumnInfo(name = "productivity")
    val productivity: Double? = null, // Pdt'y

    // ── Hauler-specific fields ─────────────────────────
    @ColumnInfo(name = "spotting_time_sec")
    val spottingTimeSec: Double? = null,

    @ColumnInfo(name = "queueing_time_sec")
    val queueingTimeSec: Double? = null,

    @ColumnInfo(name = "dumping_time_sec")
    val dumpingTimeSec: Double? = null,

    @ColumnInfo(name = "return_speed_kmh")
    val returnSpeedKmh: Double? = null,

    // ── Dozer-specific fields ──────────────────────────
    @ColumnInfo(name = "blade_width_m")
    val bladeWidthM: Double? = null,

    @ColumnInfo(name = "blade_height_m")
    val bladeHeightM: Double? = null,

    @ColumnInfo(name = "blade_volume_m3")
    val bladeVolumeM3: Double? = null,

    @ColumnInfo(name = "dozing_length_m")
    val dozingLengthM: Double? = null,

    @ColumnInfo(name = "blade_factor")
    val bladeFactor: Double? = null,

    @ColumnInfo(name = "forward_speed_kmh")
    val forwardSpeedKmh: Double? = null,

    @ColumnInfo(name = "reverse_speed_kmh")
    val reverseSpeedKmh: Double? = null,

    @ColumnInfo(name = "shifting_time_min")
    val shiftingTimeMin: Double? = null,

    @ColumnInfo(name = "positioning_time_min")
    val positioningTimeMin: Double? = null,

    // ── Metadata ───────────────────────────────────────
    @ColumnInfo(name = "created_by")
    val createdBy: String = "SYSTEM" // "SYSTEM", "SUPERADMIN", "USER"
)
