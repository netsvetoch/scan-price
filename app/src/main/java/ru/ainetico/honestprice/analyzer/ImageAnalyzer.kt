package ru.ainetico.honestprice.analyzer

import android.graphics.Bitmap
import android.graphics.Rect
import ru.ainetico.honestprice.calculator.PriceCalculator
import ru.ainetico.honestprice.model.AnalysisResult
import ru.ainetico.honestprice.ocr.LocalVisionEngine

class ImageAnalyzer(
    private val localEngine: LocalVisionEngine,
    private val calculator: PriceCalculator
) {
    suspend fun analyze(bitmap: Bitmap, cropRect: Rect?): AnalysisResult {
        val tag = localEngine.analyze(bitmap)
        val price = calculator.calculate(tag)
        return AnalysisResult(tag = tag, price = price)
    }
}
