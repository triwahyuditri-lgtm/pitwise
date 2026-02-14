package com.example.pitwise.data.remote

import com.example.pitwise.data.remote.dto.DozerDto
import com.example.pitwise.data.remote.dto.HaulerDto
import com.example.pitwise.data.remote.dto.LoaderDto
import com.example.pitwise.data.remote.dto.MaterialDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseDataService @Inject constructor(
    private val httpClient: HttpClient
) {
    
    suspend fun getMaterials(): Result<List<MaterialDto>> = fetch("material_properties")

    suspend fun getLoaders(className: String? = null): Result<List<LoaderDto>> {
        val query = if (className != null) "?class=eq.$className" else ""
        return fetch("loaders$query")
    }

    suspend fun getHaulers(className: String? = null): Result<List<HaulerDto>> {
        val query = if (className != null) "?class=eq.$className" else ""
        return fetch("haulers$query")
    }

    suspend fun getDozers(className: String? = null): Result<List<DozerDto>> {
        val query = if (className != null) "?class=eq.$className" else ""
        return fetch("dozers$query")
    }

    // Generic fetch helper
    private suspend inline fun <reified T> fetch(path: String): Result<List<T>> {
        return try {
            val url = "${SupabaseConfig.REST_ENDPOINT}/$path"
            // If path already has query params check
            val finalUrl = if (path.contains("?")) url else "$url?select=*"
            
            val response = httpClient.get(finalUrl) {
                header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                header("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
            }
            Result.success(response.body())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
