package com.example.pitwise.data.repository

import com.example.pitwise.data.local.dao.UserSessionDao
import com.example.pitwise.data.local.entity.UserSession
import com.example.pitwise.data.remote.SupabaseAuthService
import com.example.pitwise.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val sessionDao: UserSessionDao,
    private val authService: SupabaseAuthService
) : UserRepository {

    override fun getCurrentSession(): Flow<UserSession?> =
        sessionDao.getCurrentSession()

    override suspend fun getCurrentSessionSync(): UserSession? =
        sessionDao.getCurrentSessionSync()

    override suspend fun loginAsGuest() {
        sessionDao.clearSessions()
        sessionDao.insert(
            UserSession(
                userId = null,
                role = "GUEST",
                lastLogin = System.currentTimeMillis()
            )
        )
    }

    override suspend fun loginWithEmail(email: String, password: String): Result<String> {
        val result = authService.signIn(email, password)
        return result.fold(
            onSuccess = { authResponse ->
                val supabaseRole = authService.getCurrentRole()
                val appRole = when (supabaseRole) {
                    "superadmin" -> "SUPERADMIN"
                    else -> "USER"
                }

                sessionDao.clearSessions()
                sessionDao.insert(
                    UserSession(
                        userId = authResponse.user?.id,
                        fullName = authService.getCurrentFullName(),
                        role = appRole,
                        lastLogin = System.currentTimeMillis()
                    )
                )
                Result.success(appRole)
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }

    override suspend fun signUp(email: String, password: String, fullName: String): Result<String> {
        val metadata = mapOf("full_name" to fullName)
        val result = authService.signUp(email, password, metadata)
        return result.fold(
            onSuccess = { authResponse ->
                // If Supabase has email confirmation enabled, access_token will be empty
                if (authResponse.accessToken.isBlank()) {
                    // Don't create a local session — user must confirm email first, then login
                    Result.success("CONFIRM_EMAIL")
                } else {
                    // Email confirmation is disabled — user is immediately authenticated
                    sessionDao.clearSessions()
                    sessionDao.insert(
                        UserSession(
                            userId = authResponse.user?.id,
                            fullName = fullName,
                            role = "USER",
                            lastLogin = System.currentTimeMillis()
                        )
                    )
                    Result.success("USER")
                }
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }

    override suspend fun logout() {
        authService.signOut()
        sessionDao.clearSessions()
        // Re-enter as guest after logout
        loginAsGuest()
    }

    override fun getRole(): String {
        return if (authService.isAuthenticated()) {
            when (authService.getCurrentRole()) {
                "superadmin" -> "SUPERADMIN"
                else -> "USER"
            }
        } else {
            "GUEST"
        }
    }

    override fun isLoggedIn(): Boolean = authService.isAuthenticated()
}
