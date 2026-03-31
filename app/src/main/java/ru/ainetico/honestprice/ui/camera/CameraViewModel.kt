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
}

sealed class CameraEvent {
    data class NavigateToResult(val scanId: Long) : CameraEvent()
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

    fun capture(bitmap: Bitmap, cropRect: Rect?) {
        _state.value = CameraState.Scanning(bitmap)
        scanningJob = viewModelScope.launch {
            try {
                val scanId = processImage(bitmap, cropRect)
                _event.value = CameraEvent.NavigateToResult(scanId)
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Processing failed", e)
                _state.value = CameraState.Preview
            }
        }
    }

    fun importFromGallery(uri: Uri, context: Context) {
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                _state.value = CameraState.Scanning(bitmap)
                val scanId = processImage(bitmap, cropRect = null)
                _event.value = CameraEvent.NavigateToResult(scanId)
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Gallery import failed", e)
                _state.value = CameraState.Preview
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

    private suspend fun processImage(bitmap: Bitmap, cropRect: Rect?): Long {
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
            scanRepository.markCompleted(scanId, analysisResult.tag, analysisResult.price)

            val thumbDir = File(appContext.filesDir, "images/thumbnails").apply { mkdirs() }
            val thumbPath = File(thumbDir, "thumb_${scanId}_${timestamp}.jpg").absolutePath
            val thumbWidth = 200
            val scale = thumbWidth.toFloat() / bitmap.width
            val thumb = Bitmap.createScaledBitmap(bitmap, thumbWidth, (bitmap.height * scale).toInt(), true)
            FileOutputStream(thumbPath).use { out ->
                thumb.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }

            scanId
        }
    }
}
