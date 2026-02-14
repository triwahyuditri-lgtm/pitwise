package com.example.pitwise.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding")

@Singleton
class OnboardingPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val IS_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")

    val isFirstLaunch: Flow<Boolean> = context.onboardingDataStore.data.map { prefs ->
        prefs[IS_FIRST_LAUNCH] ?: true
    }

    suspend fun setOnboardingComplete() {
        context.onboardingDataStore.edit { prefs ->
            prefs[IS_FIRST_LAUNCH] = false
        }
    }

    suspend fun resetOnboarding() {
        context.onboardingDataStore.edit { prefs ->
            prefs[IS_FIRST_LAUNCH] = true
        }
    }
}
