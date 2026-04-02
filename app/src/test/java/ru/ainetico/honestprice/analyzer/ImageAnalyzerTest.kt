package ru.ainetico.honestprice.analyzer

import android.graphics.Bitmap
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.ainetico.honestprice.calculator.PriceCalculator
import ru.ainetico.honestprice.data.AppSettings
import ru.ainetico.honestprice.model.ParsedPriceTag
import ru.ainetico.honestprice.model.WeightUnit
import ru.ainetico.honestprice.ocr.VisionEngine
import ru.ainetico.honestprice.ocr.VisionResult
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class ImageAnalyzerTest {

    private val localEngine = mockk<VisionEngine> {
        every { defaultPrompt } returns "local prompt"
    }
    private val remoteClient = mockk<VisionEngine> {
        every { defaultPrompt } returns "remote prompt"
    }
    private val calculator = PriceCalculator()
    private val appSettings = mockk<AppSettings> {
        coEvery { isRemoteModelConfigured() } returns false
        every { apiUrl } returns MutableStateFlow("")
        every { apiKey } returns MutableStateFlow("")
        every { apiModel } returns MutableStateFlow("")
        every { systemPrompt } returns flowOf("")
        every { localPrompt } returns flowOf("")
    }

    private val analyzer = ImageAnalyzer(localEngine, calculator, appSettings, remoteClient)

    @Test
    fun `analyze returns complete result from local engine`() = runTest {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        coEvery { localEngine.analyze(any(), any()) } returns VisionResult.Success(
            ParsedPriceTag(
                productName = "Молоко",
                priceRegular = BigDecimal("89.90"),
                weightValue = BigDecimal("1"),
                weightUnit = WeightUnit.L
            )
        )

        val result = analyzer.analyze(bitmap, null)

        assertEquals("Молоко", result.tag.productName)
        assertEquals(BigDecimal("89.90"), result.tag.priceRegular)
        assertEquals(WeightUnit.L, result.tag.weightUnit)
        assertNotNull(result.price)
        assertEquals(WeightUnit.L, result.price!!.displayUnit)
    }

    @Test
    fun `analyze returns null price when no price detected`() = runTest {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        coEvery { localEngine.analyze(any(), any()) } returns VisionResult.Success(ParsedPriceTag())

        val result = analyzer.analyze(bitmap, null)

        assertNull(result.tag.priceRegular)
        assertNull(result.price)
    }

    @Test
    fun `analyze throws RemoteAnalysisException when remote fails`() = runTest {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val mockRemoteClient = mockk<VisionEngine> {
            every { defaultPrompt } returns "remote prompt"
            coEvery { analyze(any(), any()) } returns VisionResult.Error(
                "Ошибка при подключении к серверу: Connection refused",
                RuntimeException("Connection refused")
            )
        }
        val remoteSettings = mockk<AppSettings> {
            coEvery { isRemoteModelConfigured() } returns true
            every { apiUrl } returns MutableStateFlow("https://example.com")
            every { apiKey } returns MutableStateFlow("key")
            every { apiModel } returns MutableStateFlow("model")
            every { systemPrompt } returns flowOf("")
            every { localPrompt } returns flowOf("")
        }
        val remoteAnalyzer = ImageAnalyzer(localEngine, calculator, remoteSettings, mockRemoteClient)

        try {
            remoteAnalyzer.analyze(bitmap, null)
            fail("Expected RemoteAnalysisException")
        } catch (e: RemoteAnalysisException) {
            assertTrue(e.message!!.contains("Ошибка при подключении к серверу"))
        }
    }

    @Test
    fun `analyze throws LocalAnalysisException when local fails`() = runTest {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        coEvery { localEngine.analyze(any(), any()) } returns VisionResult.Error("Engine not ready")

        try {
            analyzer.analyze(bitmap, null)
            fail("Expected LocalAnalysisException")
        } catch (e: LocalAnalysisException) {
            assertEquals("Engine not ready", e.message)
        }
    }

    @Test
    fun `analyze uses local engine when forceLocal is true`() = runTest {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val mockRemoteClient = mockk<VisionEngine> {
            every { defaultPrompt } returns "remote prompt"
        }
        val remoteSettings = mockk<AppSettings> {
            coEvery { isRemoteModelConfigured() } returns true
            every { apiUrl } returns MutableStateFlow("https://example.com")
            every { apiKey } returns MutableStateFlow("key")
            every { apiModel } returns MutableStateFlow("model")
            every { systemPrompt } returns flowOf("")
            every { localPrompt } returns flowOf("")
        }
        val remoteAnalyzer = ImageAnalyzer(localEngine, calculator, remoteSettings, mockRemoteClient)
        coEvery { localEngine.analyze(any(), any()) } returns VisionResult.Success(
            ParsedPriceTag(
                productName = "Хлеб",
                priceRegular = BigDecimal("45.00")
            )
        )

        val result = remoteAnalyzer.analyze(bitmap, null, forceLocal = true)

        assertEquals("Хлеб", result.tag.productName)
    }
}
