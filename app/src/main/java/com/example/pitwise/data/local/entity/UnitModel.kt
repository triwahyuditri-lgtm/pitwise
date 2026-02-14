package com.example.pitwise.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "unit_model",
    foreignKeys = [
        ForeignKey(
            entity = UnitBrand::class,
            parentColumns = ["id"],
            childColumns = ["brand_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("brand_id")]
)
data class UnitModel(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "brand_id")
    val brandId: Long,

    @ColumnInfo(name = "model_name")
    val modelName: String,

    @ColumnInfo(name = "category")
    val category: String, // "EXCA" or "DT"

    @ColumnInfo(name = "source")
    val source: String = "SYSTEM", // "SYSTEM", "GLOBAL", "LOCAL_OVERRIDE"

    @ColumnInfo(name = "version")
    val version: Int = 1,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long = System.currentTimeMillis()
)
