package io.github.irfansheriffhasan.snap2note

import io.github.irfansheriffhasan.snap2note.data.safeFileName
import org.junit.Assert.assertEquals
import org.junit.Test

class FileNamesTest {
  @Test
  fun `keeps readable names`() {
    assertEquals("Physics 101", safeFileName("Physics 101"))
    assertEquals("Biología", safeFileName("Biología"))
  }

  @Test
  fun `replaces path separators and other unsafe characters`() {
    assertEquals("Maths_Stats", safeFileName("Maths/Stats"))
    assertEquals("a_b_c", safeFileName("a:b*c"))
  }

  @Test
  fun `falls back when nothing usable is left`() {
    assertEquals("Snap2Note", safeFileName("///"))
    assertEquals("Snap2Note", safeFileName("   "))
  }

  @Test
  fun `limits the length`() {
    assertEquals(60, safeFileName("x".repeat(200)).length)
  }
}
