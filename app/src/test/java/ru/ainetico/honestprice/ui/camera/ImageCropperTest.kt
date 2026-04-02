package ru.ainetico.honestprice.ui.camera

import android.graphics.Bitmap
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImageCropperTest {

    @Test
    fun `cropToFrame crops center of bitmap`() {
        val bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
        val result = ImageCropper.cropToFrame(bitmap, density = 1f, isVerticalFrame = false)
        assertEquals(850, result.width)
        assertEquals(566, result.height)
    }

    @Test
    fun `cropToFrame with vertical frame swaps dimensions`() {
        val bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
        val result = ImageCropper.cropToFrame(bitmap, density = 1f, isVerticalFrame = true)
        assertEquals(566, result.width)
        assertEquals(850, result.height)
    }

    @Test
    fun `cropToFrame returns original when frame exceeds bitmap`() {
        val small = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        val result = ImageCropper.cropToFrame(small, density = 3f, isVerticalFrame = false)
        assertEquals(50, result.width)
    }

    @Test
    fun `cropAligned produces valid crop`() {
        val bitmap = Bitmap.createBitmap(2000, 1500, Bitmap.Config.ARGB_8888)
        val result = ImageCropper.cropAligned(
            bitmap = bitmap,
            viewW = 1080f, viewH = 1920f,
            panX = 0f, panY = 0f,
            zoom = 1f,
            density = 2f,
            isVerticalFrame = false
        )
        assertTrue(result.width > 0)
        assertTrue(result.height > 0)
        assertTrue(result.width <= bitmap.width)
        assertTrue(result.height <= bitmap.height)
    }
}
