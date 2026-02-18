package com.example.pitwise.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.pitwise.data.local.dao.BaseUnitVersionDao
import com.example.pitwise.data.local.dao.EquipmentDao
import com.example.pitwise.data.local.dao.UnitBrandDao
import com.example.pitwise.data.local.dao.UnitModelDao
import com.example.pitwise.data.local.dao.UnitSpecDao
import com.example.pitwise.data.local.dao.MapDao
import com.example.pitwise.data.local.dao.UserSessionDao
import com.example.pitwise.data.local.entity.BaseUnitVersion
import com.example.pitwise.data.local.entity.EquipmentDozer
import com.example.pitwise.data.local.entity.EquipmentHauler
import com.example.pitwise.data.local.entity.EquipmentLoader
import com.example.pitwise.data.local.entity.MaterialProperty
import com.example.pitwise.data.local.entity.SyncMetadata
import com.example.pitwise.data.local.entity.UnitBrand
import com.example.pitwise.data.local.entity.UnitModel
import com.example.pitwise.data.local.entity.UnitSpec
import com.example.pitwise.data.local.entity.MapAnnotation
import com.example.pitwise.data.local.entity.MapEntry
import com.example.pitwise.data.local.entity.UserSession

@Database(
    entities = [
        UnitBrand::class,
        UnitModel::class,
        UnitSpec::class,
        UserSession::class,
        BaseUnitVersion::class,
        MapEntry::class,
        MapAnnotation::class,
        EquipmentLoader::class,
        EquipmentHauler::class,
        EquipmentDozer::class,
        MaterialProperty::class,
        SyncMetadata::class
    ],
    version = 9,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class PitwiseDatabase : RoomDatabase() {
    abstract fun unitBrandDao(): UnitBrandDao
    abstract fun unitModelDao(): UnitModelDao
    abstract fun unitSpecDao(): UnitSpecDao
    abstract fun userSessionDao(): UserSessionDao
    abstract fun baseUnitVersionDao(): BaseUnitVersionDao
    abstract fun mapDao(): MapDao
    abstract fun equipmentDao(): EquipmentDao
}

