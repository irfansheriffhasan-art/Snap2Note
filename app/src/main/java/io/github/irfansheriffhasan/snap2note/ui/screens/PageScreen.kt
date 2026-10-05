package io.github.irfansheriffhasan.snap2note.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.github.irfansheriffhasan.snap2note.ai.Flashcard
import io.github.irfansheriffhasan.snap2note.ai.FlashcardsJson
import io.github.irfansheriffhasan.snap2note.data.Page
import io.github.irfansheriffhasan.snap2note.data.isAnalyzed
import io.github.irfansheriffhasan.snap2note.ui.NoteViewModel
import io.github.irfansheriffhasan.snap2note.ui.components.ApiKeyRequiredDialog
import io.github.irfansheriffhasan.snap2note.ui.components.ConfirmDialog
import io.github.irfansheriffhasan.snap2note.ui.components.FullScreenImageDialog
import io.github.irfansheriffhasan.snap2note.ui.components.MarkdownText
import io.github.irfansheriffhasan.snap2note.ui.components.MoveToFolderDialog
import io.github.irfansheriffhasan.snap2note.ui.components.TextInputDialog
import io.github.irfansheriffhasan.snap2note.ui.components.copyToClipboard
import io.github.irfansheriffhasan.snap2note.ui.components.shareImages
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageScreen(
  folderId: Long,
  initialPageId: Long,
  viewModel: NoteViewModel,
  onBack: () -> Unit,
  onOpenSettings: () -> Unit,
) {
  val context = LocalContext.current
  val folders by viewModel.folders.collectAsStateWithLifecycle()
  val pagesFlow = remember(folderId) { viewModel.pages(folderId) }
  val loadedPages by pagesFlow.collectAsStateWithLifecycle()
  val analyzingIds by viewModel.analyzingPageIds.collectAsStateWithLifecycle()
  val analysisErrors by viewModel.analysisErrors.collectAsStateWithLifecycle()
  val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()

  val pages = loadedPages
  if (pages == null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    return
  }
  if (pages.isEmpty()) {
    LaunchedEffect(Unit) { onBack() }
    return
  }

  val initialIndex = remember { pages.indexOfFirst { it.id == initialPageId }.coerceAtLeast(0) }
  val pagerState = rememberPagerState(initialPage = initialIndex) { pages.size }
  val currentIndex = pagerState.currentPage.coerceIn(0, pages.lastIndex)
  val page = pages[currentIndex]
  val folderName = folders?.firstOrNull { it.id == folderId }?.name.orEmpty()

  var showRenameDialog by rememberSaveable { mutableStateOf(false) }
  var showMoveDialog by rememberSaveable { mutableStateOf(false) }
  var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
  var showApiKeyDialog by rememberSaveable { mutableStateOf(false) }
  var fullScreenPage by remember { mutableStateOf<Page?>(null) }

  val analyze = {
    if (apiKey.isBlank()) showApiKeyDialog = true else viewModel.analyze(listOf(page))
  }

  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        },
        title = {
          Column {
            Text("Page ${currentIndex + 1} of ${pages.size}")
            Text(
              folderName,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        },
        actions = {
          IconButton(onClick = { context.shareImages(viewModel.pageUris(listOf(page)), page.title ?: folderName) }) {
            Icon(Icons.Outlined.Share, contentDescription = "Share page")
          }
          IconButton(onClick = { showMoveDialog = true }) {
            Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = "Move page")
          }
          IconButton(onClick = { showDeleteDialog = true }) {
            Icon(Icons.Outlined.Delete, contentDescription = "Delete page")
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(0.45f)
          .background(MaterialTheme.colorScheme.surfaceContainerHigh),
      ) {
        HorizontalPager(
          state = pagerState,
          key = { pages[it].id },
          modifier = Modifier.fillMaxSize(),
        ) { index ->
          val pagerPage = pages[index]
          AsyncImage(
            model = viewModel.pageFile(pagerPage),
            contentDescription = "Page ${index + 1}",
            contentScale = ContentScale.Fit,
            modifier = Modifier
              .fillMaxSize()
              .padding(12.dp)
              .clickable { fullScreenPage = pagerPage },
          )
        }
        FilledTonalIconButton(
          onClick = { fullScreenPage = page },
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(12.dp),
        ) {
          Icon(Icons.Outlined.Fullscreen, contentDescription = "View full screen")
        }
      }

      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .weight(0.55f),
      ) {
        key(page.id) {
          PageInsights(
            page = page,
            pageNumber = currentIndex + 1,
            analyzing = page.id in analyzingIds,
            error = analysisErrors[page.id],
            onAnalyze = analyze,
            onRename = { showRenameDialog = true },
          )
        }
      }
    }
  }

  fullScreenPage?.let { shown ->
    FullScreenImageDialog(
      model = viewModel.pageFile(shown),
      contentDescription = shown.title,
      onDismiss = { fullScreenPage = null },
    )
  }

  if (showRenameDialog) {
    TextInputDialog(
      title = "Rename page",
      label = "Title",
      confirmLabel = "Save",
      initialValue = page.title.orEmpty(),
      allowEmpty = true,
      onConfirm = { viewModel.renamePage(page, it) },
      onDismiss = { showRenameDialog = false },
    )
  }

  if (showMoveDialog) {
    MoveToFolderDialog(
      folders = folders.orEmpty(),
      currentFolderId = folderId,
      coverFile = viewModel::coverFile,
      onSelect = { viewModel.movePages(listOf(page), it.id, it.name) },
      onDismiss = { showMoveDialog = false },
    )
  }

  if (showDeleteDialog) {
    ConfirmDialog(
      title = "Delete this page?",
      message = "The image and its study notes will be permanently deleted.",
      confirmLabel = "Delete",
      onConfirm = { viewModel.deletePages(listOf(page)) },
      onDismiss = { showDeleteDialog = false },
    )
  }

  if (showApiKeyDialog) {
    ApiKeyRequiredDialog(onOpenSettings = onOpenSettings, onDismiss = { showApiKeyDialog = false })
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageInsights(
  page: Page,
  pageNumber: Int,
  analyzing: Boolean,
  error: String?,
  onAnalyze: () -> Unit,
  onRename: () -> Unit,
) {
  val addedOn = remember(page.createdAt) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(page.createdAt)) }

  Column(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          page.title ?: "Page $pageNumber",
          style = MaterialTheme.typography.titleMedium,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Text("Added $addedOn", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      IconButton(onClick = onRename) { Icon(Icons.Outlined.Edit, contentDescription = "Rename page") }
      if (page.isAnalyzed && !analyzing) {
        IconButton(onClick = onAnalyze) { Icon(Icons.Outlined.Refresh, contentDescription = "Analyze again") }
      }
    }

    when {
      analyzing -> AnalyzingState()
      !page.isAnalyzed -> AnalyzePrompt(error = error, onAnalyze = onAnalyze)
      else -> AnalysisTabs(page = page, error = error)
    }
  }
}

