package io.github.irfansheriffhasan.snap2note

import io.github.irfansheriffhasan.snap2note.data.CropRect
import io.github.irfansheriffhasan.snap2note.data.ImageProcessing
import io.github.irfansheriffhasan.snap2note.data.PageEdits
import io.github.irfansheriffhasan.snap2note.data.PageFilter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageEditsTest {
  private val crop = CropRect(0.1f, 0.2f, 0.6f, 0.9f)

  @Test
  fun `four quarter turns bring back the original edits`() {
    val edits = PageEdits(crop = crop)
    val turned = edits.rotatedClockwise().rotatedClockwise().rotatedClockwise().rotatedClockwise()
    assertEquals(0, turned.rotation)
    assertCropEquals(crop, turned.crop)
  }

  @Test
  fun `rotating one way and back is a no-op`() {
    val edits = PageEdits(crop = crop).rotatedClockwise().rotatedCounterClockwise()
    assertEquals(0, edits.rotation)
    assertCropEquals(crop, edits.crop)
  }

  @Test
  fun `rotating clockwise moves the crop with the image`() {
    // The top-left corner of the image ends up at the top-right after a clockwise turn.
    val rotated = CropRect(0f, 0f, 0.5f, 0.25f).rotatedClockwise()
    assertCropEquals(CropRect(0.75f, 0f, 1f, 0.5f), rotated)
  }

  @Test
  fun `flipping twice is a no-op`() {
    val edits = PageEdits(rotation = 90, crop = crop).flipped().flipped()
    assertEquals(PageEdits(rotation = 90, crop = crop).rotation, edits.rotation)
    assertFalse(edits.flipHorizontal)
    assertCropEquals(crop, edits.crop)
  }

  @Test
  fun `default edits leave colors unchanged`() {
    assertTrue(PageEdits().isUnchanged)
    assertFalse(PageEdits().hasColorChanges)
    assertArrayEquals(PageEdits.identity(), PageEdits().colorMatrix(), 1e-6f)
  }

  @Test
  fun `grayscale gives equal channels`() {
    val gray = apply(PageEdits(filter = PageFilter.GRAYSCALE).colorMatrix(), 200f, 40f, 90f)
    assertEquals(gray[0], gray[1], 0.01f)
    assertEquals(gray[1], gray[2], 0.01f)
  }

  @Test
  fun `brightness and contrast move colors the right way`() {
    val brighter = apply(PageEdits(brightness = 0.5f).colorMatrix(), 100f, 100f, 100f)
    assertTrue(brighter[0] > 100f)
    val contrasted = apply(PageEdits(contrast = 0.5f).colorMatrix(), 200f, 200f, 200f)
    assertTrue(contrasted[0] > 200f)
  }

  @Test
  fun `concat applies the second matrix first`() {
    val brighten = PageEdits(brightness = 0.25f).colorMatrix()
    val gray = PageEdits.filterMatrix(PageFilter.GRAYSCALE)
    val combined = PageEdits.concat(brighten, gray)
    val grayed = apply(gray, 10f, 20f, 30f)
    val expected = apply(brighten, grayed[0], grayed[1], grayed[2])
    assertArrayEquals(expected, apply(combined, 10f, 20f, 30f), 0.01f)
  }

  @Test
  fun `detects a JPEG that is still being written`() {
    val complete = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())
    val padded = complete + byteArrayOf(0, 0, 0)
    val truncated = complete.copyOf(5)
    assertTrue(ImageProcessing.isCompleteImage(complete))
    assertTrue(ImageProcessing.isCompleteImage(padded))
    assertFalse(ImageProcessing.isCompleteImage(truncated))
    assertFalse(ImageProcessing.isCompleteImage(ByteArray(0)))
  }

  @Test
  fun `other formats are treated as complete`() {
    val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
    assertTrue(ImageProcessing.isCompleteImage(png))
  }

  private fun apply(matrix: FloatArray, r: Float, g: Float, b: Float): FloatArray =
    FloatArray(3) { row -> matrix[row * 5] * r + matrix[row * 5 + 1] * g + matrix[row * 5 + 2] * b + matrix[row * 5 + 4] }

  private fun assertCropEquals(expected: CropRect, actual: CropRect) {
    assertEquals(expected.left, actual.left, 1e-5f)
    assertEquals(expected.top, actual.top, 1e-5f)
    assertEquals(expected.right, actual.right, 1e-5f)
    assertEquals(expected.bottom, actual.bottom, 1e-5f)
  }
}
