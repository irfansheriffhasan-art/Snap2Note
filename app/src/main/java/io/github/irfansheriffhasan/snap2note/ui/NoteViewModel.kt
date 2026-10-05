package io.github.irfansheriffhasan.snap2note.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.irfansheriffhasan.snap2note.Snap2NoteApplication
import io.github.irfansheriffhasan.snap2note.ai.FlashcardsJson
import io.github.irfansheriffhasan.snap2note.ai.GeminiClient
import io.github.irfansheriffhasan.snap2note.ai.GeminiException
import io.github.irfansheriffhasan.snap2note.data.AddPagesResult
import io.github.irfansheriffhasan.snap2note.data.FolderWithStats
import io.github.irfansheriffhasan.snap2note.data.NoteRepository
import io.github.irfansheriffhasan.snap2note.data.Page
import io.github.irfansheriffhasan.snap2note.data.PageEdits
import io.github.irfansheriffhasan.snap2note.data.SettingsRepository
import io.github.irfansheriffhasan.snap2note.data.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** App-wide state: folders, pages, page analysis, export and settings. */
class NoteViewModel(
  private val repository: NoteRepository,
  private val settings: SettingsRepository,
  private val gemini: GeminiClient,
  private val savedState: SavedStateHandle,
) : ViewModel() {

  /** All folders, or null until the first database load completes. */
  val folders: StateFlow<List<FolderWithStats>?> =
    repository.folders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  val searchResults: StateFlow<List<FolderWithStats>?> =
    combine(folders, _searchQuery) { list, query ->
      if (list == null || query.isBlank()) list else list.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  val themeMode: StateFlow<ThemeMode> = settings.themeMode
  val apiKey: StateFlow<String> = settings.apiKey

  private val messageChannel = Channel<String>(Channel.BUFFERED)

  /** One-off messages to show to the user. */
  val messages: Flow<String> = messageChannel.receiveAsFlow()

  /** Captured or imported images waiting for the user to pick a folder. */
  val pendingUris: StateFlow<List<Uri>> = savedState.getStateFlow(KEY_PENDING_URIS, emptyList())

  private val _isSaving = MutableStateFlow(false)
  val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

  private val _isExporting = MutableStateFlow(false)
  val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

  private val _analyzingPageIds = MutableStateFlow<Set<Long>>(emptySet())
  val analyzingPageIds: StateFlow<Set<Long>> = _analyzingPageIds.asStateFlow()

  private val _analysisErrors = MutableStateFlow<Map<Long, String>>(emptyMap())
  val analysisErrors: StateFlow<Map<Long, String>> = _analysisErrors.asStateFlow()

  private val _storageUsedBytes = MutableStateFlow<Long?>(null)
  val storageUsedBytes: StateFlow<Long?> = _storageUsedBytes.asStateFlow()

  private val pageFlows = mutableMapOf<Long, StateFlow<List<Page>?>>()

  fun setSearchQuery(query: String) {
    _searchQuery.value = query
  }

  /** Pages of a folder in reading order, or null until loaded. */
  fun pages(folderId: Long): StateFlow<List<Page>?> =
    pageFlows.getOrPut(folderId) {
      repository.observePages(folderId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }

  fun pageFile(page: Page): File = repository.pageFile(page)

  fun coverFile(folder: FolderWithStats): File? = folder.coverFileName?.let(repository::pageFile)

  fun pageUris(pages: List<Page>): List<Uri> = pages.map { repository.contentUri(repository.pageFile(it)) }

  // region Folders

  fun createFolder(name: String, onCreated: (Long) -> Unit = {}) {
    viewModelScope.launch { onCreated(repository.createFolder(name)) }
  }

  fun renameFolder(folderId: Long, name: String) {
    viewModelScope.launch { repository.renameFolder(folderId, name) }
  }

  fun deleteFolder(folderId: Long) {
    viewModelScope.launch {
      repository.deleteFolder(folderId)
      refreshStorageUsed()
    }
  }

  // endregion

  // region Adding pages

  fun newCaptureUri(): Uri = repository.newCaptureUri()

  fun setPendingUris(uris: List<Uri>) {
    savedState[KEY_PENDING_URIS] = ArrayList(uris)
  }

  fun discardPendingUris() {
    pendingUris.value.forEach(repository::discardCapture)
    setPendingUris(emptyList())
  }

  fun savePendingTo(folderId: Long, onSaved: () -> Unit) {
    val uris = pendingUris.value
    if (uris.isEmpty() || _isSaving.value) return
    _isSaving.value = true
    viewModelScope.launch {
      try {
        report(repository.addPages(folderId, uris))
        setPendingUris(emptyList())
        onSaved()
      } finally {
        _isSaving.value = false
      }
    }
  }

  fun savePendingToNewFolder(name: String, onSaved: (Long) -> Unit) {
    val uris = pendingUris.value
    if (uris.isEmpty() || _isSaving.value) return
    _isSaving.value = true
    viewModelScope.launch {
      try {
        val folderId = repository.createFolder(name)
        report(repository.addPages(folderId, uris))
        setPendingUris(emptyList())
        onSaved(folderId)
      } finally {
        _isSaving.value = false
      }
    }
  }

  fun addPages(folderId: Long, uris: List<Uri>, onSaved: (List<Long>) -> Unit = {}) {
    if (uris.isEmpty()) return
    viewModelScope.launch {
      val result = repository.addPages(folderId, uris)
      report(result)
      onSaved(result.pageIds)
    }
  }

  fun discardCapture(uri: Uri) = repository.discardCapture(uri)

  private fun report(result: AddPagesResult) {
    refreshStorageUsed()
    if (result.failedCount > 0) {
      val images = if (result.failedCount == 1) "1 image" else "${result.failedCount} images"
      showMessage("Couldn't import $images.")
    }
  }

  // endregion

  // region Editing pages

  fun deletePages(pages: List<Page>) {
    if (pages.isEmpty()) return
    viewModelScope.launch {
      repository.deletePages(pages)
      refreshStorageUsed()
    }
  }

  fun movePages(pages: List<Page>, folderId: Long, folderName: String) {
    if (pages.isEmpty()) return
    viewModelScope.launch {
      repository.movePages(pages.map { it.id }, folderId)
      showMessage(if (pages.size == 1) "Moved to $folderName" else "Moved ${pages.size} pages to $folderName")
    }
  }

  fun renamePage(page: Page, title: String) {
    viewModelScope.launch { repository.renamePage(page.id, title) }
  }

  suspend fun loadPageBitmap(page: Page, maxSide: Int): Bitmap? = repository.loadPageBitmap(page, maxSide)

  fun editPage(page: Page, edits: PageEdits, onDone: (Boolean) -> Unit) {
    viewModelScope.launch {
      val saved = repository.editPage(page, edits)
      if (saved) refreshStorageUsed() else showMessage("Couldn't save the edited page.")
      onDone(saved)
    }
  }

  // endregion

  // region Analysis

  /**
   * Analyzes the pages one after another and stops early on key or quota errors.
   * Errors are always kept per page; [reportErrors] also shows them as a message, for
   * callers that don't display the per-page error themselves.
   */
  fun analyze(pages: List<Page>, reportErrors: Boolean = false) {
    val queued = pages.filter { it.id !in _analyzingPageIds.value }
    if (queued.isEmpty()) return
    val queuedIds = queued.map { it.id }.toSet()
    _analyzingPageIds.update { it + queuedIds }
    _analysisErrors.update { it - queuedIds }

    viewModelScope.launch {
      var failedCount = 0
      var lastError: String? = null
      try {
        for (page in queued) {
          val result = gemini.analyzePage(repository.pageFile(page))
          result
            .onSuccess { analysis ->
              repository.saveAnalysis(page.id, analysis, FlashcardsJson.encode(analysis.flashcards))
            }
            .onFailure { error ->
              val message = error.message ?: "Analysis failed."
              _analysisErrors.update { it + (page.id to message) }
              failedCount++
              lastError = message
            }
          _analyzingPageIds.update { it - page.id }
          if ((result.exceptionOrNull() as? GeminiException)?.stopsBatch == true) break
        }
      } finally {
        _analyzingPageIds.update { it - queuedIds }
      }
      val error = lastError
      if (reportErrors && error != null) {
        showMessage(if (failedCount == 1) error else "Couldn't analyze $failedCount pages. $error")
      }
    }
  }

  // endregion

  // region Export

  fun exportPdf(folderId: Long, folderName: String, onReady: (Uri) -> Unit) {
    if (_isExporting.value) return
    _isExporting.value = true
    viewModelScope.launch {
      try {
        val pages = repository.getPages(folderId)
        if (pages.isEmpty()) {
          showMessage("This folder has no pages to export yet.")
        } else {
          onReady(repository.contentUri(repository.exportPdf(folderName, pages)))
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        showMessage("Couldn't create the PDF. Please try again.")
      } finally {
        _isExporting.value = false
      }
    }
  }

  fun sharePages(folderId: Long, onReady: (List<Uri>) -> Unit) {
    viewModelScope.launch {
      val pages = repository.getPages(folderId)
      if (pages.isEmpty()) showMessage("This folder has no pages to share yet.") else onReady(pageUris(pages))
    }
  }

  // endregion

  // region Settings

  fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)

  fun setApiKey(key: String) = settings.setApiKey(key)

  fun refreshStorageUsed() {
    viewModelScope.launch { _storageUsedBytes.value = repository.storageUsedBytes() }
  }

  fun clearCache() {
    viewModelScope.launch {
      repository.clearCache()
      showMessage("Temporary files cleared")
    }
  }

  // endregion

  fun showMessage(message: String) {
    messageChannel.trySend(message)
  }

  companion object {
    private const val KEY_PENDING_URIS = "pending_uris"

    val Factory = viewModelFactory {
      initializer {
        val app = this[APPLICATION_KEY] as Snap2NoteApplication
        NoteViewModel(app.repository, app.settings, app.gemini, createSavedStateHandle())
      }
    }
  }
}
