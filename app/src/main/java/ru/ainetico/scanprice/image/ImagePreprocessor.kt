package ru.ainetico.scanprice.image

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.core.graphics.scale

class ImagePreprocessor {

  companion object {
    const val MIN_SHORT_SIDE = 1080
  }

  fun processBitmap(bitmap: Bitmap, cropRect: Rect?): Bitmap {
    val cropped = cropRect?.let { cropBitmap(bitmap, it) } ?: bitmap
    val result = downsample(cropped)
    if (result !== cropped && cropped !== bitmap) cropped.recycle()
    return result
  }

  fun calculateInSampleSize(width: Int, height: Int): Int {
    val shortSide = minOf(width, height)
    var inSampleSize = 1
    while (shortSide / (inSampleSize * 2) >= MIN_SHORT_SIDE) {
      inSampleSize *= 2
    }
    return inSampleSize
  }

  fun cropBitmap(bitmap: Bitmap, rect: Rect): Bitmap {
    val safeRect = Rect(
      rect.left.coerceIn(0, bitmap.width),
      rect.top.coerceIn(0, bitmap.height),
      rect.right.coerceIn(0, bitmap.width),
      rect.bottom.coerceIn(0, bitmap.height)
    )
    return Bitmap.createBitmap(
      bitmap,
      safeRect.left,
      safeRect.top,
      safeRect.width(),
      safeRect.height()
    )
  }

  fun bitmapToJpeg(bitmap: Bitmap, quality: Int): ByteArray {
    val stream = java.io.ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
    return stream.toByteArray()
  }

  fun downscaleToMaxSide(bitmap: Bitmap, maxSide: Int): Bitmap {
    val longer = maxOf(bitmap.width, bitmap.height)
    if (longer <= maxSide) return bitmap
    val scale = maxSide.toFloat() / longer
    return bitmap.scale(
      (bitmap.width * scale).toInt(),
      (bitmap.height * scale).toInt()
    )
  }

  private fun downsample(bitmap: Bitmap): Bitmap {
    val shortSide = minOf(bitmap.width, bitmap.height)
    if (shortSide <= MIN_SHORT_SIDE) return bitmap
    val scale = MIN_SHORT_SIDE.toFloat() / shortSide
    return bitmap.scale(
      (bitmap.width * scale).toInt(),
      (bitmap.height * scale).toInt()
    )
  }
}
