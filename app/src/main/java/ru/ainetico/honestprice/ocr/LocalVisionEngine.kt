package ru.ainetico.honestprice.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import ru.ainetico.honestprice.BuildConfig
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.ainetico.honestprice.model.ParsedPriceTag
import org.json.JSONObject
import java.io.File
import ru.ainetico.honestprice.image.ImagePreprocessor

/**
 * On-device vision inference using llama.cpp + Qwen3.5 GGUF.
 * Fully offline — no network required.
 */
class LocalVisionEngine(private val appContext: Context) {

  companion object {
    private const val TAG = "LocalVisionEngine"
    private const val MODEL_FILENAME = "Qwen3.5-0.8B-Q4_K_M.gguf"
    private const val MMPROJ_FILENAME = "mmproj-BF16.gguf"

    private const val JSON_SCHEMA = """
{
  "type": "object",
  "properties": {
    "product_name": { "type": ["string", "null"], "description": "Short product name from the tag" },
    "product_description": { "type": ["string", "null"], "description": "Additional details: brand, composition, variety" },
    "price_regular": { "type": ["number", "null"], "description": "Regular price" },
    "price_discount": { "type": ["number", "null"], "description": "Discounted/card price, null if no discount" },
    "weight_value": { "type": ["number", "null"], "description": "Weight or volume number as on the tag" },
    "weight_unit": { "enum": ["г", "кг", "мл", "л", "шт", null] }
  },
  "required": ["product_name", "price_regular"],
  "additionalProperties": false
}
"""

    const val DEFAULT_PROMPT = "This is a photo of a price tag from a Russian store. " +
        "Extract the product name, description, prices, weight/volume from the tag. " +
        "If there is a discounted or card price, include it separately from the regular price. " +
        "Use null for any fields you cannot read."
  }

  private val imagePreprocessor = ImagePreprocessor()
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
        modelsDir = File(
          android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
          ), "models"
        )
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
  suspend fun analyze(bitmap: Bitmap, prompt: String = DEFAULT_PROMPT): ParsedPriceTag {
    val eng = engine
    if (!isReady || eng == null) {
      Log.e(TAG, "Engine not ready!")
      return ParsedPriceTag()
    }

    return withContext(Dispatchers.IO) {
      try {
        // Preprocess: crop to frame area, downscale
        val cropped = cropToPriceTag(bitmap)
        val resized = imagePreprocessor.downscaleToMaxSide(cropped, 640)
        val imageBytes = imagePreprocessor.bitmapToJpeg(resized, quality = 60)
        Log.i(
          TAG,
          "Preprocessed: ${bitmap.width}x${bitmap.height} → crop ${cropped.width}x${cropped.height} → ${resized.width}x${resized.height}, ${imageBytes.size / 1024}KB"
        )

        val response = eng.analyzeImage(imageBytes, prompt, JSON_SCHEMA)
        if (BuildConfig.DEBUG) Log.d(TAG, "Response (${response.length} chars): '${response.take(500)}'")

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
      if (BuildConfig.DEBUG) Log.d(TAG, "Parsing JSON: $content")
      val json = JSONObject(content)
      PriceTagParser.parse(json).also {
        Log.i(
          TAG, "Parsed: name=${it.productName}, regular=${it.priceRegular}, " +
                  "discount=${it.priceDiscount}, weight=${it.weightValue} ${it.weightUnit}"
        )
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
    val frameWidth = (bitmap.width * ru.ainetico.honestprice.FrameConfig.WIDTH_FRACTION).toInt()
    val frameHeight = (frameWidth / ru.ainetico.honestprice.FrameConfig.ASPECT_RATIO).toInt()
    val left = (bitmap.width - frameWidth) / 2
    val top = ((bitmap.height - frameHeight) / 2f).toInt().coerceAtLeast(0)

    if (frameWidth <= 0 || frameHeight <= 0 || left + frameWidth > bitmap.width || top + frameHeight > bitmap.height) {
      return bitmap
    }

    return Bitmap.createBitmap(bitmap, left, top, frameWidth, frameHeight)
  }

}
