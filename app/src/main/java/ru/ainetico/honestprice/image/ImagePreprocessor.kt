package ru.ainetico.honestprice.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import androidx.exifinterface.media.ExifInterface

class ImagePreprocessor {

    companion object {
        const val MIN_SHORT_SIDE = 1080
    }

    fun processFile(filePath: String, cropRect: Rect?): Bitmap {
        val (width, height) = getImageDimensions(filePath)
        val inSampleSize = calculateInSampleSize(width, height)
        val options = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
        val bitmap = BitmapFactory.decodeFile(filePath, options)
        val rotated = applyExifRotation(bitmap, filePath)
        return cropRect?.let { cropBitmap(rotated, it) } ?: rotated
    }

    fun processBitmap(bitmap: Bitmap, cropRect: Rect?): Bitmap {
        val cropped = cropRect?.let { cropBitmap(bitmap, it) } ?: bitmap
        return downsample(cropped)
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
        return Bitmap.createBitmap(bitmap, safeRect.left, safeRect.top, safeRect.width(), safeRect.height())
    }

    private fun getImageDimensions(filePath: String): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, options)
        return options.outWidth to options.outHeight
    }

    private fun applyExifRotation(bitmap: Bitmap, filePath: String): Bitmap {
        val exif = ExifInterface(filePath)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
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
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt(),
            (bitmap.height * scale).toInt(),
            true
        )
    }

    private fun downsample(bitmap: Bitmap): Bitmap {
        val shortSide = minOf(bitmap.width, bitmap.height)
        if (shortSide <= MIN_SHORT_SIDE) return bitmap
        val scale = MIN_SHORT_SIDE.toFloat() / shortSide
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
    }
}
