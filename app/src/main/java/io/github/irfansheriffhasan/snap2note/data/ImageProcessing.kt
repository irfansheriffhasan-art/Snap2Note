package io.github.irfansheriffhasan.snap2note.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

internal object ImageProcessing {
  /**
   * Decodes an image so that its longest side is at most [maxSide] pixels and applies the
   * EXIF orientation, so camera photos are always upright. [open] is called once per pass.
   */
  fun decode(maxSide: Int, open: () -> InputStream?): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = open() ?: return null
    boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val longest = max(bounds.outWidth, bounds.outHeight)
    var sampleSize = 1
    while (longest / (sampleSize * 2) >= maxSide) sampleSize *= 2

    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val stream = open() ?: return null
    val decoded = stream.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
    val rotation = open()?.use(::readRotation) ?: 0
    return decoded.scaledToFit(maxSide).rotated(rotation)
  }

  private fun readRotation(stream: InputStream): Int =
    try {
      when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
      }
    } catch (e: Exception) {
      0
    }

  private fun Bitmap.scaledToFit(maxSide: Int): Bitmap {
    val longest = max(width, height)
    if (longest <= maxSide) return this
    val ratio = maxSide.toFloat() / longest
    val scaled = scale(
      (width * ratio).roundToInt().coerceAtLeast(1),
      (height * ratio).roundToInt().coerceAtLeast(1),
    )
    if (scaled !== this) recycle()
    return scaled
  }

  private fun Bitmap.rotated(degrees: Int): Bitmap {
    if (degrees == 0) return this
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    val rotated = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    if (rotated !== this) recycle()
    return rotated
  }
}
