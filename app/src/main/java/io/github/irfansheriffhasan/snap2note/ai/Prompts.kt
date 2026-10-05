package io.github.irfansheriffhasan.snap2note.ai

internal object Prompts {
  val PAGE_ANALYSIS = """
    This image is a page of study material: handwritten notes, a printed handout, a textbook page, slides, or a whiteboard.
    Return a JSON object with these fields:
    - "title": a short, specific title for the page (2 to 6 words).
    - "text": a faithful transcription of all readable text. Keep the reading order, headings, lists and line breaks. Write equations in plain text.
    - "summary": a clear explanation of the key ideas for a student revising this material. Use short paragraphs and "- " bullet points, and **bold** for key terms. Do not use LaTeX.
    - "flashcards": 3 to 6 question-and-answer pairs that test the most important points on the page.
    If the image has no readable study content, set "text" to an empty string and say so briefly in "summary".
  """.trimIndent()

  val PAGE_ANALYSIS_SCHEMA: Map<String, Any> = mapOf(
    "type" to "OBJECT",
    "properties" to mapOf(
      "title" to mapOf("type" to "STRING"),
      "text" to mapOf("type" to "STRING"),
      "summary" to mapOf("type" to "STRING"),
      "flashcards" to mapOf(
        "type" to "ARRAY",
        "items" to mapOf(
          "type" to "OBJECT",
          "properties" to mapOf(
            "question" to mapOf("type" to "STRING"),
            "answer" to mapOf("type" to "STRING"),
          ),
          "required" to listOf("question", "answer"),
        ),
      ),
    ),
    "required" to listOf("title", "text", "summary", "flashcards"),
    "propertyOrdering" to listOf("title", "text", "summary", "flashcards"),
  )

  fun studyAssistant(folderName: String, notes: String): String {
    val instructions = """
      You are the study assistant in Snap2Note, helping a student revise the notes in their folder "$folderName".
      Base your answers on the notes below. If the notes don't cover something, say so briefly, then answer from general knowledge.
      Keep answers concise and well structured. Use "- " bullet points and **bold** for key terms where helpful. Do not use LaTeX; write formulas in plain text.
      When asked for a quiz, ask the questions first and only reveal the answers when the student asks for them.
    """.trimIndent()
    val notesSection = if (notes.isBlank()) {
      "The student hasn't analyzed any pages in this folder yet, so you don't have their notes. " +
        "Answer from general knowledge and mention that analyzing pages gives answers based on their own material."
    } else {
      "Notes:\n$notes"
    }
    return "$instructions\n\n$notesSection"
  }
}
