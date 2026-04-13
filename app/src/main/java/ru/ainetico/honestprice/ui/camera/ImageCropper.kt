package ru.ainetico.honestprice.ui.camera

import android.graphics.Bitmap
import android.util.Log
import ru.ainetico.honestprice.FrameConfig

object ImageCropper {

  private const val TAG = "ImageCropper"

  fun cropToFrame(bitmap: Bitmap, density: Float, isVerticalFrame: Boolean): Bitmap {
    val aspectRatio = FrameConfig.ratio(isVerticalFrame)
    val baseW = (bitmap.width * FrameConfig.WIDTH_FRACTION).toInt()
    val baseH = (baseW / FrameConfig.ASPECT_RATIO).toInt()
    val frameWidth = if (aspectRatio >= 1f) baseW else baseH
    val frameHeight = if (aspectRatio >= 1f) baseH else baseW
    val left = (bitmap.width - frameWidth) / 2
    val rawTop = FrameConfig.frameTop(bitmap.height.toFloat(), frameHeight.toFloat(), density)
    val top = rawTop.toInt().coerceAtLeast(0)

    if (frameWidth <= 0 || frameHeight <= 0
      || left + frameWidth > bitmap.width || top + frameHeight > bitmap.height
      || rawTop < 0
    ) {
      return bitmap
    }
    return Bitmap.createBitmap(bitmap, left, top, frameWidth, frameHeight)
  }

  fun cropAligned(
    bitmap: Bitmap,
    viewW: Float, viewH: Float,
    panX: Float, panY: Float,
    zoom: Float,
    density: Float,
    isVerticalFrame: Boolean
  ): Bitmap {
    val aspectRatio = FrameConfig.ratio(isVerticalFrame)

    val scaleToFill = viewW / bitmap.width
    val totalScale = scaleToFill * zoom

    val imgCenterX = viewW / 2f
    val imgCenterY = viewH / 2f

    val baseW = viewW * FrameConfig.WIDTH_FRACTION
    val baseH = baseW / FrameConfig.ASPECT_RATIO
    val frameW = if (aspectRatio >= 1f) baseW else baseH
    val frameH = if (aspectRatio >= 1f) baseH else baseW
    val frameLeft = (viewW - frameW) / 2f
    val frameTop = FrameConfig.frameTop(viewH, frameH, density)

    fun viewToBmpX(vx: Float) = ((vx - imgCenterX - panX) / totalScale + bitmap.width / 2f)
    fun viewToBmpY(vy: Float) = ((vy - imgCenterY - panY) / totalScale + bitmap.height / 2f)

    val bmpLeft = viewToBmpX(frameLeft).toInt().coerceIn(0, bitmap.width - 1)
    val bmpTop = viewToBmpY(frameTop).toInt().coerceIn(0, bitmap.height - 1)
    val bmpRight = viewToBmpX(frameLeft + frameW).toInt().coerceIn(bmpLeft + 1, bitmap.width)
    val bmpBottom = viewToBmpY(frameTop + frameH).toInt().coerceIn(bmpTop + 1, bitmap.height)

    val cropW = bmpRight - bmpLeft
    val cropH = bmpBottom - bmpTop

    Log.d(
      TAG,
      "cropAligned: view=${viewW}x${viewH} pan=$panX,$panY zoom=$zoom → bmp crop ($bmpLeft,$bmpTop ${cropW}x${cropH})"
    )

    return Bitmap.createBitmap(bitmap, bmpLeft, bmpTop, cropW, cropH)
  }
}
