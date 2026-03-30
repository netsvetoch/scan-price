package ru.ainetico.honestprice.ocr

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import ru.ainetico.honestprice.model.OcrResult
import ru.ainetico.honestprice.model.ParsedPriceTag
import ru.ainetico.honestprice.model.WeightUnit
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

/**
 * Sends price tag image to an OpenAI-compatible vision API (e.g. local Qwen, Ollama)
 * and receives structured price tag data back.
 *
 * Replaces the OCR + Parser pipeline entirely — the LLM handles both recognition and parsing.
 */
class VisionApiClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val model: String = DEFAULT_MODEL,
    private val apiKey: String = ""
) {

    companion object {
        private const val TAG = "VisionApiClient"
        const val DEFAULT_BASE_URL = "https://ollama.netsvetaev.dev/v1"
        const val DEFAULT_MODEL = "qwen3.5:9b"

        private const val SYSTEM_PROMPT = """Ты анализируешь фото ценника из магазина. Извлеки данные и верни ТОЛЬКО JSON без markdown:
{"product_name": "название товара", "price_regular": "цена без скидки (число)", "price_discount": "цена со скидкой/по карте (число или null)", "weight_value": "вес/объём (число или null)", "weight_unit": "единица: г, кг, мл, л, шт (или null)"}
Если поле не найдено, ставь null. Цены в рублях, числа без символа валюты."""
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Send image to vision API and get parsed price tag data.
     * Returns ParsedPriceTag with as many fields filled as the model could extract.
     */
    suspend fun analyze(bitmap: Bitmap): ParsedPriceTag = withContext(Dispatchers.IO) {
        try {
            val base64Image = bitmapToBase64(bitmap)
            Log.d(TAG, "Sending image to $baseUrl/chat/completions (model=$model, image=${base64Image.length} chars)")

            val messagesArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", "data:image/jpeg;base64,$base64Image")
                            })
                        })
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", "Проанализируй этот ценник")
                        })
                    })
                })
            }

            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", messagesArray)
                put("max_tokens", 500)
                put("temperature", 0.1)
            }

            val request = Request.Builder()
                .url("$baseUrl/chat/completions")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .apply {
                    if (apiKey.isNotBlank()) {
                        addHeader("Authorization", "Bearer $apiKey")
                    }
                }
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                Log.e(TAG, "API error ${response.code}: $responseBody")
                return@withContext ParsedPriceTag()
            }

            Log.d(TAG, "API response: ${responseBody?.take(500)}")

            val message = JSONObject(responseBody!!)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")

            // Qwen3.5 may put the answer in "content" or in "reasoning"
            var content = message.optString("content", "")
            if (content.isBlank()) {
                content = message.optString("reasoning", "")
                Log.d(TAG, "Content empty, using reasoning field (${content.length} chars)")
            }

            parseApiResponse(content)
        } catch (e: Exception) {
            Log.e(TAG, "Vision API call failed", e)
            ParsedPriceTag()
        }
    }

    private fun parseApiResponse(content: String): ParsedPriceTag {
        return try {
            // Extract JSON object from content — may be surrounded by reasoning text
            val jsonMatch = Regex("""\{[^{}]*"product_name"[^{}]*\}""").find(content)
            val jsonStr = if (jsonMatch != null) {
                jsonMatch.value
            } else {
                // Fallback: strip markdown fences and try the whole string
                content
                    .replace(Regex("""```json\s*"""), "")
                    .replace(Regex("""```\s*"""), "")
                    .trim()
            }

            Log.d(TAG, "Parsing JSON: $jsonStr")
            val json = JSONObject(jsonStr)

            val unit = json.optString("weight_unit", "").lowercase().let { raw ->
                when {
                    raw.contains("кг") -> WeightUnit.KG
                    raw.contains("г") -> WeightUnit.G
                    raw.contains("мл") -> WeightUnit.ML
                    raw.contains("л") -> WeightUnit.L
                    raw.contains("шт") -> WeightUnit.PCS
                    else -> null
                }
            }

            val tag = ParsedPriceTag(
                productName = json.optStringOrNull("product_name"),
                priceRegular = json.optStringOrNull("price_regular")?.toBigDecimalSafe(),
                priceDiscount = json.optStringOrNull("price_discount")?.toBigDecimalSafe(),
                weightValue = json.optStringOrNull("weight_value")?.toBigDecimalSafe(),
                weightUnit = unit
            )

            Log.d(TAG, "Parsed: name=${tag.productName}, regular=${tag.priceRegular}, discount=${tag.priceDiscount}, weight=${tag.weightValue} ${tag.weightUnit}")
            tag
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse API response: $content", e)
            ParsedPriceTag()
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
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
