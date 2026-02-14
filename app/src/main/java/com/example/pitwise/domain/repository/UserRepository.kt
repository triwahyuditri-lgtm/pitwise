package com.example.pitwise.domain.repository

import com.example.pitwise.data.local.entity.UserSession
import kotlinx.coroutines.flow.Flow

interface UserRepository {

    fun getCurrentSession(): Flow<UserSession?>
    suspend fun getCurrentSessionSync(): UserSession?

    suspend fun loginAsGuest()
    suspend fun loginWithEmail(email: String, password: String): Result<String>
    suspend fun signUp(email: String, password: String, fullName: String): Result<String>
    suspend fun logout()

    fun getRole(): String
    fun isLoggedIn(): Boolean
}
