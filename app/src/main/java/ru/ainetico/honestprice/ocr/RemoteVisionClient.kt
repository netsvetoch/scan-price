package ru.ainetico.honestprice.ocr

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import ru.ainetico.honestprice.BuildConfig
import ru.ainetico.honestprice.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import ru.ainetico.honestprice.model.ParsedPriceTag
import java.io.ByteArrayOutputStream

/**
 * Sends image to OpenAI-compatible API for price tag analysis.
 * Used when user configures an external server in settings.
 */
class RemoteVisionClient(private val appSettings: AppSettings) : VisionEngine {

    companion object {
        private const val TAG = "RemoteVisionClient"

        const val DEFAULT_SYSTEM_PROMPT = "Analyze this Russian store price tag photo. Extract product info into JSON.\n\n" +
                "Rules:\n" +
                "- product_name: short product name as written on the tag\n" +
                "- product_description: additional details in small print (composition, brand details, etc.), null if none\n" +
                "- price_regular: regular price as a number, null if unreadable\n" +
                "- price_discount: discounted/loyalty card price as a number, null if NO discount exists\n" +
                "- weight_value: number exactly as on tag (500 for \"500г\", 1 for \"1кг\")\n" +
                "- weight_unit: one of г, кг, мл, л, шт — null if not shown"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val PRICE_TAG_SCHEMA_JSON = """
            {
              "type": "object",
              "required": [
                "product_name", "product_description", "price_regular",
                "price_discount", "weight_value", "weight_unit"
              ],
              "properties": {
                "product_name":        { "type": "string" },
                "product_description": { "type": ["string", "null"] },
                "price_regular":       { "type": ["number", "null"] },
                "price_discount":      { "type": ["number", "null"] },
                "weight_value":        { "type": ["number", "null"] },
                "weight_unit": {
                  "type": ["string", "null"],
                  "enum": ["г", "кг", "мл", "л", "шт", null]
                }
              },
              "additionalProperties": false
            }
        """.trimIndent()
    }

    override suspend fun analyze(bitmap: Bitmap, prompt: String): VisionResult =
        withContext(Dispatchers.IO) {
            try {
                val apiUrl = appSettings.apiUrl.value
                val apiKey = appSettings.apiKey.value
                val model = appSettings.apiModel.value

                val base64Image = bitmapToBase64(bitmap)
                val baseUrl = apiUrl.trimEnd('/')
                Log.d(TAG, "Sending to $baseUrl (image: ${base64Image.length} chars)")

                val messagesArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", prompt)
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

                val jsonSchema = JSONObject(PRICE_TAG_SCHEMA_JSON)

                val requestBody = JSONObject().apply {
                    if (model.isNotBlank()) put("model", model)
                    put("messages", messagesArray)
                    put("temperature", 0.1)
                    put("stream", false)
                    put("response_format", JSONObject().apply {
                        put("type", "json_schema")
                        put("json_schema", JSONObject().apply {
                            put("name", "price_tag")
                            put("strict", true)
                            put("schema", jsonSchema)
                        })
                    })
                }

                val request = Request.Builder()
                    .url("$baseUrl/chat/completions")
                    .post(requestBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .apply {
                        if (apiKey.isNotBlank()) {
                            addHeader("Authorization", "Bearer $apiKey")
                        }
                    }
                    .build()

                ApiHttpClient.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val error = response.body?.string()?.take(200) ?: "Unknown error"
                        throw RuntimeException("API error ${response.code}: $error")
                    }

                    val responseBody = response.body?.string()
                        ?: throw RuntimeException("Empty response body")
                    if (BuildConfig.DEBUG) Log.d(TAG, "Response: ${responseBody.take(300)}")

                    val content = JSONObject(responseBody)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")

                    VisionResult.Success(parseResponse(content))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Remote analysis failed", e)
                VisionResult.Error("Ошибка при подключении к серверу: ${e.message}", e)
            }
        }

    internal fun parseResponse(content: String): ParsedPriceTag {
        if (BuildConfig.DEBUG) Log.d(TAG, "parseResponse input (${content.length} chars): '${content.take(500)}'")

        val jsonMatch = Regex("""\{[^{}]*"product_name"[^{}]*\}""").find(content)
        val jsonStr = jsonMatch?.value ?: content
            .replace(Regex("""```json\s*"""), "")
            .replace(Regex("""```\s*"""), "")
            .trim()

        if (BuildConfig.DEBUG) Log.d(TAG, "Parsing JSON: '$jsonStr'")
        val json = JSONObject(jsonStr)

        return PriceTagParser.parse(json)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val maxSide = 800
        val resized = if (maxOf(bitmap.width, bitmap.height) > maxSide) {
            val scale = maxSide.toFloat() / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap

        val stream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        if (resized !== bitmap) resized.recycle()
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

}
