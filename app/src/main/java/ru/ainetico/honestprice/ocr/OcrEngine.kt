package ru.ainetico.honestprice.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.ainetico.honestprice.model.OcrBlock
import ru.ainetico.honestprice.model.OcrResult
import java.io.File
import java.io.FileOutputStream

class OcrEngine(private val appContext: Context) {

    companion object {
        private const val TAG = "OcrEngine"
        private const val LANGUAGE = "rus"
    }

    private var tessApi: TessBaseAPI? = null

    private suspend fun ensureInitialized(): TessBaseAPI {
        tessApi?.let { return it }

        return withContext(Dispatchers.IO) {
            val dataDir = File(appContext.filesDir, "tesseract")
            val tessDataDir = File(dataDir, "tessdata")

            if (!tessDataDir.exists()) {
                tessDataDir.mkdirs()
                // Copy traineddata from assets
                val assetFileName = "$LANGUAGE.traineddata"
                appContext.assets.open("tessdata/$assetFileName").use { input ->
                    FileOutputStream(File(tessDataDir, assetFileName)).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Copied $assetFileName to ${tessDataDir.absolutePath}")
            }

            val api = TessBaseAPI()
            if (!api.init(dataDir.absolutePath, LANGUAGE)) {
                Log.e(TAG, "Tesseract init failed for language: $LANGUAGE")
                throw RuntimeException("Tesseract init failed")
            }
            // PSM_AUTO — automatic page segmentation, good for price tags
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            Log.d(TAG, "Tesseract initialized with language: $LANGUAGE")
            tessApi = api
            api
        }
    }

    suspend fun recognize(bitmap: Bitmap): OcrResult {
        return try {
            Log.d(TAG, "Starting OCR on bitmap ${bitmap.width}x${bitmap.height}")

            val api = ensureInitialized()
            val blocks = withContext(Dispatchers.Default) {
                api.setImage(bitmap)

                val fullText = api.utF8Text
                Log.d(TAG, "Tesseract full text: '${fullText?.take(200)}'")

                val lines = mutableListOf<OcrBlock>()

                val iterator = api.resultIterator
                if (iterator != null) {
                    val level = TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE
                    do {
                        val text = iterator.getUTF8Text(level) ?: continue
                        val confidence = iterator.confidence(level)
                        val boundingRect = iterator.getBoundingRect(level)

                        if (text.isNotBlank() && boundingRect != null) {
                            Log.d(TAG, "  Line: '$text' confidence=$confidence box=$boundingRect")
                            lines.add(
                                OcrBlock(
                                    text = text.trim(),
                                    boundingBox = boundingRect,
                                    confidence = confidence / 100f
                                )
                            )
                        }
                    } while (iterator.next(level))
                    iterator.delete()
                }

                api.clear()
                lines
            }

            Log.d(TAG, "OCR result: ${blocks.size} lines extracted")
            OcrResult(blocks)
        } catch (e: Exception) {
            Log.e(TAG, "OCR failed", e)
            OcrResult(emptyList())
        }
    }
}
