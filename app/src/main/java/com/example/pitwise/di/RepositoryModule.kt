package com.example.pitwise.di

import com.example.pitwise.data.repository.UnitRepositoryImpl
import com.example.pitwise.data.repository.UserRepositoryImpl
import com.example.pitwise.domain.repository.UnitRepository
import com.example.pitwise.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindUnitRepository(impl: UnitRepositoryImpl): UnitRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository
}
