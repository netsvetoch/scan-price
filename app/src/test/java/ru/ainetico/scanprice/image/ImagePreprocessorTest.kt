package ru.ainetico.honestprice.image

import android.graphics.Bitmap
import android.graphics.Rect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImagePreprocessorTest {

    private val preprocessor = ImagePreprocessor()

    @Test
    fun `calculates inSampleSize 2 for 4000px image`() {
        assertEquals(2, preprocessor.calculateInSampleSize(4000, 3000))
    }

    @Test
    fun `calculates inSampleSize 1 for 1920px image`() {
        assertEquals(1, preprocessor.calculateInSampleSize(1920, 1080))
    }

    @Test
    fun `calculates inSampleSize 1 for small image`() {
        assertEquals(1, preprocessor.calculateInSampleSize(800, 600))
    }

    @Test
    fun `calculates inSampleSize 4 for 8000px image`() {
        assertEquals(4, preprocessor.calculateInSampleSize(8000, 6000))
    }

    @Test
    fun `crops bitmap correctly`() {
        val bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
        val crop = Rect(100, 100, 500, 500)
        val result = preprocessor.cropBitmap(bitmap, crop)
        assertEquals(400, result.width)
        assertEquals(400, result.height)
    }

    @Test
    fun `processBitmap downsamples large image`() {
        val largeBitmap = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888)
        val result = preprocessor.processBitmap(largeBitmap, cropRect = null)
        assertTrue(minOf(result.width, result.height) >= 1080)
        assertTrue(result.width < 4000 || result.height < 3000)
    }

    @Test
    fun `processBitmap does not upsample small image`() {
        val smallBitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val result = preprocessor.processBitmap(smallBitmap, cropRect = null)
        assertEquals(800, result.width)
        assertEquals(600, result.height)
    }

    @Test
    fun `bitmapToJpeg returns non-empty byte array`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val result = preprocessor.bitmapToJpeg(bitmap, 80)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `bitmapToJpeg starts with JPEG magic bytes`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val result = preprocessor.bitmapToJpeg(bitmap, 80)
        assertEquals(0xFF.toByte(), result[0])
        assertEquals(0xD8.toByte(), result[1])
    }

    @Test
    fun `downscaleToMaxSide shrinks large bitmap`() {
        val bitmap = Bitmap.createBitmap(1280, 960, Bitmap.Config.ARGB_8888)
        val result = preprocessor.downscaleToMaxSide(bitmap, 640)
        assertEquals(640, result.width)
        assertEquals(480, result.height)
    }

    @Test
    fun `downscaleToMaxSide returns same bitmap when already small`() {
        val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        val result = preprocessor.downscaleToMaxSide(bitmap, 640)
        assertSame(bitmap, result)
    }
}
