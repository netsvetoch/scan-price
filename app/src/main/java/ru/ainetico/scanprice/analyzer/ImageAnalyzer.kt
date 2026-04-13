package ru.ainetico.scanprice.analyzer

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.flow.first
import ru.ainetico.scanprice.calculator.PriceCalculator
import ru.ainetico.scanprice.data.AppSettings
import ru.ainetico.scanprice.model.AnalysisResult
import ru.ainetico.scanprice.ocr.VisionEngine
import ru.ainetico.scanprice.ocr.VisionResult

/**
 * Exception thrown when remote API fails — UI can offer "Process locally" button.
 */
class RemoteAnalysisException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when local engine fails.
 */
class LocalAnalysisException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ImageAnalyzer(
  private val localEngine: VisionEngine,
  private val calculator: PriceCalculator,
  private val appSettings: AppSettings,
  private val remoteClient: VisionEngine
) {

  /**
   * Analyze bitmap. If remote model configured — try it first.
   * @param forceLocal — skip remote, use local directly
   */
  suspend fun analyze(
    bitmap: Bitmap,
    forceLocal: Boolean = false
  ): AnalysisResult {
    val useRemote = !forceLocal && appSettings.isRemoteModelConfigured()

    val engine: VisionEngine
    val promptFlow: String
    if (useRemote) {
      Log.d("ImageAnalyzer", "Using remote model...")
      engine = remoteClient
      promptFlow = appSettings.systemPrompt.first()
    } else {
      Log.d("ImageAnalyzer", "Using local model...")
      engine = localEngine
      promptFlow = appSettings.localPrompt.first()
    }
    val prompt = promptFlow.ifBlank { engine.defaultPrompt }

    val tag = when (val result = engine.analyze(bitmap, prompt)) {
      is VisionResult.Success -> result.tag
      is VisionResult.Error -> {
        if (useRemote) {
          Log.e("ImageAnalyzer", "Remote failed: ${result.message}")
          throw RemoteAnalysisException(result.message, result.cause)
        } else {
          Log.e("ImageAnalyzer", "Local failed: ${result.message}")
          throw LocalAnalysisException(result.message, result.cause)
        }
      }
    }

    val price = calculator.calculate(tag)
    return AnalysisResult(tag = tag, price = price)
  }
}
