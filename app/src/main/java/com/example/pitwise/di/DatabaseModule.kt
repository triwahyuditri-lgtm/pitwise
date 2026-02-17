package com.example.pitwise.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.pitwise.data.local.PitwiseDatabase
import com.example.pitwise.data.local.dao.BaseUnitVersionDao
import com.example.pitwise.data.local.dao.EquipmentDao
import com.example.pitwise.data.local.dao.MapDao
import com.example.pitwise.data.local.dao.UnitBrandDao
import com.example.pitwise.data.local.dao.UnitModelDao
import com.example.pitwise.data.local.dao.UnitSpecDao
import com.example.pitwise.data.local.dao.UserSessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Volatile
    private var INSTANCE: PitwiseDatabase? = null

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): PitwiseDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                PitwiseDatabase::class.java,
                "pitwise_database"
            )
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
        }
    }

    @Provides
    fun provideUnitBrandDao(database: PitwiseDatabase): UnitBrandDao =
        database.unitBrandDao()

    @Provides
    fun provideUnitModelDao(database: PitwiseDatabase): UnitModelDao =
        database.unitModelDao()

    @Provides
    fun provideUnitSpecDao(database: PitwiseDatabase): UnitSpecDao =
        database.unitSpecDao()

    @Provides
    fun provideUserSessionDao(database: PitwiseDatabase): UserSessionDao =
        database.userSessionDao()

    @Provides
    fun provideBaseUnitVersionDao(database: PitwiseDatabase): BaseUnitVersionDao =
        database.baseUnitVersionDao()

    @Provides
    fun provideMapDao(database: PitwiseDatabase): MapDao =
        database.mapDao()

    @Provides
    fun provideEquipmentDao(database: PitwiseDatabase): EquipmentDao =
        database.equipmentDao()
}

