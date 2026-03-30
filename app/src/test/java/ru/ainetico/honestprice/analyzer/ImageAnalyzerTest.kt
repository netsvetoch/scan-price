package ru.ainetico.honestprice.analyzer

import android.graphics.Bitmap
import android.graphics.Rect
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.ainetico.honestprice.calculator.PriceCalculator
import ru.ainetico.honestprice.image.ImagePreprocessor
import ru.ainetico.honestprice.model.OcrBlock
import ru.ainetico.honestprice.model.OcrResult
import ru.ainetico.honestprice.model.WeightUnit
import ru.ainetico.honestprice.ocr.BarcodeEngine
import ru.ainetico.honestprice.ocr.OcrEngine
import ru.ainetico.honestprice.parser.PriceTagParser
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class ImageAnalyzerTest {

    private val preprocessor = mockk<ImagePreprocessor>()
    private val ocrEngine = mockk<OcrEngine>()
    private val barcodeEngine = mockk<BarcodeEngine>()
    private val parser = PriceTagParser()
    private val calculator = PriceCalculator()

    private val analyzer = ImageAnalyzer(
        preprocessor, ocrEngine, barcodeEngine, parser, calculator
    )

    @Test
    fun `analyze returns complete result`() = runTest {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        coEvery { preprocessor.processBitmap(any(), any()) } returns bitmap
        coEvery { ocrEngine.recognize(any()) } returns OcrResult(
            listOf(
                OcrBlock("Молоко", Rect(0, 0, 100, 30), 0.9f),
                OcrBlock("89.90 ₽", Rect(0, 50, 100, 80), 0.9f),
                OcrBlock("1 л", Rect(0, 80, 50, 100), 0.9f)
            )
        )
        coEvery { barcodeEngine.scan(any()) } returns "4607025392408"

        val result = analyzer.analyze(bitmap, null)

        assertEquals("Молоко", result.tag.productName)
        assertEquals(BigDecimal("89.90"), result.tag.priceRegular)
        assertEquals(WeightUnit.L, result.tag.weightUnit)
        assertEquals("4607025392408", result.tag.barcode)
        assertNotNull(result.price)
        assertEquals(WeightUnit.L, result.price!!.displayUnit)
    }

    @Test
    fun `analyze returns null price when no price detected`() = runTest {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        coEvery { preprocessor.processBitmap(any(), any()) } returns bitmap
        coEvery { ocrEngine.recognize(any()) } returns OcrResult(emptyList())
        coEvery { barcodeEngine.scan(any()) } returns null

        val result = analyzer.analyze(bitmap, null)

        assertNull(result.tag.priceRegular)
        assertNull(result.price)
    }
}
