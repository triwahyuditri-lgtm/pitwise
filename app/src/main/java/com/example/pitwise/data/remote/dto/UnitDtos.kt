package com.example.pitwise.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MaterialDto(
    val id: Int = 0,
    val material: String,
    val insitu: Double = 1.0,
    @SerialName("fill_factor") val fillFactor: Double,
    @SerialName("swell_factor") val swellFactor: Double,
    val sg: Double,
    @SerialName("job_eff") val jobEff: Double
)

@Serializable
data class LoaderDto(
    val id: Int = 0,
    val category: String = "Digger",
    @SerialName("class") val className: String,
    val material: String,
    @SerialName("bucket_cap_lcm") val bucketCapLcm: Double,
    @SerialName("bucket_cap_bcm") val bucketCapBcm: Double,
    @SerialName("fill_factor") val fillFactor: Double,
    @SerialName("swell_factor") val swellFactor: Double,
    @SerialName("job_eff") val jobEff: Double,
    val sg: Double,
    @SerialName("cycle_time_sec") val cycleTimeSec: Double,
    val productivity: Double
)

@Serializable
data class HaulerDto(
    val id: Int = 0,
    val category: String = "Hauler",
    @SerialName("class") val className: String = "",
    val material: String = "",
    @SerialName("vessel_lcm") val vesselLcm: Double = 0.0,
    @SerialName("vessel_bcm_or_ton") val vesselBcmOrTon: Double = 0.0,
    @SerialName("spotting_sec") val spottingSec: Double = 0.0,
    @SerialName("travel_sec") val travelSec: Double = 200.0,
    @SerialName("queueing_time_sec") val queueingTimeSec: Double = 0.0,
    @SerialName("dumping_sec") val dumpingSec: Double = 0.0,
    @SerialName("return_kmh") val returnKmh: Double = 0.0,
    @SerialName("cycle_time_sec") val cycleTimeSec: Double = 0.0,
    @SerialName("cycletime_min") val cycleTimeMin: Double = 0.0,
    @SerialName("specific_gravity") val specificGravity: Double = 1.0,
    val productivity: Double = 0.0
)

@Serializable
data class DozerDto(
    val id: Int = 0,
    val category: String = "Bulldozer",
    @SerialName("class") val className: String,
    val material: String,
    @SerialName("blade_width_m") val bladeWidthM: Double,
    @SerialName("blade_height_m") val bladeHeightM: Double,
    @SerialName("blade_volume_m3") val bladeVolumeM3: Double,
    @SerialName("length_m") val lengthM: Double,
    @SerialName("jarak_dozing_m") val jarakDozingM: Double,
    @SerialName("speed_forward_kmh") val speedForwardKmh: Double,
    @SerialName("speed_reverse_kmh") val speedReverseKmh: Double,
    @SerialName("shifting_min") val shiftingMin: Double,
    @SerialName("positioning_min") val positioningMin: Double,
    @SerialName("cycle_time_min") val cycleTimeMin: Double,
    @SerialName("job_eff") val jobEff: Double,
    @SerialName("blade_factor") val bladeFactor: Double,
    val productivity: Double
)
