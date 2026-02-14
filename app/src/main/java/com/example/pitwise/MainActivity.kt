package com.example.pitwise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.example.pitwise.data.local.OnboardingPreferences
import com.example.pitwise.domain.sync.GlobalUnitSyncManager
import com.example.pitwise.ui.navigation.PitwiseNavGraph
import com.example.pitwise.ui.theme.PitwiseTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var syncManager: GlobalUnitSyncManager

    @Inject
    lateinit var onboardingPreferences: OnboardingPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Non-blocking background sync on app launch
        CoroutineScope(Dispatchers.IO).launch {
            syncManager.syncIfNeeded()
        }

        setContent {
            PitwiseTheme {
                val navController = rememberNavController()
                PitwiseNavGraph(
                    navController = navController,
                    onboardingPreferences = onboardingPreferences
                )
            }
        }
    }
}