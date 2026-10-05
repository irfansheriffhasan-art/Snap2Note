package io.github.irfansheriffhasan.snap2note.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** The subset of Markdown that Gemini typically returns: headings, lists and paragraphs. */
sealed interface MarkdownBlock {
  data class Heading(val text: String) : MarkdownBlock

  data class Paragraph(val text: String) : MarkdownBlock

  data class ListItem(val marker: String, val text: String, val level: Int) : MarkdownBlock
}

private val HeadingPattern = Regex("^#{1,6}\\s+(.*)$")
private val BulletPattern = Regex("^[-*•]\\s+(.*)$")
private val NumberedPattern = Regex("^(\\d+)[.)]\\s+(.*)$")
private val RulePattern = Regex("^([-*_])\\1{2,}$")

fun parseMarkdown(source: String): List<MarkdownBlock> {
  val blocks = mutableListOf<MarkdownBlock>()
  val paragraph = StringBuilder()

  fun flushParagraph() {
    if (paragraph.isNotEmpty()) {
      blocks += MarkdownBlock.Paragraph(paragraph.toString())
      paragraph.clear()
    }
  }

  for (rawLine in source.lines()) {
    val line = rawLine.trimEnd()
    val trimmed = line.trimStart()
    val level = (line.length - trimmed.length) / 2
    val heading = HeadingPattern.find(trimmed)
    val bullet = BulletPattern.find(trimmed)
    val numbered = NumberedPattern.find(trimmed)
    when {
      trimmed.isEmpty() || RulePattern.matches(trimmed) -> flushParagraph()
      heading != null -> {
        flushParagraph()
        blocks += MarkdownBlock.Heading(heading.groupValues[1].trimEnd('#', ' '))
      }
      bullet != null -> {
        flushParagraph()
        blocks += MarkdownBlock.ListItem("•", bullet.groupValues[1], level)
      }
      numbered != null -> {
        flushParagraph()
        blocks += MarkdownBlock.ListItem("${numbered.groupValues[1]}.", numbered.groupValues[2], level)
      }
      else -> {
        if (paragraph.isNotEmpty()) paragraph.append('\n')
        paragraph.append(trimmed)
      }
    }
  }
  flushParagraph()
  return blocks
}

/** Applies **bold**, *italic* and `code` spans and removes their markers. */
fun inlineMarkdown(text: String, codeStyle: SpanStyle = SpanStyle(fontFamily = FontFamily.Monospace)): AnnotatedString =
  buildAnnotatedString {
    var i = 0
    while (i < text.length) {
      val char = text[i]
      when {
        text.startsWith("**", i) -> {
          val end = text.indexOf("**", i + 2)
          if (end > i + 2) {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(inlineMarkdown(text.substring(i + 2, end), codeStyle)) }
            i = end + 2
          } else {
            append("**")
            i += 2
          }
        }
        char == '`' -> {
          val end = text.indexOf('`', i + 1)
          if (end > i + 1) {
            withStyle(codeStyle) { append(text.substring(i + 1, end)) }
            i = end + 1
          } else {
            append(char)
            i++
          }
        }
        char == '*' && i + 1 < text.length && !text[i + 1].isWhitespace() -> {
          val end = text.indexOf('*', i + 1)
          if (end > i + 1 && !text[end - 1].isWhitespace()) {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
            i = end + 1
          } else {
            append(char)
            i++
          }
        }
        else -> {
          append(char)
          i++
        }
      }
    }
  }

@Composable
fun MarkdownText(
  markdown: String,
  modifier: Modifier = Modifier,
  style: TextStyle = MaterialTheme.typography.bodyMedium,
  color: Color = MaterialTheme.colorScheme.onSurface,
) {
  val blocks = remember(markdown) { parseMarkdown(markdown) }
  val codeStyle = SpanStyle(
    fontFamily = FontFamily.Monospace,
    background = MaterialTheme.colorScheme.surfaceContainerHighest,
  )
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    blocks.forEach { block ->
      when (block) {
        is MarkdownBlock.Heading ->
          Text(inlineMarkdown(block.text, codeStyle), style = MaterialTheme.typography.titleSmall, color = color)
        is MarkdownBlock.Paragraph ->
          Text(inlineMarkdown(block.text, codeStyle), style = style, color = color)
        is MarkdownBlock.ListItem ->
          Row(modifier = Modifier.padding(start = (block.level * 16).dp)) {
            Text(block.marker, style = style, color = color, modifier = Modifier.widthIn(min = 18.dp).padding(end = 6.dp))
            Text(inlineMarkdown(block.text, codeStyle), style = style, color = color, modifier = Modifier.weight(1f))
          }
      }
    }
  }
}
