package io.github.irfansheriffhasan.snap2note.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.irfansheriffhasan.snap2note.Snap2NoteApplication
import io.github.irfansheriffhasan.snap2note.ai.ChatTurn
import io.github.irfansheriffhasan.snap2note.ai.GeminiClient
import io.github.irfansheriffhasan.snap2note.data.FolderWithStats
import io.github.irfansheriffhasan.snap2note.data.NoteRepository
import io.github.irfansheriffhasan.snap2note.data.Page
import io.github.irfansheriffhasan.snap2note.data.isAnalyzed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(
  val text: String,
  val fromUser: Boolean,
  val isError: Boolean = false,
)

/** Conversation with the study assistant about the notes in one folder. */
class StudyChatViewModel(
  private val repository: NoteRepository,
  private val gemini: GeminiClient,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {
  private val folderId: Long = checkNotNull(savedStateHandle[Routes.ARG_FOLDER_ID])

  val folder: StateFlow<FolderWithStats?> = repository.folders
    .map { folders -> folders.firstOrNull { it.id == folderId } }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
  val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

  private val _isSending = MutableStateFlow(false)
  val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

  fun send(text: String) {
    val message = text.trim()
    if (message.isEmpty() || _isSending.value) return

    val history = completedTurns(_messages.value)
    _messages.update { it + ChatMessage(message, fromUser = true) }
    _isSending.value = true

    viewModelScope.launch {
      try {
        val notes = buildNotesContext(repository.getPages(folderId))
        val folderName = folder.value?.name.orEmpty()
        val reply = gemini.chat(folderName, notes, history, message)
        _messages.update { messages ->
          messages + reply.fold(
            onSuccess = { ChatMessage(it, fromUser = false) },
            onFailure = { ChatMessage(it.message ?: "Something went wrong.", fromUser = false, isError = true) },
          )
        }
      } finally {
        _isSending.value = false
      }
    }
  }

  fun clear() {
    if (!_isSending.value) _messages.value = emptyList()
  }

  private fun buildNotesContext(pages: List<Page>): String =
    pages.withIndex()
      .filter { (_, page) -> page.isAnalyzed }
      .joinToString("\n\n") { (index, page) ->
        buildString {
          append("## Page ${index + 1}: ${page.title ?: "Untitled"}\n")
          page.extractedText?.takeIf { it.isNotBlank() }?.let { append(it).append("\n") }
          page.summary?.takeIf { it.isNotBlank() }?.let { append("\nSummary:\n").append(it) }
        }
      }
      .take(MAX_NOTES_CHARS)

  companion object {
    private const val MAX_NOTES_CHARS = 400_000

    /** Question/answer pairs that completed successfully, so failed turns aren't sent back to the model. */
    internal fun completedTurns(messages: List<ChatMessage>): List<ChatTurn> =
      messages.zipWithNext()
        .filter { (question, answer) -> question.fromUser && !answer.fromUser && !answer.isError }
        .flatMap { (question, answer) -> listOf(ChatTurn(question.text, true), ChatTurn(answer.text, false)) }

    val Factory = viewModelFactory {
      initializer {
        val app = this[APPLICATION_KEY] as Snap2NoteApplication
        StudyChatViewModel(app.repository, app.gemini, createSavedStateHandle())
      }
    }
  }
}
