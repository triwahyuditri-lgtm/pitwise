package com.example.pitwise

import android.app.Application
import android.util.Log
import com.example.pitwise.data.importer.BaseDataImporter
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PitwiseApplication : Application() {

    @Inject
    lateinit var baseDataImporter: BaseDataImporter

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        triggerBaseDataImport()
    }

    /**
     * Auto-import base data on first launch or when a new version is available.
     * Runs on IO dispatcher to avoid blocking the main thread.
     */
    private fun triggerBaseDataImport() {
        applicationScope.launch {
            try {
                if (baseDataImporter.isImportNeeded()) {
                    Log.i("PitwiseApp", "Starting base data import...")
                    val result = baseDataImporter.importBaseData(applicationContext)
                    Log.i("PitwiseApp", "Base data import complete: ${result.summary}")
                } else {
                    Log.i("PitwiseApp", "Base data import not needed")
                }
            } catch (e: Throwable) {
                Log.e("PitwiseApp", "Base data import failed", e)
            }
        }
    }
}

