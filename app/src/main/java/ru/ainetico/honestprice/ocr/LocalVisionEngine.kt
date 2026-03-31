package ru.ainetico.honestprice.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.ainetico.honestprice.model.ParsedPriceTag
import ru.ainetico.honestprice.model.WeightUnit
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigDecimal

/**
 * On-device vision inference using llama.cpp + Qwen3.5 GGUF.
 * Fully offline — no network required.
 */
class LocalVisionEngine(private val appContext: Context) {

    companion object {
        private const val TAG = "LocalVisionEngine"
        private const val MODEL_FILENAME = "Qwen3.5-0.8B-Q4_K_M.gguf"
        private const val MMPROJ_FILENAME = "mmproj-BF16.gguf"

        private const val PROMPT = "Analyze this price tag from a Russian store. " +
                "Extract data and return ONLY JSON without markdown:\n" +
                "{\"product_name\": \"name\", \"price_regular\": number_or_null, " +
                "\"price_discount\": number_or_null, \"weight_value\": number_or_null, " +
                "\"weight_unit\": \"г/кг/мл/л/шт or null\"}\n" +
                "If a field is not found, use null. Prices in rubles, numbers without currency symbol."
    }

    private var engine: InferenceEngine? = null
    private var isReady = false

    /**
     * Initialize the engine — load model + mmproj.
     * Call once at app startup. Takes a few seconds.
     */
    suspend fun initialize() {
        if (isReady) return

        try {
            val eng = AiChat.getInferenceEngine(appContext)
            engine = eng

            // Wait for native lib to initialize via StateFlow
            Log.i(TAG, "Waiting for engine to initialize...")
            eng.state.first { it is InferenceEngine.State.Initialized }
            Log.i(TAG, "Engine initialized!")

            // Find model files
            val modelsDir = File(appContext.filesDir, "models")
            val modelFile = File(modelsDir, MODEL_FILENAME)
            val mmprojFile = File(modelsDir, MMPROJ_FILENAME)

            if (!modelFile.exists() || !mmprojFile.exists()) {
                Log.e(TAG, "Model files not found in ${modelsDir.absolutePath}")
                Log.e(TAG, "  Model: ${modelFile.exists()} (${MODEL_FILENAME})")
                Log.e(TAG, "  Mmproj: ${mmprojFile.exists()} (${MMPROJ_FILENAME})")
                return
            }

            Log.i(TAG, "Loading model: ${modelFile.absolutePath}")
            eng.loadModel(modelFile.absolutePath)

            Log.i(TAG, "Loading mmproj: ${mmprojFile.absolutePath}")
            eng.loadVisionProjector(mmprojFile.absolutePath)

            isReady = true
            Log.i(TAG, "Local vision engine ready!")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize", e)
        }
    }

    fun isAvailable(): Boolean = isReady

    /**
     * Analyze a price tag image. Returns ParsedPriceTag.
     */
    suspend fun analyze(bitmap: Bitmap): ParsedPriceTag {
        val eng = engine
        if (!isReady || eng == null) {
            Log.e(TAG, "Engine not ready!")
            return ParsedPriceTag()
        }

        return withContext(Dispatchers.IO) {
            try {
                // Downscale for performance
                val resized = downscale(bitmap, 800)
                val jpegBytes = bitmapToJpeg(resized, quality = 80)
                Log.i(TAG, "Sending ${jpegBytes.size} bytes to local model")

                val response = eng.analyzeImage(jpegBytes, PROMPT)
                Log.i(TAG, "Response: ${response.take(300)}")

                parseResponse(response)
            } catch (e: Exception) {
                Log.e(TAG, "Analysis failed", e)
                ParsedPriceTag()
            }
        }
    }

    private fun parseResponse(content: String): ParsedPriceTag {
        return try {
            // Extract JSON from response
            val jsonMatch = Regex("""\{[^{}]*"product_name"[^{}]*\}""").find(content)
            val jsonStr = if (jsonMatch != null) {
                jsonMatch.value
            } else {
                content
                    .replace(Regex("""```json\s*"""), "")
                    .replace(Regex("""```\s*"""), "")
                    .trim()
            }

            Log.d(TAG, "Parsing JSON: $jsonStr")
            val json = JSONObject(jsonStr)

            val unit = json.optStringOrNull("weight_unit")?.lowercase()?.let { raw ->
                when {
                    raw.contains("кг") -> WeightUnit.KG
                    raw.contains("г") -> WeightUnit.G
                    raw.contains("мл") -> WeightUnit.ML
                    raw.contains("л") -> WeightUnit.L
                    raw.contains("шт") -> WeightUnit.PCS
                    else -> null
                }
            }

            ParsedPriceTag(
                productName = json.optStringOrNull("product_name"),
                priceRegular = json.optStringOrNull("price_regular")?.toBigDecimalSafe(),
                priceDiscount = json.optStringOrNull("price_discount")?.toBigDecimalSafe(),
                weightValue = json.optStringOrNull("weight_value")?.toBigDecimalSafe(),
                weightUnit = unit
            ).also {
                Log.i(TAG, "Parsed: name=${it.productName}, regular=${it.priceRegular}, " +
                        "discount=${it.priceDiscount}, weight=${it.weightValue} ${it.weightUnit}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse: $content", e)
            ParsedPriceTag()
        }
    }

    private fun downscale(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longer = maxOf(bitmap.width, bitmap.height)
        if (longer <= maxSide) return bitmap
        val scale = maxSide.toFloat() / longer
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
    }

    private fun bitmapToJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        val value = optString(key, "")
        return if (value.isBlank() || value == "null") null else value
    }

    private fun String.toBigDecimalSafe(): BigDecimal? {
        return try {
            val cleaned = this.replace(Regex("""[^\d.,]"""), "").replace(',', '.')
            if (cleaned.isBlank()) null else BigDecimal(cleaned)
        } catch (_: Exception) { null }
    }
}
