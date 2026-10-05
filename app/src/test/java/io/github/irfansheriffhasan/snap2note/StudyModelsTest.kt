package io.github.irfansheriffhasan.snap2note

import io.github.irfansheriffhasan.snap2note.ai.ChatTurn
import io.github.irfansheriffhasan.snap2note.ai.Flashcard
import io.github.irfansheriffhasan.snap2note.ai.FlashcardsJson
import io.github.irfansheriffhasan.snap2note.ui.ChatMessage
import io.github.irfansheriffhasan.snap2note.ui.StudyChatViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyModelsTest {
  @Test
  fun `flashcards survive a JSON round trip`() {
    val cards = listOf(Flashcard("What is ATP?", "The cell's energy currency"), Flashcard("Q2", "A2"))
    assertEquals(cards, FlashcardsJson.decode(FlashcardsJson.encode(cards)))
  }

  @Test
  fun `invalid flashcard JSON decodes to an empty list`() {
    assertEquals(emptyList<Flashcard>(), FlashcardsJson.decode("not json"))
    assertEquals(emptyList<Flashcard>(), FlashcardsJson.decode(null))
  }

  @Test
  fun `failed chat turns are not sent back to the model`() {
    val messages = listOf(
      ChatMessage("Explain osmosis", fromUser = true),
      ChatMessage("Osmosis is...", fromUser = false),
      ChatMessage("Quiz me", fromUser = true),
      ChatMessage("No internet connection", fromUser = false, isError = true),
      ChatMessage("Quiz me again", fromUser = true),
      ChatMessage("Question 1...", fromUser = false),
    )

    assertEquals(
      listOf(
        ChatTurn("Explain osmosis", fromUser = true),
        ChatTurn("Osmosis is...", fromUser = false),
        ChatTurn("Quiz me again", fromUser = true),
        ChatTurn("Question 1...", fromUser = false),
      ),
      StudyChatViewModel.completedTurns(messages),
    )
  }
}
