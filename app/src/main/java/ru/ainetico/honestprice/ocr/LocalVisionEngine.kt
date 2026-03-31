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

        private const val PROMPT = "This is a photo of a price tag from a Russian store. " +
                "Return ONLY a JSON object, no markdown, no explanation.\n" +
                "Example: {\"product_name\":\"Молоко 3.2%\",\"price_regular\":89.99,\"price_discount\":69.99,\"weight_value\":900,\"weight_unit\":\"мл\"}\n" +
                "Rules:\n" +
                "- product_name: full product name from the tag\n" +
                "- price_regular: regular price number\n" +
                "- price_discount: discounted/card price number, or null\n" +
                "- weight_value: weight or volume number EXACTLY as written on the tag (e.g. 500 for 500г, 1 for 1кг)\n" +
                "- weight_unit: exactly one of: г, кг, мл, л, шт\n" +
                "- null for missing fields\n" +
                "JSON:"
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
            Log.i(TAG, "Waiting for engine to initialize, current state: ${eng.state.value}")
            eng.state.first { state ->
                Log.i(TAG, "State transition: ${state.javaClass.simpleName}")
                state is InferenceEngine.State.Initialized
            }
            Log.i(TAG, "Engine initialized!")

            // List available .so files for debugging
            val nativeLibDir = File(appContext.applicationInfo.nativeLibraryDir)
            Log.i(TAG, "Native libs in ${nativeLibDir.absolutePath}:")
            nativeLibDir.listFiles()?.forEach { Log.i(TAG, "  ${it.name} (${it.length() / 1024}KB)") }

            // Find model files — check internal storage first, then Downloads
            var modelsDir = File(appContext.filesDir, "models")
            if (!File(modelsDir, MODEL_FILENAME).exists()) {
                modelsDir = File(android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS), "models")
            }
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
                // Preprocess: crop to frame area, grayscale, downscale, WebP
                val cropped = cropToPriceTag(bitmap)
                val grayscale = toGrayscale(cropped)
                val resized = downscale(grayscale, 640)
                val imageBytes = bitmapToJpeg(resized, quality = 60)
                Log.i(TAG, "Preprocessed: ${bitmap.width}x${bitmap.height} → crop ${cropped.width}x${cropped.height} → ${resized.width}x${resized.height}, ${imageBytes.size / 1024}KB")

                val response = eng.analyzeImage(imageBytes, PROMPT)
                Log.i(TAG, "Response (${response.length} chars): '${response.take(500)}'")

                if (response.isBlank()) {
                    Log.w(TAG, "Empty response from model")
                    return@withContext ParsedPriceTag()
                }

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

    /**
     * Crop to the viewfinder frame area — matches FrameOverlay dimensions:
     * width = 85% of image, height = width * 0.5, centered with 15% vertical offset up.
     * This is where the user aims the price tag.
     */
    private fun cropToPriceTag(bitmap: Bitmap): Bitmap {
        val frameWidth = (bitmap.width * 0.85f).toInt()
        val frameHeight = (frameWidth * 2f / 3f).toInt()  // 3:2 aspect ratio
        val left = (bitmap.width - frameWidth) / 2
        val top = ((bitmap.height - frameHeight) / 2f - bitmap.height * 0.15f).toInt().coerceAtLeast(0)

        if (frameWidth <= 0 || frameHeight <= 0 || left + frameWidth > bitmap.width || top + frameHeight > bitmap.height) {
            return bitmap
        }

        return Bitmap.createBitmap(bitmap, left, top, frameWidth, frameHeight)
    }

    /**
     * Convert to grayscale — reduces image complexity and size significantly.
     */
    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val grayscale = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(grayscale)
        val paint = android.graphics.Paint()
        val colorMatrix = android.graphics.ColorMatrix()
        colorMatrix.setSaturation(0f)
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return grayscale
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
