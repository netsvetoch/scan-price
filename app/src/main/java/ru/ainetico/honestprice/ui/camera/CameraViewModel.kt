package ru.ainetico.honestprice.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.ainetico.honestprice.analyzer.ImageAnalyzer
import ru.ainetico.honestprice.data.ScanRepository
import java.io.File
import java.io.FileOutputStream

sealed class CameraState {
    object Preview : CameraState()
    data class Scanning(val previewBitmap: Bitmap) : CameraState()
    data class Error(val message: String, val previewBitmap: Bitmap) : CameraState()
}

sealed class CameraEvent {
    data class NavigateToResult(val scanId: Long, val result: ru.ainetico.honestprice.model.AnalysisResult) : CameraEvent()
    object NavigateToManualEntry : CameraEvent()
}

class CameraViewModel(
    private val imageAnalyzer: ImageAnalyzer,
    private val scanRepository: ScanRepository,
    private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow<CameraState>(CameraState.Preview)
    val state: StateFlow<CameraState> = _state

    private val _event = MutableStateFlow<CameraEvent?>(null)
    val event: StateFlow<CameraEvent?> = _event

    private var scanningJob: Job? = null
    private var lastScanId: Long? = null
    private var lastImagePath: String? = null

    companion object {
        private const val SCAN_TIMEOUT_MS = 120_000L  // 2 minutes for on-device inference
    }

    fun capture(bitmap: Bitmap, cropRect: Rect?) {
        _state.value = CameraState.Scanning(bitmap)
        scanningJob = viewModelScope.launch {
            try {
                val (scanId, result) = kotlinx.coroutines.withTimeout(SCAN_TIMEOUT_MS) {
                    processImage(bitmap, cropRect)
                }
                _event.value = CameraEvent.NavigateToResult(scanId, result)
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.e("CameraViewModel", "Scan timed out", e)
                _state.value = CameraState.Error("Превышено время ожидания. Попробуйте ещё раз.", bitmap)
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Processing failed", e)
                _state.value = CameraState.Error("Ошибка распознавания: ${e.message}", bitmap)
            }
        }
    }

    fun importFromGallery(uri: Uri, context: Context) {
        viewModelScope.launch {
            var bitmap: Bitmap? = null
            try {
                bitmap = withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                _state.value = CameraState.Scanning(bitmap)
                val (scanId, result) = kotlinx.coroutines.withTimeout(SCAN_TIMEOUT_MS) {
                    processImage(bitmap, cropRect = null)
                }
                _event.value = CameraEvent.NavigateToResult(scanId, result)
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.e("CameraViewModel", "Gallery scan timed out", e)
                _state.value = CameraState.Error("Превышено время ожидания. Попробуйте ещё раз.", bitmap ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Gallery import failed", e)
                _state.value = CameraState.Error("Ошибка: ${e.message}", bitmap ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
            }
        }
    }

    fun retake() {
        scanningJob?.cancel()
        // Clean up incomplete scan
        val scanId = lastScanId
        val imagePath = lastImagePath
        lastScanId = null
        lastImagePath = null
        if (scanId != null || imagePath != null) {
            viewModelScope.launch(Dispatchers.IO) {
                scanId?.let { scanRepository.delete(it) }
                imagePath?.let { File(it).delete() }
            }
        }
        _state.value = CameraState.Preview
    }

    fun onManualEntry() {
        _event.value = CameraEvent.NavigateToManualEntry
    }

    fun eventConsumed() {
        _event.value = null
    }

    fun resetToPreview() {
        _state.value = CameraState.Preview
    }

    fun dismissError() {
        _state.value = CameraState.Preview
    }

    private suspend fun processImage(bitmap: Bitmap, cropRect: Rect?): Pair<Long, ru.ainetico.honestprice.model.AnalysisResult> {
        return withContext(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis()
            val imagesDir = File(appContext.filesDir, "images/originals").apply { mkdirs() }
            val imagePath = File(imagesDir, "scan_${timestamp}.jpg").absolutePath

            FileOutputStream(imagePath).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            lastImagePath = imagePath

            val scanId = scanRepository.createProcessing(imagePath)
            lastScanId = scanId
            val analysisResult = imageAnalyzer.analyze(bitmap, cropRect)
            // Don't markCompleted here — stays PROCESSING until user explicitly saves
            // ResultViewModel.save() will call updateUserFields which sets COMPLETED
            // PROCESSING records are hidden from history list

            val thumbDir = File(appContext.filesDir, "images/thumbnails").apply { mkdirs() }
            val thumbPath = File(thumbDir, "thumb_${scanId}_${timestamp}.jpg").absolutePath
            val thumbWidth = 200
            val scale = thumbWidth.toFloat() / bitmap.width
            val thumb = Bitmap.createScaledBitmap(bitmap, thumbWidth, (bitmap.height * scale).toInt(), true)
            FileOutputStream(thumbPath).use { out ->
                thumb.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }

            Pair(scanId, analysisResult)
        }
    }
}
