package com.example.pitwise.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pitwise.data.local.entity.UserSession
import kotlinx.coroutines.flow.Flow

@Dao
interface UserSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: UserSession): Long

    @Query("SELECT * FROM user_session ORDER BY last_login DESC LIMIT 1")
    fun getCurrentSession(): Flow<UserSession?>

    @Query("SELECT * FROM user_session ORDER BY last_login DESC LIMIT 1")
    suspend fun getCurrentSessionSync(): UserSession?

    @Query("DELETE FROM user_session")
    suspend fun clearSessions()
}
