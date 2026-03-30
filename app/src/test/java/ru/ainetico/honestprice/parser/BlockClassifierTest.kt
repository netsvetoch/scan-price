package ru.ainetico.honestprice.parser

import android.graphics.Rect
import ru.ainetico.honestprice.model.OcrBlock
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BlockClassifierTest {

    private val classifier = BlockClassifier()

    @Test
    fun `classifies price with ruble sign`() {
        val block = OcrBlock("89.90 ₽", Rect(0, 100, 200, 200), 0.9f)
        assertEquals(BlockRole.PRICE, classifier.classify(block, imageHeight = 400))
    }

    @Test
    fun `classifies price with руб`() {
        val block = OcrBlock("123.45 руб", Rect(0, 100, 200, 200), 0.9f)
        assertEquals(BlockRole.PRICE, classifier.classify(block, imageHeight = 400))
    }

    @Test
    fun `classifies discount price near card keyword`() {
        val block = OcrBlock("по карте 69.90", Rect(0, 100, 200, 200), 0.9f)
        assertEquals(BlockRole.DISCOUNT_PRICE, classifier.classify(block, imageHeight = 400))
    }

    @Test
    fun `classifies discount price with скидка`() {
        val block = OcrBlock("скидка 49.99 ₽", Rect(0, 100, 200, 200), 0.9f)
        assertEquals(BlockRole.DISCOUNT_PRICE, classifier.classify(block, imageHeight = 400))
    }

    @Test
    fun `classifies weight in grams`() {
        val block = OcrBlock("500 г", Rect(0, 200, 100, 250), 0.9f)
        assertEquals(BlockRole.WEIGHT, classifier.classify(block, imageHeight = 400))
    }

    @Test
    fun `classifies weight in kg`() {
        val block = OcrBlock("1.5 кг", Rect(0, 200, 100, 250), 0.9f)
        assertEquals(BlockRole.WEIGHT, classifier.classify(block, imageHeight = 400))
    }

    @Test
    fun `classifies weight in ml`() {
        val block = OcrBlock("330 мл", Rect(0, 200, 100, 250), 0.9f)
        assertEquals(BlockRole.WEIGHT, classifier.classify(block, imageHeight = 400))
    }

    @Test
    fun `classifies weight in liters`() {
        val block = OcrBlock("1 л", Rect(0, 200, 100, 250), 0.9f)
        assertEquals(BlockRole.WEIGHT, classifier.classify(block, imageHeight = 400))
    }

    @Test
    fun `classifies weight in pieces`() {
        val block = OcrBlock("10 шт", Rect(0, 200, 100, 250), 0.9f)
        assertEquals(BlockRole.WEIGHT, classifier.classify(block, imageHeight = 400))
    }

    @Test
    fun `classifies name in upper third`() {
        val block = OcrBlock("Молоко Простоквашино", Rect(0, 0, 300, 50), 0.9f)
        assertEquals(BlockRole.NAME, classifier.classify(block, imageHeight = 400))
    }

    @Test
    fun `classifies noise for dates`() {
        val block = OcrBlock("01.03.2026", Rect(0, 350, 100, 400), 0.9f)
        assertEquals(BlockRole.NOISE, classifier.classify(block, imageHeight = 400))
    }

    @Test
    fun `classifies abbreviated names`() {
        val block = OcrBlock("Филе кур. охл.", Rect(0, 10, 300, 50), 0.9f)
        assertEquals(BlockRole.NAME, classifier.classify(block, imageHeight = 400))
    }

    @Test
    fun `classifies price with р dot`() {
        val block = OcrBlock("199.00 р.", Rect(0, 100, 200, 200), 0.9f)
        assertEquals(BlockRole.PRICE, classifier.classify(block, imageHeight = 400))
    }
}
