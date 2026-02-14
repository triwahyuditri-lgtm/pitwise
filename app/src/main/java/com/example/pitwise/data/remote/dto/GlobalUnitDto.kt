package com.example.pitwise.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GlobalUnitDto(
    @SerialName("unit_id")
    val unitId: String = "",

    @SerialName("brand")
    val brand: String = "",

    @SerialName("model")
    val model: String = "",

    @SerialName("category")
    val category: String = "", // "EXCA" or "DT"

    @SerialName("spec_json")
    val specJson: String = "", // JSON string of specs

    @SerialName("version")
    val version: Int = 1,

    @SerialName("updated_at")
    val updatedAt: String = "",

    @SerialName("updated_by")
    val updatedBy: String = ""
)

@Serializable
data class AuthRequest(
    val email: String,
    val password: String,
    val data: Map<String, String>? = null
)

@Serializable
data class AuthResponse(
    @SerialName("access_token")
    val accessToken: String = "",

    @SerialName("token_type")
    val tokenType: String = "",

    @SerialName("expires_in")
    val expiresIn: Long = 0,

    @SerialName("refresh_token")
    val refreshToken: String = "",

    val user: UserDto? = null
)

@Serializable
data class UserDto(
    val id: String = "",
    val email: String = "",

    @SerialName("app_metadata")
    val appMetadata: AppMetadata? = null,

    @SerialName("user_metadata")
    val userMetadata: UserMetadata? = null
)

@Serializable
data class AppMetadata(
    val provider: String = "",
    val providers: List<String> = emptyList()
)

@Serializable
data class UserMetadata(
    val role: String = "user" // "user" or "superadmin"
)

@Serializable
data class SpecJsonDto(
    @SerialName("bucket_capacity_m3")
    val bucketCapacityM3: Double? = null,

    @SerialName("vessel_capacity_m3")
    val vesselCapacityM3: Double? = null,

    @SerialName("fill_factor_default")
    val fillFactorDefault: Double? = null,

    @SerialName("cycle_time_ref_sec")
    val cycleTimeRefSec: Double? = null,

    @SerialName("speed_loaded_kmh")
    val speedLoadedKmh: Double? = null,

    @SerialName("speed_empty_kmh")
    val speedEmptyKmh: Double? = null,

    @SerialName("engine_power_hp")
    val enginePowerHp: Double? = null
)
