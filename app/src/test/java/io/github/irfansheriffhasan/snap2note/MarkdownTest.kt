package io.github.irfansheriffhasan.snap2note

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import io.github.irfansheriffhasan.snap2note.ui.components.MarkdownBlock
import io.github.irfansheriffhasan.snap2note.ui.components.inlineMarkdown
import io.github.irfansheriffhasan.snap2note.ui.components.parseMarkdown
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTest {
  @Test
  fun `parses headings, lists and paragraphs`() {
    val blocks = parseMarkdown(
      """
      ## Photosynthesis
      Plants turn light into
      chemical energy.

      - Happens in **chloroplasts**
        - Uses chlorophyll
      1. Light reactions
      2) Calvin cycle
      ---
      """.trimIndent(),
    )

    assertEquals(
      listOf(
        MarkdownBlock.Heading("Photosynthesis"),
        MarkdownBlock.Paragraph("Plants turn light into\nchemical energy."),
        MarkdownBlock.ListItem("•", "Happens in **chloroplasts**", level = 0),
        MarkdownBlock.ListItem("•", "Uses chlorophyll", level = 1),
        MarkdownBlock.ListItem("1.", "Light reactions", level = 0),
        MarkdownBlock.ListItem("2.", "Calvin cycle", level = 0),
      ),
      blocks,
    )
  }

  @Test
  fun `bold text at the start of a line is not a bullet`() {
    assertEquals(listOf(MarkdownBlock.Paragraph("**Key idea:** energy")), parseMarkdown("**Key idea:** energy"))
  }

  @Test
  fun `inline markers are removed and styled`() {
    val text = inlineMarkdown("The **mitochondria** is *the* powerhouse of `cell`")

    assertEquals("The mitochondria is the powerhouse of cell", text.text)
    val bold = text.spanStyles.single { it.item.fontWeight == FontWeight.SemiBold }
    assertEquals("mitochondria", text.text.substring(bold.start, bold.end))
    val italic = text.spanStyles.single { it.item.fontStyle == FontStyle.Italic }
    assertEquals("the", text.text.substring(italic.start, italic.end))
  }

  @Test
  fun `unmatched markers are kept as text`() {
    assertEquals("2 * 3 = 6 and a ** b", inlineMarkdown("2 * 3 = 6 and a ** b").text)
  }
}
