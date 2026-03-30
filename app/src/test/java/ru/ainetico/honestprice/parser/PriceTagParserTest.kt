package ru.ainetico.honestprice.parser

import android.graphics.Rect
import ru.ainetico.honestprice.model.OcrBlock
import ru.ainetico.honestprice.model.OcrResult
import ru.ainetico.honestprice.model.WeightUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class PriceTagParserTest {

    private val parser = PriceTagParser()

    @Test
    fun `parses complete price tag`() {
        val blocks = listOf(
            OcrBlock("Молоко 3.2%", Rect(10, 10, 300, 50), 0.95f),
            OcrBlock("89.90 ₽", Rect(10, 100, 250, 180), 0.98f),
            OcrBlock("по карте 69.90", Rect(10, 200, 250, 260), 0.96f),
            OcrBlock("1 л", Rect(10, 270, 100, 300), 0.9f)
        )
        val result = parser.parse(OcrResult(blocks), barcode = "4607025392408")

        assertEquals("Молоко 3.2%", result.productName)
        assertEquals(BigDecimal("89.90"), result.priceRegular)
        assertEquals(BigDecimal("69.90"), result.priceDiscount)
        assertEquals(BigDecimal("1"), result.weightValue)
        assertEquals(WeightUnit.L, result.weightUnit)
        assertEquals("4607025392408", result.barcode)
    }

    @Test
    fun `parses tag with single price`() {
        val blocks = listOf(
            OcrBlock("Хлеб белый", Rect(10, 10, 300, 50), 0.95f),
            OcrBlock("45.00 ₽", Rect(10, 100, 250, 180), 0.98f),
            OcrBlock("500 г", Rect(10, 200, 100, 230), 0.9f)
        )
        val result = parser.parse(OcrResult(blocks), barcode = null)

        assertEquals("Хлеб белый", result.productName)
        assertEquals(BigDecimal("45.00"), result.priceRegular)
        assertNull(result.priceDiscount)
        assertEquals(BigDecimal("500"), result.weightValue)
        assertEquals(WeightUnit.G, result.weightUnit)
        assertNull(result.barcode)
    }

    @Test
    fun `two prices without context - smaller is discount`() {
        val blocks = listOf(
            OcrBlock("Сок апельсиновый", Rect(10, 10, 300, 50), 0.95f),
            OcrBlock("129.90", Rect(10, 100, 250, 180), 0.98f),
            OcrBlock("99.90", Rect(10, 200, 250, 260), 0.96f)
        )
        val result = parser.parse(OcrResult(blocks), barcode = null)

        assertEquals(BigDecimal("129.90"), result.priceRegular)
        assertEquals(BigDecimal("99.90"), result.priceDiscount)
    }

    @Test
    fun `empty OCR result returns all nulls`() {
        val result = parser.parse(OcrResult(emptyList()), barcode = null)

        assertNull(result.productName)
        assertNull(result.priceRegular)
        assertNull(result.priceDiscount)
        assertNull(result.weightValue)
        assertNull(result.weightUnit)
    }

    @Test
    fun `parses weight in grams`() {
        val blocks = listOf(
            OcrBlock("250 гр", Rect(10, 200, 100, 230), 0.9f)
        )
        val result = parser.parse(OcrResult(blocks), barcode = null)

        assertEquals(BigDecimal("250"), result.weightValue)
        assertEquals(WeightUnit.G, result.weightUnit)
    }

    @Test
    fun `parses price with comma separator`() {
        val blocks = listOf(
            OcrBlock("89,90 руб", Rect(10, 100, 250, 180), 0.98f)
        )
        val result = parser.parse(OcrResult(blocks), barcode = null)

        assertEquals(BigDecimal("89.90"), result.priceRegular)
    }

    @Test
    fun `parses weight in kg with decimal`() {
        val blocks = listOf(
            OcrBlock("0.5 кг", Rect(10, 200, 100, 230), 0.9f)
        )
        val result = parser.parse(OcrResult(blocks), barcode = null)

        assertEquals(BigDecimal("0.5"), result.weightValue)
        assertEquals(WeightUnit.KG, result.weightUnit)
    }
}
