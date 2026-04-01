package ru.ainetico.honestprice.ocr

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.ainetico.honestprice.model.ParsedPriceTag
import ru.ainetico.honestprice.model.WeightUnit
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends image to OpenAI-compatible API for price tag analysis.
 * Used when user configures an external server in settings.
 */
class RemoteVisionClient {

    companion object {
        private const val TAG = "RemoteVisionClient"
        private const val TIMEOUT_MS = 30_000

        private const val SYSTEM_PROMPT = "Analyze this Russian store price tag photo. Extract product info into JSON.\n\n" +
                "Rules:\n" +
                "- product_name: full name as written on the tag\n" +
                "- price_regular: regular price as a number, null if unreadable\n" +
                "- price_discount: discounted/loyalty card price as a number, null if NO discount exists\n" +
                "- weight_value: number exactly as on tag (500 for \"500г\", 1 for \"1кг\")\n" +
                "- weight_unit: one of г, кг, мл, л, шт — null if not shown"
    }

    suspend fun analyze(bitmap: Bitmap, apiUrl: String, apiKey: String): ParsedPriceTag =
        withContext(Dispatchers.IO) {
            try {
                val base64Image = bitmapToBase64(bitmap)
                val baseUrl = apiUrl.trimEnd('/')
                Log.d(TAG, "Sending to $baseUrl (image: ${base64Image.length} chars)")

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

                val jsonSchema = JSONObject().apply {
                    put("type", "object")
                    put("required", JSONArray().apply {
                        put("product_name"); put("price_regular"); put("price_discount")
                        put("weight_value"); put("weight_unit")
                    })
                    put("properties", JSONObject().apply {
                        put("product_name", JSONObject().put("type", "string"))
                        put("price_regular", JSONObject().put("type", JSONArray().apply { put("number"); put("null") }))
                        put("price_discount", JSONObject().put("type", JSONArray().apply { put("number"); put("null") }))
                        put("weight_value", JSONObject().put("type", JSONArray().apply { put("number"); put("null") }))
                        put("weight_unit", JSONObject().apply {
                            put("type", JSONArray().apply { put("string"); put("null") })
                            put("enum", JSONArray().apply { put("г"); put("кг"); put("мл"); put("л"); put("шт"); put(JSONObject.NULL) })
                        })
                    })
                    put("additionalProperties", false)
                }

                val requestBody = JSONObject().apply {
                    put("messages", messagesArray)
                    put("max_tokens", 512)
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

                val url = URL("$baseUrl/chat/completions")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    setRequestProperty("Content-Type", "application/json")
                    if (apiKey.isNotBlank()) {
                        setRequestProperty("Authorization", "Bearer $apiKey")
                    }
                    doOutput = true
                }

                conn.outputStream.use { it.write(requestBody.toString().toByteArray()) }

                val responseCode = conn.responseCode
                if (responseCode != 200) {
                    val error = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                    throw RuntimeException("API error $responseCode: ${error.take(200)}")
                }

                val responseBody = conn.inputStream.bufferedReader().readText()
                Log.d(TAG, "Response: ${responseBody.take(300)}")

                val content = JSONObject(responseBody)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")

                parseResponse(content)
            } catch (e: Exception) {
                Log.e(TAG, "Remote analysis failed", e)
                throw e
            }
        }

    private fun parseResponse(content: String): ParsedPriceTag {
        val jsonMatch = Regex("""\{[^{}]*"product_name"[^{}]*\}""").find(content)
        val jsonStr = jsonMatch?.value ?: content
            .replace(Regex("""```json\s*"""), "")
            .replace(Regex("""```\s*"""), "")
            .trim()

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

        val discount = json.optStringOrNull("price_discount")?.toBigDecimalSafe()

        return ParsedPriceTag(
            productName = json.optStringOrNull("product_name"),
            priceRegular = json.optStringOrNull("price_regular")?.toBigDecimalSafe(),
            priceDiscount = if (discount != null && discount.compareTo(BigDecimal.ZERO) == 0) null else discount,
            weightValue = json.optStringOrNull("weight_value")?.toBigDecimalSafe(),
            weightUnit = unit
        )
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val maxSide = 800
        val resized = if (maxOf(bitmap.width, bitmap.height) > maxSide) {
            val scale = maxSide.toFloat() / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap

        val stream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 70, stream)
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
