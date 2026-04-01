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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.ainetico.honestprice.FrameConfig
import ru.ainetico.honestprice.R
import ru.ainetico.honestprice.analyzer.ImageAnalyzer
import ru.ainetico.honestprice.analyzer.RemoteAnalysisException
import ru.ainetico.honestprice.data.ScanRepository
import java.io.File
import java.io.FileOutputStream

sealed class CameraState {
  object Preview : CameraState()
  data class Adjusting(val bitmap: Bitmap) : CameraState()  // gallery image — user aligns price tag
  data class Scanning(
    val displayBitmap: Bitmap?,  // null = skeleton
    val status: String = ""
  ) : CameraState()

  data class Error(val message: String, val previewBitmap: Bitmap) : CameraState()
  data class RemoteError(val message: String, val croppedBitmap: Bitmap) : CameraState()
}

sealed class CameraEvent {
  data class NavigateToResult(
    val scanId: Long,
    val result: ru.ainetico.honestprice.model.AnalysisResult
  ) : CameraEvent()

  object NavigateToManualEntry : CameraEvent()
}

@dagger.hilt.android.lifecycle.HiltViewModel
class CameraViewModel @javax.inject.Inject constructor(
  private val imageAnalyzer: ImageAnalyzer,
  private val scanRepository: ScanRepository,
  @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: Context
) : ViewModel() {

  private val _state = MutableStateFlow<CameraState>(CameraState.Preview)
  val state: StateFlow<CameraState> = _state

  private val _event = Channel<CameraEvent>(Channel.BUFFERED)
  val event = _event.receiveAsFlow()

  private val _isVerticalFrame = MutableStateFlow(false)
  val isVerticalFrame: StateFlow<Boolean> = _isVerticalFrame

  fun toggleFrameOrientation() {
    _isVerticalFrame.value = !_isVerticalFrame.value
  }

  private val currentAspectRatio: Float
    get() = if (_isVerticalFrame.value) 1f / FrameConfig.ASPECT_RATIO else FrameConfig.ASPECT_RATIO

  private var scanningJob: Job? = null
  private var lastScanId: Long? = null
  private var lastImagePath: String? = null

  companion object {
    private const val SCAN_TIMEOUT_MS = 120_000L  // 2 minutes for on-device inference
  }

  fun capture(bitmap: Bitmap, cropRect: Rect?) {
    lastBitmapForRetry = bitmap
    _state.value = CameraState.Scanning(null, appContext.getString(R.string.camera_cropping))
    scanningJob = viewModelScope.launch {
      try {
        val (scanId, result) = kotlinx.coroutines.withTimeout(SCAN_TIMEOUT_MS) {
          processImage(bitmap, cropRect)
        }
        _event.trySend(CameraEvent.NavigateToResult(scanId, result))
      } catch (e: RemoteAnalysisException) {
        Log.e("CameraViewModel", "Remote failed", e)
        val cropped = withContext(Dispatchers.Default) { cropToFrame(bitmap) }
        _state.value = CameraState.RemoteError(e.message ?: appContext.getString(R.string.camera_error_server), cropped)
      } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        Log.e("CameraViewModel", "Scan timed out", e)
        _state.value = CameraState.Error(appContext.getString(R.string.camera_error_timeout), bitmap)
      } catch (e: Exception) {
        Log.e("CameraViewModel", "Processing failed", e)
        _state.value = CameraState.Error(appContext.getString(R.string.camera_error_recognition, e.message), bitmap)
      }
    }
  }

  /** Retry last scan using local model after remote failure */
  private var lastBitmapForRetry: Bitmap? = null

  fun retryWithLocal() {
    val bitmap = lastBitmapForRetry ?: return
    _state.value = CameraState.Scanning(null, appContext.getString(R.string.camera_cropping))
    scanningJob = viewModelScope.launch {
      try {
        val (scanId, result) = kotlinx.coroutines.withTimeout(SCAN_TIMEOUT_MS) {
          processImageForceLocal(bitmap)
        }
        _event.trySend(CameraEvent.NavigateToResult(scanId, result))
      } catch (e: Exception) {
        Log.e("CameraViewModel", "Local retry failed", e)
        _state.value = CameraState.Error(appContext.getString(R.string.camera_error_generic, e.message), bitmap)
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
        _state.value = CameraState.Adjusting(bitmap)
      } catch (e: Exception) {
        Log.e("CameraViewModel", "Gallery import failed", e)
        _state.value = CameraState.Preview
      }
    }
  }

  /**
   * Called after user aligns the image.
   * @param viewWidth/viewHeight — size of the composable displaying the image
   * @param offsetX/offsetY — drag offset in screen pixels
   * @param zoom — pinch zoom factor
   */
  fun confirmAdjustment(
    bitmap: Bitmap,
    viewWidth: Float,
    viewHeight: Float,
    offsetX: Float,
    offsetY: Float,
    zoom: Float
  ) {
    _state.value = CameraState.Scanning(null, appContext.getString(R.string.camera_cropping))
    scanningJob = viewModelScope.launch {
      try {
        val (scanId, result) = kotlinx.coroutines.withTimeout(SCAN_TIMEOUT_MS) {
          processImageWithAlignment(bitmap, viewWidth, viewHeight, offsetX, offsetY, zoom)
        }
        _event.trySend(CameraEvent.NavigateToResult(scanId, result))
      } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        Log.e("CameraViewModel", "Scan timed out", e)
        _state.value = CameraState.Error(appContext.getString(R.string.camera_error_timeout_short), bitmap)
      } catch (e: Exception) {
        Log.e("CameraViewModel", "Processing failed", e)
        _state.value = CameraState.Error(appContext.getString(R.string.camera_error_generic, e.message), bitmap)
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
    _event.trySend(CameraEvent.NavigateToManualEntry)
  }


  fun resetToPreview() {
    _state.value = CameraState.Preview
  }

  fun dismissError() {
    _state.value = CameraState.Preview
  }

  private suspend fun saveBitmapToFile(bitmap: Bitmap, timestamp: Long): String {
    return withContext(Dispatchers.IO) {
      val imagesDir = File(appContext.filesDir, "images").apply { mkdirs() }
      val path = File(imagesDir, "scan_${timestamp}.jpg").absolutePath
      FileOutputStream(path).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
      }
      path
    }
  }

  private suspend fun processImage(
    bitmap: Bitmap,
    cropRect: Rect?
  ): Pair<Long, ru.ainetico.honestprice.model.AnalysisResult> {
    val timestamp = System.currentTimeMillis()

    // Step 1: Crop
    val cropped = withContext(Dispatchers.Default) { cropToFrame(bitmap) }
    _state.value = CameraState.Scanning(cropped, appContext.getString(R.string.camera_compressing))

    // Step 2: Save
    val imagePath = saveBitmapToFile(cropped, timestamp)
    lastImagePath = imagePath
    _state.value = CameraState.Scanning(cropped, appContext.getString(R.string.camera_analyzing))

    // Step 3: Analyze
    val scanId = withContext(Dispatchers.IO) { scanRepository.createProcessing(imagePath) }
    lastScanId = scanId
    val analysisResult = imageAnalyzer.analyze(bitmap, cropRect)

    return Pair(scanId, analysisResult)
  }

  private suspend fun processImageForceLocal(
    bitmap: Bitmap
  ): Pair<Long, ru.ainetico.honestprice.model.AnalysisResult> {
    val timestamp = System.currentTimeMillis()
    val cropped = withContext(Dispatchers.Default) { cropToFrame(bitmap) }
    _state.value = CameraState.Scanning(cropped, appContext.getString(R.string.camera_compressing))

    val imagePath = saveBitmapToFile(cropped, timestamp)
    lastImagePath = imagePath
    _state.value = CameraState.Scanning(cropped, appContext.getString(R.string.camera_analyzing_local))

    val scanId = withContext(Dispatchers.IO) { scanRepository.createProcessing(imagePath) }
    lastScanId = scanId
    val analysisResult = imageAnalyzer.analyze(bitmap, null, forceLocal = true)
    return Pair(scanId, analysisResult)
  }

  private suspend fun processImageWithAlignment(
    bitmap: Bitmap, viewWidth: Float, viewHeight: Float,
    offsetX: Float, offsetY: Float, zoom: Float
  ): Pair<Long, ru.ainetico.honestprice.model.AnalysisResult> {
    val timestamp = System.currentTimeMillis()

    val cropped = withContext(Dispatchers.Default) {
      cropAligned(bitmap, viewWidth, viewHeight, offsetX, offsetY, zoom)
    }
    _state.value = CameraState.Scanning(cropped, appContext.getString(R.string.camera_compressing))

    val imagePath = saveBitmapToFile(cropped, timestamp)
    lastImagePath = imagePath
    _state.value = CameraState.Scanning(cropped, appContext.getString(R.string.camera_analyzing))

    val scanId = withContext(Dispatchers.IO) { scanRepository.createProcessing(imagePath) }
    lastScanId = scanId
    val analysisResult = imageAnalyzer.analyze(cropped, null)

    return Pair(scanId, analysisResult)
  }

  /**
   * Crop the area visible inside the frame, accounting for ContentScale.FillWidth + pan + zoom.
   *
   * ContentScale.FillWidth scales bitmap to match view width, preserving aspect ratio.
   * The user then applies pan (offset) and zoom on top.
   * We need to find which bitmap pixels are inside the frame rectangle.
   */
  private fun cropAligned(
    bitmap: Bitmap, viewW: Float, viewH: Float,
    panX: Float, panY: Float, zoom: Float
  ): Bitmap {
    // Step 1: ContentScale.FillWidth — scale to match view width
    val scaleToFill = viewW / bitmap.width
    // Effective scale after user zoom
    val totalScale = scaleToFill * zoom

    // Step 2: Image center in view coordinates (before pan)
    val imgCenterX = viewW / 2f
    val imgCenterY = viewH / 2f

    // Step 3: Frame rectangle in view coordinates
    val baseW = viewW * FrameConfig.WIDTH_FRACTION
    val baseH = baseW / FrameConfig.ASPECT_RATIO
    val frameW = if (currentAspectRatio >= 1f) baseW else baseH
    val frameH = if (currentAspectRatio >= 1f) baseH else baseW
    val density = appContext.resources.displayMetrics.density
    val frameLeft = (viewW - frameW) / 2f
    val frameTop = FrameConfig.frameTop(viewH, frameH, density)

    // Step 4: Convert frame corners to bitmap coordinates
    // View point → bitmap: bmpX = (viewX - imgCenterX - panX) / totalScale + bmpWidth/2
    fun viewToBmpX(vx: Float) = ((vx - imgCenterX - panX) / totalScale + bitmap.width / 2f)
    fun viewToBmpY(vy: Float) = ((vy - imgCenterY - panY) / totalScale + bitmap.height / 2f)

    val bmpLeft = viewToBmpX(frameLeft).toInt().coerceIn(0, bitmap.width - 1)
    val bmpTop = viewToBmpY(frameTop).toInt().coerceIn(0, bitmap.height - 1)
    val bmpRight = viewToBmpX(frameLeft + frameW).toInt().coerceIn(bmpLeft + 1, bitmap.width)
    val bmpBottom = viewToBmpY(frameTop + frameH).toInt().coerceIn(bmpTop + 1, bitmap.height)

    val cropW = bmpRight - bmpLeft
    val cropH = bmpBottom - bmpTop

    Log.d(
      "CameraViewModel",
      "cropAligned: view=${viewW}x${viewH} pan=$panX,$panY zoom=$zoom → bmp crop ($bmpLeft,$bmpTop ${cropW}x${cropH})"
    )

    return Bitmap.createBitmap(bitmap, bmpLeft, bmpTop, cropW, cropH)
  }

  private fun cropToFrame(bitmap: Bitmap): Bitmap {
    val baseW = (bitmap.width * FrameConfig.WIDTH_FRACTION).toInt()
    val baseH = (baseW / FrameConfig.ASPECT_RATIO).toInt()
    val frameWidth = if (currentAspectRatio >= 1f) baseW else baseH
    val frameHeight = if (currentAspectRatio >= 1f) baseH else baseW
    val density = appContext.resources.displayMetrics.density
    val left = (bitmap.width - frameWidth) / 2
    val top = FrameConfig.frameTop(bitmap.height.toFloat(), frameHeight.toFloat(), density).toInt().coerceAtLeast(0)

    if (frameWidth <= 0 || frameHeight <= 0 || left + frameWidth > bitmap.width || top + frameHeight > bitmap.height) {
      return bitmap
    }
    return Bitmap.createBitmap(bitmap, left, top, frameWidth, frameHeight)
  }
}
