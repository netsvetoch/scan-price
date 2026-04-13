package ru.ainetico.honestprice.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
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
import ru.ainetico.honestprice.R
import ru.ainetico.honestprice.analyzer.ImageAnalyzer
import ru.ainetico.honestprice.analyzer.RemoteAnalysisException
import ru.ainetico.honestprice.data.ScanRepository
import java.io.File
import java.io.FileOutputStream

@androidx.compose.runtime.Stable
sealed class CameraState {
  object Preview : CameraState()
  data class Adjusting(val bitmap: Bitmap) :
    CameraState()  // gallery image — user aligns price tag

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
          processImage(
            cropOp = {
              withContext(Dispatchers.Default) {
                ImageCropper.cropToFrame(
                  bitmap,
                  appContext.resources.displayMetrics.density,
                  _isVerticalFrame.value
                )
              }
            },
            analyzeBitmap = bitmap,
            cropRect = cropRect
          )
        }
        _event.trySend(CameraEvent.NavigateToResult(scanId, result))
      } catch (e: RemoteAnalysisException) {
        Log.e("CameraViewModel", "Remote failed", e)
        val cropped = withContext(Dispatchers.Default) {
          ImageCropper.cropToFrame(
            bitmap,
            appContext.resources.displayMetrics.density,
            _isVerticalFrame.value
          )
        }
        _state.value = CameraState.RemoteError(
          e.message ?: appContext.getString(R.string.camera_error_server), cropped
        )
      } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        Log.e("CameraViewModel", "Scan timed out", e)
        _state.value =
          CameraState.Error(appContext.getString(R.string.camera_error_timeout), bitmap)
      } catch (e: Exception) {
        Log.e("CameraViewModel", "Processing failed", e)
        _state.value = CameraState.Error(
          appContext.getString(
            R.string.camera_error_recognition,
            e.message
          ), bitmap
        )
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
          processImage(
            cropOp = {
              withContext(Dispatchers.Default) {
                ImageCropper.cropToFrame(
                  bitmap,
                  appContext.resources.displayMetrics.density,
                  _isVerticalFrame.value
                )
              }
            },
            analyzeBitmap = bitmap,
            forceLocal = true,
            analyzingStatus = R.string.camera_analyzing_local
          )
        }
        _event.trySend(CameraEvent.NavigateToResult(scanId, result))
      } catch (e: Exception) {
        Log.e("CameraViewModel", "Local retry failed", e)
        _state.value = CameraState.Error(
          appContext.getString(
            R.string.camera_error_generic,
            e.message
          ), bitmap
        )
      }
    }
  }

  fun importFromGallery(uri: Uri, context: Context) {
    viewModelScope.launch {
      try {
        val bitmap = withContext(Dispatchers.IO) {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
              decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
          } else {
            context.contentResolver.openInputStream(uri)?.use { stream ->
              BitmapFactory.decodeStream(stream)
            } ?: throw IllegalStateException("Cannot open URI: $uri")
          }
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
          processImage(
            cropOp = {
              withContext(Dispatchers.Default) {
                ImageCropper.cropAligned(
                  bitmap,
                  viewWidth,
                  viewHeight,
                  offsetX,
                  offsetY,
                  zoom,
                  appContext.resources.displayMetrics.density,
                  _isVerticalFrame.value
                )
              }
            }
          )
        }
        _event.trySend(CameraEvent.NavigateToResult(scanId, result))
      } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        Log.e("CameraViewModel", "Scan timed out", e)
        _state.value = CameraState.Error(
          appContext.getString(R.string.camera_error_timeout_short),
          bitmap
        )
      } catch (e: Exception) {
        Log.e("CameraViewModel", "Processing failed", e)
        _state.value = CameraState.Error(
          appContext.getString(
            R.string.camera_error_generic,
            e.message
          ), bitmap
        )
      }
    }
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
    cropOp: suspend () -> Bitmap,
    analyzeBitmap: Bitmap? = null,
    cropRect: Rect? = null,
    forceLocal: Boolean = false,
    analyzingStatus: Int = R.string.camera_analyzing
  ): Pair<Long, ru.ainetico.honestprice.model.AnalysisResult> {
    val timestamp = System.currentTimeMillis()

    val cropped = cropOp()
    _state.value =
      CameraState.Scanning(cropped, appContext.getString(R.string.camera_compressing))

    val imagePath = saveBitmapToFile(cropped, timestamp)
    lastImagePath = imagePath
    _state.value = CameraState.Scanning(cropped, appContext.getString(analyzingStatus))

    val scanId = withContext(Dispatchers.IO) { scanRepository.createProcessing(imagePath) }
    lastScanId = scanId
    val analysisResult = imageAnalyzer.analyze(analyzeBitmap ?: cropped, forceLocal)

    return Pair(scanId, analysisResult)
  }

}
