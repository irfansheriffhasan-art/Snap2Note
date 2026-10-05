package io.github.irfansheriffhasan.snap2note.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.createBitmap
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

  /**
   * Returns false for a JPEG that is missing its end-of-image marker. Some camera apps report
   * success before they finish writing the photo, and decoding such a file fills the missing
   * rows with black. Other formats are assumed complete.
   */
  fun isCompleteImage(bytes: ByteArray): Boolean {
    if (bytes.isEmpty()) return false
    val isJpeg = bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
    if (!isJpeg) return true
    // Some encoders pad the file after the marker, so skip trailing zero bytes.
    var end = bytes.lastIndex
    while (end > 1 && bytes[end] == 0.toByte()) end--
    return bytes[end - 1] == 0xFF.toByte() && bytes[end] == 0xD9.toByte()
  }

  /** Returns an opaque copy with transparent areas painted white, since JPEG has no alpha. */
  fun Bitmap.flattenedOnWhite(): Bitmap {
    if (!hasAlpha()) return this
    val flat = createBitmap(width, height)
    Canvas(flat).apply {
      drawColor(Color.WHITE)
      drawBitmap(this@flattenedOnWhite, 0f, 0f, null)
    }
    recycle()
    return flat
  }

  /** Rotates by a multiple of 90 degrees and optionally mirrors horizontally. */
  fun Bitmap.oriented(rotation: Int, flipHorizontal: Boolean): Bitmap {
    if (rotation % 360 == 0 && !flipHorizontal) return this
    val matrix = Matrix().apply {
      if (flipHorizontal) postScale(-1f, 1f)
      postRotate(rotation.toFloat())
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
  }

  /** Applies [edits] to a page image and returns a new bitmap. [source] is left untouched. */
  fun applyEdits(source: Bitmap, edits: PageEdits): Bitmap {
    val oriented = source.oriented(edits.rotation, edits.flipHorizontal)
    val crop = edits.crop
    val left = (crop.left * oriented.width).roundToInt().coerceIn(0, oriented.width - 1)
    val top = (crop.top * oriented.height).roundToInt().coerceIn(0, oriented.height - 1)
    val right = (crop.right * oriented.width).roundToInt().coerceIn(left + 1, oriented.width)
    val bottom = (crop.bottom * oriented.height).roundToInt().coerceIn(top + 1, oriented.height)

    val output = createBitmap(right - left, bottom - top)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
      colorFilter = ColorMatrixColorFilter(android.graphics.ColorMatrix(edits.colorMatrix()))
    }
    Canvas(output).apply {
      drawColor(Color.WHITE)
      drawBitmap(oriented, Rect(left, top, right, bottom), Rect(0, 0, output.width, output.height), paint)
    }
    if (oriented !== source) oriented.recycle()
    return output
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
