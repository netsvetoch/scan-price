package ru.ainetico.honestprice.ocr

import android.graphics.Bitmap
import ru.ainetico.honestprice.model.ParsedPriceTag

/**
 * Unified contract for vision engines (local and remote).
 * Both engines return [VisionResult] — never throw from [analyze].
 */
interface VisionEngine {
    val defaultPrompt: String
    suspend fun analyze(bitmap: Bitmap, prompt: String): VisionResult
}

sealed interface VisionResult {
    data class Success(val tag: ParsedPriceTag) : VisionResult
    data class Error(val message: String, val cause: Throwable? = null) : VisionResult
}
