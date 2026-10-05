package io.github.irfansheriffhasan.snap2note.data

/** A crop rectangle in fractions (0 to 1) of the image after rotation and flipping. */
data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
  val width get() = right - left
  val height get() = bottom - top

  /** The same region after the image is turned 90 degrees clockwise. */
  fun rotatedClockwise() = CropRect(left = 1f - bottom, top = left, right = 1f - top, bottom = right)

  /** The same region after the image is turned 90 degrees counter-clockwise. */
  fun rotatedCounterClockwise() = CropRect(left = top, top = 1f - right, right = bottom, bottom = 1f - left)

  /** The same region after the image is mirrored horizontally. */
  fun flippedHorizontally() = copy(left = 1f - right, right = 1f - left)

  companion object {
    val Full = CropRect(0f, 0f, 1f, 1f)
  }
}

enum class PageFilter(val label: String) {
  ORIGINAL("Original"),
  DOCUMENT("Document"),
  GRAYSCALE("Grayscale"),
  BLACK_WHITE("B&W"),
  SEPIA("Sepia"),
  VIVID("Vivid"),
  COOL("Cool"),
}

/**
 * Everything the page editor can change. Adjustments range from -1 to 1, with 0 meaning
 * unchanged. Rotation is applied first, then the crop, then the filter and adjustments.
 */
data class PageEdits(
  val rotation: Int = 0,
  val flipHorizontal: Boolean = false,
  val crop: CropRect = CropRect.Full,
  val filter: PageFilter = PageFilter.ORIGINAL,
  val brightness: Float = 0f,
  val contrast: Float = 0f,
  val saturation: Float = 0f,
  val warmth: Float = 0f,
) {
  val isUnchanged get() = this == PageEdits()

  val hasColorChanges
    get() = filter != PageFilter.ORIGINAL || brightness != 0f || contrast != 0f || saturation != 0f || warmth != 0f

  fun rotatedClockwise() = copy(rotation = (rotation + 90) % 360, crop = crop.rotatedClockwise())

  fun rotatedCounterClockwise() = copy(rotation = (rotation + 270) % 360, crop = crop.rotatedCounterClockwise())

  // Mirroring after a quarter turn is the same as mirroring vertically before it, so the
  // rotation is mirrored too to keep the result matching what the user sees.
  fun flipped() = copy(
    flipHorizontal = !flipHorizontal,
    rotation = (360 - rotation) % 360,
    crop = crop.flippedHorizontally(),
  )

  /**
   * The combined color change as a 4x5 row-major matrix, in the layout used by both
   * android.graphics.ColorMatrix and Compose's ColorMatrix (offsets on a 0 to 255 scale).
   */
  fun colorMatrix(): FloatArray {
    var matrix = filterMatrix(filter)
    if (brightness != 0f) matrix = concat(offsetMatrix(brightness * 80f, brightness * 80f, brightness * 80f), matrix)
    if (contrast != 0f) matrix = concat(contrastMatrix(1f + if (contrast > 0) contrast else contrast * 0.7f), matrix)
    if (saturation != 0f) matrix = concat(saturationMatrix(1f + saturation), matrix)
    if (warmth != 0f) matrix = concat(offsetMatrix(warmth * 30f, warmth * 8f, -warmth * 30f), matrix)
    return matrix
  }

  companion object {
    fun filterMatrix(filter: PageFilter): FloatArray = when (filter) {
      PageFilter.ORIGINAL -> identity()
      // Lifts the paper towards white and darkens the ink.
      PageFilter.DOCUMENT -> concat(offsetMatrix(18f, 18f, 18f), concat(contrastMatrix(1.45f), saturationMatrix(0.9f)))
      PageFilter.GRAYSCALE -> saturationMatrix(0f)
      PageFilter.BLACK_WHITE -> concat(offsetMatrix(30f, 30f, 30f), concat(contrastMatrix(2.3f), saturationMatrix(0f)))
      PageFilter.SEPIA -> floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
      )
      PageFilter.VIVID -> concat(contrastMatrix(1.15f), saturationMatrix(1.5f))
      PageFilter.COOL -> concat(offsetMatrix(-12f, 4f, 22f), saturationMatrix(0.95f))
    }

    fun identity() = floatArrayOf(
      1f, 0f, 0f, 0f, 0f,
      0f, 1f, 0f, 0f, 0f,
      0f, 0f, 1f, 0f, 0f,
      0f, 0f, 0f, 1f, 0f,
    )

    private fun offsetMatrix(r: Float, g: Float, b: Float) = floatArrayOf(
      1f, 0f, 0f, 0f, r,
      0f, 1f, 0f, 0f, g,
      0f, 0f, 1f, 0f, b,
      0f, 0f, 0f, 1f, 0f,
    )

    /** Scales colors around mid-gray, so 1 leaves them unchanged. */
    private fun contrastMatrix(scale: Float): FloatArray {
      val offset = 128f * (1f - scale)
      return floatArrayOf(
        scale, 0f, 0f, 0f, offset,
        0f, scale, 0f, 0f, offset,
        0f, 0f, scale, 0f, offset,
        0f, 0f, 0f, 1f, 0f,
      )
    }

    /** 0 gives grayscale, 1 leaves colors unchanged and higher values intensify them. */
    private fun saturationMatrix(saturation: Float): FloatArray {
      val inv = 1f - saturation
      val r = 0.2126f * inv
      val g = 0.7152f * inv
      val b = 0.0722f * inv
      return floatArrayOf(
        r + saturation, g, b, 0f, 0f,
        r, g + saturation, b, 0f, 0f,
        r, g, b + saturation, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
      )
    }

    /** Returns the matrix that applies [second] and then [first]. */
    fun concat(first: FloatArray, second: FloatArray): FloatArray {
      val out = FloatArray(20)
      for (row in 0 until 4) {
        for (col in 0 until 5) {
          var value = if (col == 4) first[row * 5 + 4] else 0f
          for (k in 0 until 4) value += first[row * 5 + k] * second[k * 5 + col]
          out[row * 5 + col] = value
        }
      }
      return out
    }
  }
}
