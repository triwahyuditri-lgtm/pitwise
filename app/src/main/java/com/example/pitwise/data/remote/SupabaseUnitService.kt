package com.example.pitwise.data.remote

import com.example.pitwise.data.remote.dto.GlobalUnitDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for interacting with the global_units table on Supabase.
 * Supports reading units (all roles) and publishing (superadmin only).
 */
@Singleton
class SupabaseUnitService @Inject constructor(
    private val httpClient: HttpClient,
    private val authService: SupabaseAuthService
) {
    /**
     * Fetch all global units from Supabase.
     */
    suspend fun fetchGlobalUnits(): Result<List<GlobalUnitDto>> {
        return try {
            val response = httpClient.get("${SupabaseConfig.REST_ENDPOINT}/global_units?select=*") {
                header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                header("Authorization", "Bearer ${authService.getAccessToken() ?: SupabaseConfig.SUPABASE_ANON_KEY}")
            }
            val units: List<GlobalUnitDto> = response.body()
            Result.success(units)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch global units that are newer than the given version.
     */
    suspend fun fetchUnitsNewerThan(version: Int): Result<List<GlobalUnitDto>> {
        return try {
            val response = httpClient.get(
                "${SupabaseConfig.REST_ENDPOINT}/global_units?select=*&version=gt.$version"
            ) {
                header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                header("Authorization", "Bearer ${authService.getAccessToken() ?: SupabaseConfig.SUPABASE_ANON_KEY}")
            }
            val units: List<GlobalUnitDto> = response.body()
            Result.success(units)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the maximum version number from global units.
     */
    suspend fun getMaxGlobalVersion(): Result<Int> {
        return try {
            val response = httpClient.get(
                "${SupabaseConfig.REST_ENDPOINT}/global_units?select=version&order=version.desc&limit=1"
            ) {
                header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                header("Authorization", "Bearer ${authService.getAccessToken() ?: SupabaseConfig.SUPABASE_ANON_KEY}")
            }
            val units: List<GlobalUnitDto> = response.body()
            Result.success(units.firstOrNull()?.version ?: 0)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Publish a unit to the global_units table (superadmin only).
     * RLS on Supabase enforces that only superadmin can INSERT/UPDATE.
     */
    suspend fun publishUnit(unit: GlobalUnitDto): Result<Unit> {
        return try {
            if (authService.getCurrentRole() != "superadmin") {
                return Result.failure(SecurityException("Only superadmin can publish global units"))
            }

            httpClient.post("${SupabaseConfig.REST_ENDPOINT}/global_units") {
                contentType(ContentType.Application.Json)
                header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                header("Authorization", "Bearer ${authService.getAccessToken()}")
                header("Prefer", "resolution=merge-duplicates")
                setBody(unit)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
