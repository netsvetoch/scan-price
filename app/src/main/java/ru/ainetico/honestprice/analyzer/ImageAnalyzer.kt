package ru.ainetico.honestprice.analyzer

import android.graphics.Bitmap
import android.graphics.Rect
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import ru.ainetico.honestprice.calculator.PriceCalculator
import ru.ainetico.honestprice.image.ImagePreprocessor
import ru.ainetico.honestprice.model.AnalysisResult
import ru.ainetico.honestprice.ocr.BarcodeEngine
import ru.ainetico.honestprice.ocr.OcrEngine
import ru.ainetico.honestprice.parser.PriceTagParser

class ImageAnalyzer(
    private val preprocessor: ImagePreprocessor,
    private val ocrEngine: OcrEngine,
    private val barcodeEngine: BarcodeEngine,
    private val parser: PriceTagParser,
    private val calculator: PriceCalculator
) {
    suspend fun analyze(bitmap: Bitmap, cropRect: Rect?): AnalysisResult =
        coroutineScope {
            val processed = preprocessor.processBitmap(bitmap, cropRect)

            val ocrDeferred = async { ocrEngine.recognize(processed) }
            val barcodeDeferred = async { barcodeEngine.scan(processed) }

            val ocrResult = ocrDeferred.await()
            val barcode = barcodeDeferred.await()

            val tag = parser.parse(ocrResult, barcode)
            val price = calculator.calculate(tag)

            AnalysisResult(tag = tag, price = price)
        }
}
