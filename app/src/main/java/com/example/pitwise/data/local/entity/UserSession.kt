package com.example.pitwise.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_session")
data class UserSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "user_id")
    val userId: String? = null,

    @ColumnInfo(name = "full_name")
    val fullName: String? = null,

    @ColumnInfo(name = "role")
    val role: String = "GUEST", // "GUEST", "USER", "SUPERADMIN"

    @ColumnInfo(name = "last_login")
    val lastLogin: Long = System.currentTimeMillis()
)

