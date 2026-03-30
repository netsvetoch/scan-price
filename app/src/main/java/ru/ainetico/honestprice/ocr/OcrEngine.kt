package ru.ainetico.honestprice.ocr

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import ru.ainetico.honestprice.model.OcrBlock
import ru.ainetico.honestprice.model.OcrResult

class OcrEngine {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): OcrResult {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val visionText = recognizer.process(image).await()

            val blocks = visionText.textBlocks.flatMap { textBlock ->
                textBlock.lines.mapNotNull { line ->
                    val box = line.boundingBox ?: return@mapNotNull null
                    OcrBlock(
                        text = line.text,
                        boundingBox = box,
                        confidence = line.confidence ?: 0f
                    )
                }
            }

            OcrResult(blocks)
        } catch (e: Exception) {
            Log.e("OcrEngine", "OCR failed", e)
            OcrResult(emptyList())
        }
    }
}
