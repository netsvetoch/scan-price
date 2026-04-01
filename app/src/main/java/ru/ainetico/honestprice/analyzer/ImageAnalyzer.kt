package ru.ainetico.honestprice.analyzer

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import ru.ainetico.honestprice.calculator.PriceCalculator
import ru.ainetico.honestprice.data.AppSettings
import ru.ainetico.honestprice.model.AnalysisResult
import ru.ainetico.honestprice.model.ParsedPriceTag
import ru.ainetico.honestprice.ocr.LocalVisionEngine
import ru.ainetico.honestprice.ocr.RemoteVisionClient

sealed class AnalysisMode {
    object Local : AnalysisMode()
    object Remote : AnalysisMode()
}

/**
 * Exception thrown when remote API fails — UI can offer "Process locally" button.
 */
class RemoteAnalysisException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ImageAnalyzer(
    private val localEngine: LocalVisionEngine,
    private val calculator: PriceCalculator,
    private val appSettings: AppSettings
) {
    private val remoteClient = RemoteVisionClient()

    /**
     * Analyze bitmap. If remote model configured — try it first.
     * @param forceLocal — skip remote, use local directly
     */
    suspend fun analyze(bitmap: Bitmap, cropRect: Rect?, forceLocal: Boolean = false): AnalysisResult {
        val useRemote = !forceLocal && appSettings.isRemoteModelConfigured()

        val tag: ParsedPriceTag = if (useRemote) {
            try {
                Log.d("ImageAnalyzer", "Using remote model...")
                remoteClient.analyze(bitmap, appSettings.apiUrl.value, appSettings.apiKey.value, appSettings.apiModel.value)
            } catch (e: Exception) {
                Log.e("ImageAnalyzer", "Remote failed: ${e.message}")
                throw RemoteAnalysisException("Ошибка при подключении к серверу", e)
            }
        } else {
            Log.d("ImageAnalyzer", "Using local model...")
            localEngine.analyze(bitmap)
        }

        val price = calculator.calculate(tag)
        return AnalysisResult(tag = tag, price = price)
    }
}
