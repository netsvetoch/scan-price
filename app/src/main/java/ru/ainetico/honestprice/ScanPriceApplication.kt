package ru.ainetico.honestprice

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.ainetico.honestprice.model.ModelDownloader
import ru.ainetico.honestprice.ocr.LocalVisionEngine
import javax.inject.Inject

@HiltAndroidApp
class ScanPriceApplication : Application() {

    @Inject lateinit var localVisionEngine: LocalVisionEngine
    @Inject lateinit var modelDownloader: ModelDownloader

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            if (modelDownloader.isModelDownloaded()) {
                Log.i("ScanPriceApplication", "Models already present, initializing vision engine...")
                localVisionEngine.initialize()
                Log.i("ScanPriceApplication", "Vision engine ready: ${localVisionEngine.isAvailable()}")
            } else {
                modelDownloader.state.first { it is ModelDownloader.DownloadState.Completed }
                Log.i("ScanPriceApplication", "Download completed, initializing vision engine...")
                localVisionEngine.initialize()
                Log.i("ScanPriceApplication", "Vision engine ready: ${localVisionEngine.isAvailable()}")
            }
        }
    }
}
