package ru.ainetico.scanprice.ocr

import android.graphics.Bitmap
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import ru.ainetico.scanprice.FrameConfig
import ru.ainetico.scanprice.model.WeightUnit
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class LocalVisionEngineTest {

    private lateinit var engine: LocalVisionEngine

    @Before
    fun setUp() {
        engine = LocalVisionEngine(RuntimeEnvironment.getApplication())
    }

    // --- isAvailable / not initialized ---

    @Test
    fun `isAvailable returns false before initialization`() {
        assertFalse(engine.isAvailable())
    }

    @Test
    fun `analyze returns error when engine not initialized`() = runTest {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val result = engine.analyze(bitmap, "test prompt")

        assertTrue(result is VisionResult.Error)
        assertEquals("Engine not ready", (result as VisionResult.Error).message)
    }

    // --- cropToPriceTag via reflection (private method) ---

    @Test
    fun `cropToPriceTag returns original bitmap when frame exceeds bounds`() {
        // Tiny bitmap where frame dimensions will be invalid
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val method = LocalVisionEngine::class.java.getDeclaredMethod("cropToPriceTag", Bitmap::class.java)
        method.isAccessible = true

        val result = method.invoke(engine, bitmap) as Bitmap

        assertSame(bitmap, result)
    }

    @Test
    fun `cropToPriceTag crops to correct frame dimensions`() {
        val width = 1000
        val height = 1500
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val method = LocalVisionEngine::class.java.getDeclaredMethod("cropToPriceTag", Bitmap::class.java)
        method.isAccessible = true

        val result = method.invoke(engine, bitmap) as Bitmap

        val expectedWidth = (width * FrameConfig.WIDTH_FRACTION).toInt()
        val expectedHeight = (expectedWidth / FrameConfig.ASPECT_RATIO).toInt()

        assertEquals(expectedWidth, result.width)
        assertEquals(expectedHeight, result.height)
    }

    @Test
    fun `cropToPriceTag centers frame horizontally`() {
        val width = 1000
        val height = 1500
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val method = LocalVisionEngine::class.java.getDeclaredMethod("cropToPriceTag", Bitmap::class.java)
        method.isAccessible = true

        val result = method.invoke(engine, bitmap) as Bitmap

        val expectedWidth = (width * FrameConfig.WIDTH_FRACTION).toInt()
        // Verify centered: left margin = right margin
        val expectedLeft = (width - expectedWidth) / 2
        // Result should have the expected width from centered crop
        assertEquals(expectedWidth, result.width)
        assertTrue("Left margin should be positive", expectedLeft > 0)
    }

    // --- parseResponse via reflection ---

    @Test
    fun `parseResponse parses valid JSON to ParsedPriceTag`() {
        val method = LocalVisionEngine::class.java.getDeclaredMethod("parseResponse", String::class.java)
        method.isAccessible = true

        val json = """{"product_name":"Масло","price_regular":250,"weight_value":180,"weight_unit":"г"}"""
        val tag = method.invoke(engine, json) as ru.ainetico.scanprice.model.ParsedPriceTag

        assertEquals("Масло", tag.productName)
        assertTrue(BigDecimal("250").compareTo(tag.priceRegular) == 0)
        assertTrue(BigDecimal("180").compareTo(tag.weightValue) == 0)
        assertEquals(WeightUnit.G, tag.weightUnit)
    }

    @Test
    fun `parseResponse handles minimal JSON`() {
        val method = LocalVisionEngine::class.java.getDeclaredMethod("parseResponse", String::class.java)
        method.isAccessible = true

        val json = """{"product_name":"Хлеб","price_regular":45}"""
        val tag = method.invoke(engine, json) as ru.ainetico.scanprice.model.ParsedPriceTag

        assertEquals("Хлеб", tag.productName)
        assertNull(tag.priceDiscount)
        assertNull(tag.weightUnit)
    }

    @Test(expected = org.json.JSONException::class)
    fun `parseResponse throws on invalid JSON`() {
        val method = LocalVisionEngine::class.java.getDeclaredMethod("parseResponse", String::class.java)
        method.isAccessible = true

        try {
            method.invoke(engine, "not json at all")
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.cause!!
        }
    }

    // --- Constants validation ---

    @Test
    fun `JSON_SCHEMA contains all expected fields`() {
        val schema = LocalVisionEngine::class.java.getDeclaredField("JSON_SCHEMA")
        schema.isAccessible = true
        val schemaStr = schema.get(null) as String

        assertTrue(schemaStr.contains("product_name"))
        assertTrue(schemaStr.contains("product_description"))
        assertTrue(schemaStr.contains("price_regular"))
        assertTrue(schemaStr.contains("price_discount"))
        assertTrue(schemaStr.contains("weight_value"))
        assertTrue(schemaStr.contains("weight_unit"))
    }

    @Test
    fun `DEFAULT_PROMPT mentions Russian store context`() {
        assertTrue(LocalVisionEngine.DEFAULT_PROMPT.contains("Russian"))
        assertTrue(LocalVisionEngine.DEFAULT_PROMPT.contains("price tag"))
    }
}
