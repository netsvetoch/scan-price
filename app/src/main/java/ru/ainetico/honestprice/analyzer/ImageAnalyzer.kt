package ru.ainetico.honestprice.analyzer

import android.graphics.Bitmap
import android.graphics.Rect
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import ru.ainetico.honestprice.calculator.PriceCalculator
import ru.ainetico.honestprice.model.AnalysisResult
import ru.ainetico.honestprice.model.ParsedPriceTag
import ru.ainetico.honestprice.ocr.BarcodeEngine
import ru.ainetico.honestprice.ocr.LocalVisionEngine

class ImageAnalyzer(
    private val localEngine: LocalVisionEngine,
    private val barcodeEngine: BarcodeEngine,
    private val calculator: PriceCalculator
) {
    suspend fun analyze(bitmap: Bitmap, cropRect: Rect?): AnalysisResult =
        coroutineScope {
            val visionDeferred = async { localEngine.analyze(bitmap) }
            val barcodeDeferred = async { barcodeEngine.scan(bitmap) }

            val tag = visionDeferred.await()
            val barcode = barcodeDeferred.await()

            val tagWithBarcode = tag.copy(barcode = barcode ?: tag.barcode)
            val price = calculator.calculate(tagWithBarcode)

            AnalysisResult(tag = tagWithBarcode, price = price)
        }
}