@Composable
private fun AnalyzingState() {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    CircularProgressIndicator()
    Spacer(Modifier.height(20.dp))
    Text("Analyzing page…", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(6.dp))
    Text(
      "Reading the text and preparing notes and flashcards. This usually takes a few seconds.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
private fun AnalyzePrompt(error: String?, onAnalyze: () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .navigationBarsPadding()
      .padding(horizontal = 32.dp, vertical = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Box(
      modifier = Modifier
        .size(56.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.tertiaryContainer),
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
    }
    Spacer(Modifier.height(16.dp))
    Text("Turn this page into study notes", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(6.dp))
    Text(
      "Extract the text, get a clear explanation of the key ideas and generate flashcards to test yourself.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(20.dp))
    Button(onClick = onAnalyze) {
      Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
      Spacer(Modifier.width(ButtonDefaults.IconSpacing))
      Text("Analyze page")
    }
    if (error != null) {
      Spacer(Modifier.height(16.dp))
      Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalysisTabs(page: Page, error: String?) {
  val context = LocalContext.current
  var selectedTab by rememberSaveable { mutableIntStateOf(0) }
  val flashcards = remember(page.flashcardsJson) { FlashcardsJson.decode(page.flashcardsJson) }
  val tabs = listOf("Notes", "Text", if (flashcards.isEmpty()) "Flashcards" else "Flashcards (${flashcards.size})")

  Column(modifier = Modifier.fillMaxSize()) {
    PrimaryTabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent) {
      tabs.forEachIndexed { index, title ->
        Tab(
          selected = selectedTab == index,
          onClick = { selectedTab = index },
          text = { Text(title, maxLines = 1) },
          selectedContentColor = MaterialTheme.colorScheme.primary,
          unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    key(selectedTab) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .verticalScroll(rememberScrollState())
          .navigationBarsPadding()
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (error != null) {
          Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        when (selectedTab) {
          0 -> SelectionContainer {
            MarkdownText(page.summary.orEmpty().ifBlank { "No explanation is available for this page." })
          }
          1 -> {
            val text = page.extractedText.orEmpty()
            if (text.isBlank()) {
              Text(
                "No text was found on this page.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              TextButton(onClick = { context.copyToClipboard("Page text", text) }) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Copy text")
              }
              SelectionContainer { Text(text, style = MaterialTheme.typography.bodyMedium) }
            }
          }
          else ->
            if (flashcards.isEmpty()) {
              Text(
                "No flashcards were generated for this page.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              flashcards.forEachIndexed { index, card -> FlashcardItem(card, index + 1) }
            }
        }
      }
    }
  }
}

@Composable
private fun FlashcardItem(card: Flashcard, number: Int) {
  var revealed by rememberSaveable(card.question) { mutableStateOf(false) }
  Card(
    onClick = { revealed = !revealed },
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        "Question $number",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
      )
      Spacer(Modifier.height(6.dp))
      Text(card.question, style = MaterialTheme.typography.bodyLarge)
      AnimatedVisibility(visible = revealed, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column {
          HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
          Text(card.answer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      if (!revealed) {
        Spacer(Modifier.height(8.dp))
        Text("Tap to show answer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}
