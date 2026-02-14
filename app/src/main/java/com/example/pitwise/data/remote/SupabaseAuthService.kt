package com.example.pitwise.data.remote

import com.example.pitwise.data.remote.dto.AuthRequest
import com.example.pitwise.data.remote.dto.AuthResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles Supabase Auth operations: sign-up, sign-in, sign-out.
 * Uses Supabase GoTrue REST API directly via Ktor.
 */
@Singleton
class SupabaseAuthService @Inject constructor(
    private val httpClient: HttpClient
) {
    private var currentToken: String? = null
    private var currentUserId: String? = null
    private var currentRole: String = "user"

    /**
     * Sign up a new user with email and password.
     */
    suspend fun signUp(email: String, password: String, data: Map<String, String>? = null): Result<AuthResponse> {
        return try {
            val response = httpClient.post("${SupabaseConfig.AUTH_ENDPOINT}/signup") {
                contentType(ContentType.Application.Json)
                header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                setBody(AuthRequest(email, password, data))
            }
            val authResponse: AuthResponse = response.body()
            handleAuthSuccess(authResponse)
            Result.success(authResponse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sign in with email and password.
     */
    suspend fun signIn(email: String, password: String): Result<AuthResponse> {
        return try {
            val response = httpClient.post("${SupabaseConfig.AUTH_ENDPOINT}/token?grant_type=password") {
                contentType(ContentType.Application.Json)
                header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                setBody(AuthRequest(email, password))
            }
            val authResponse: AuthResponse = response.body()
            handleAuthSuccess(authResponse)
            Result.success(authResponse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sign out the current user.
     */
    fun signOut() {
        currentToken = null
        currentUserId = null
        currentRole = "user"
    }

    fun getAccessToken(): String? = currentToken
    fun getCurrentUserId(): String? = currentUserId
    fun getCurrentRole(): String = currentRole
    fun isAuthenticated(): Boolean = currentToken != null

    private fun handleAuthSuccess(response: AuthResponse) {
        currentToken = response.accessToken
        currentUserId = response.user?.id
        currentRole = response.user?.userMetadata?.role ?: "user"
    }
}
