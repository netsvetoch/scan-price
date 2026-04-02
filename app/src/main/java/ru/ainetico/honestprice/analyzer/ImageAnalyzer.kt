package ru.ainetico.honestprice.analyzer

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import kotlinx.coroutines.flow.first
import ru.ainetico.honestprice.calculator.PriceCalculator
import ru.ainetico.honestprice.data.AppSettings
import ru.ainetico.honestprice.model.AnalysisResult
import ru.ainetico.honestprice.ocr.LocalVisionEngine
import ru.ainetico.honestprice.ocr.RemoteVisionClient
import ru.ainetico.honestprice.ocr.VisionEngine
import ru.ainetico.honestprice.ocr.VisionResult

/**
 * Exception thrown when remote API fails — UI can offer "Process locally" button.
 */
class RemoteAnalysisException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when local engine fails.
 */
class LocalAnalysisException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ImageAnalyzer(
    private val localEngine: LocalVisionEngine,
    private val calculator: PriceCalculator,
    private val appSettings: AppSettings,
    private val remoteClient: RemoteVisionClient
) {

    /**
     * Analyze bitmap. If remote model configured — try it first.
     * @param forceLocal — skip remote, use local directly
     */
    suspend fun analyze(bitmap: Bitmap, cropRect: Rect?, forceLocal: Boolean = false): AnalysisResult {
        val useRemote = !forceLocal && appSettings.isRemoteModelConfigured()

        val engine: VisionEngine
        val prompt: String
        if (useRemote) {
            Log.d("ImageAnalyzer", "Using remote model...")
            engine = remoteClient
            prompt = appSettings.systemPrompt.first().ifBlank { RemoteVisionClient.DEFAULT_SYSTEM_PROMPT }
        } else {
            Log.d("ImageAnalyzer", "Using local model...")
            engine = localEngine
            prompt = appSettings.localPrompt.first().ifBlank { LocalVisionEngine.DEFAULT_PROMPT }
        }

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
