package io.github.irfansheriffhasan.snap2note.ai

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

@JsonClass(generateAdapter = true)
data class Flashcard(
  val question: String,
  val answer: String,
)

/** The structured result of analyzing a single page. */
data class PageAnalysis(
  val title: String,
  val text: String,
  val summary: String,
  val flashcards: List<Flashcard>,
)

/** One turn of a study assistant conversation. */
data class ChatTurn(
  val text: String,
  val fromUser: Boolean,
)

/** Raw JSON shape returned by the page analysis prompt. */
@JsonClass(generateAdapter = true)
data class PageAnalysisJson(
  val title: String? = null,
  val text: String? = null,
  val summary: String? = null,
  val flashcards: List<Flashcard>? = null,
)

/** A user-facing error from the Gemini API. [stopsBatch] is set for key and quota problems. */
class GeminiException(message: String, val stopsBatch: Boolean = false) : Exception(message)

object FlashcardsJson {
  private val adapter: JsonAdapter<List<Flashcard>> = Moshi.Builder().build()
    .adapter(Types.newParameterizedType(List::class.java, Flashcard::class.java))

  fun encode(cards: List<Flashcard>): String = adapter.toJson(cards)

  fun decode(json: String?): List<Flashcard> =
    if (json.isNullOrBlank()) emptyList() else runCatching { adapter.fromJson(json) }.getOrNull().orEmpty()
}
