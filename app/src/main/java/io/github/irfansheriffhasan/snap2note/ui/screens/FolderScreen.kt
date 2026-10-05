package io.github.irfansheriffhasan.snap2note.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.github.irfansheriffhasan.snap2note.data.isAnalyzed
import io.github.irfansheriffhasan.snap2note.ui.NoteViewModel
import io.github.irfansheriffhasan.snap2note.ui.components.ApiKeyRequiredDialog
import io.github.irfansheriffhasan.snap2note.ui.components.ConfirmDialog
import io.github.irfansheriffhasan.snap2note.ui.components.EmptyState
import io.github.irfansheriffhasan.snap2note.ui.components.MoveToFolderDialog
import io.github.irfansheriffhasan.snap2note.ui.components.TextInputDialog
import io.github.irfansheriffhasan.snap2note.ui.components.pageCountLabel
import io.github.irfansheriffhasan.snap2note.ui.components.rememberPickImages
import io.github.irfansheriffhasan.snap2note.ui.components.shareImages
import io.github.irfansheriffhasan.snap2note.ui.components.sharePdf
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
  folderId: Long,
  viewModel: NoteViewModel,
  onBack: () -> Unit,
  onOpenPage: (Long) -> Unit,
  onScan: () -> Unit,
  onOpenAssistant: () -> Unit,
  onOpenSettings: () -> Unit,
) {
  val context = LocalContext.current
  val folders by viewModel.folders.collectAsStateWithLifecycle()
  val pagesFlow = remember(folderId) { viewModel.pages(folderId) }
  val loadedPages by pagesFlow.collectAsStateWithLifecycle()
  val analyzingIds by viewModel.analyzingPageIds.collectAsStateWithLifecycle()
  val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
  val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()

  val folder = folders?.firstOrNull { it.id == folderId }
  val folderName = folder?.name.orEmpty()
  val pages = loadedPages.orEmpty()

  // Leave the screen once the folder no longer exists, e.g. after deleting it. A newly created
  // folder may not have reached the list yet, so only react to a folder that was seen before.
  var folderSeen by remember { mutableStateOf(false) }
  LaunchedEffect(folder != null) {
    if (folder != null) folderSeen = true else if (folderSeen) onBack()
  }

  var selectedIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }
  val selectedPages = pages.filter { it.id in selectedIds }
  val selectionMode = selectedPages.isNotEmpty()
  LaunchedEffect(pages) {
    val existing = pages.map { it.id }.toSet()
    selectedIds = selectedIds intersect existing
  }
  BackHandler(enabled = selectionMode) { selectedIds = emptySet() }

  var menuExpanded by remember { mutableStateOf(false) }
  var showRenameDialog by rememberSaveable { mutableStateOf(false) }
  var showDeleteFolderDialog by rememberSaveable { mutableStateOf(false) }
  var showDeletePagesDialog by rememberSaveable { mutableStateOf(false) }
  var showMoveDialog by rememberSaveable { mutableStateOf(false) }
  var showApiKeyDialog by rememberSaveable { mutableStateOf(false) }

  val pickImages = rememberPickImages { uris -> viewModel.addPages(folderId, uris) }
  val gridState = rememberLazyGridState()
  val fabExpanded by remember { derivedStateOf { gridState.firstVisibleItemIndex == 0 } }

  fun requireApiKey(action: () -> Unit) {
    if (apiKey.isBlank()) showApiKeyDialog = true else action()
  }

  Scaffold(
    topBar = {
      if (selectionMode) {
        TopAppBar(
          navigationIcon = {
            IconButton(onClick = { selectedIds = emptySet() }) { Icon(Icons.Filled.Close, contentDescription = "Clear selection") }
          },
          title = { Text("${selectedPages.size} selected") },
          actions = {
            IconButton(onClick = { selectedIds = pages.map { it.id }.toSet() }) {
              Icon(Icons.Outlined.SelectAll, contentDescription = "Select all")
            }
            IconButton(onClick = { context.shareImages(viewModel.pageUris(selectedPages), folderName) }) {
              Icon(Icons.Outlined.Share, contentDescription = "Share")
            }
            IconButton(onClick = { showMoveDialog = true }) {
              Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = "Move to folder")
            }
            IconButton(onClick = { showDeletePagesDialog = true }) {
              Icon(Icons.Outlined.Delete, contentDescription = "Delete")
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        )
      } else {
        TopAppBar(
          navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
          },
          title = {
            Column {
              Text(folderName, maxLines = 1, overflow = TextOverflow.Ellipsis)
              if (loadedPages != null) {
                Text(
                  pageCountLabel(pages.size),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          },
          actions = {
            if (pages.isNotEmpty()) {
              IconButton(
                onClick = { viewModel.exportPdf(folderId, folderName) { context.sharePdf(it, folderName) } },
                enabled = !isExporting,
              ) {
                if (isExporting) {
                  CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                  Icon(Icons.Outlined.PictureAsPdf, contentDescription = "Export as PDF")
                }
              }
            }
            Box {
              IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More options") }
              DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                MenuItem("Import from gallery", Icons.Outlined.PhotoLibrary) { menuExpanded = false; pickImages() }
                if (pages.isNotEmpty()) {
                  MenuItem("Share pages", Icons.Outlined.Share) {
                    menuExpanded = false
                    context.shareImages(viewModel.pageUris(pages), folderName)
                  }
                  MenuItem("Select pages", Icons.Outlined.Checklist) {
                    menuExpanded = false
                    selectedIds = setOf(pages.first().id)
                  }
                }
                MenuItem("Rename folder", Icons.Outlined.Edit) { menuExpanded = false; showRenameDialog = true }
                HorizontalDivider()
                MenuItem("Delete folder", Icons.Outlined.Delete, destructive = true) {
                  menuExpanded = false
                  showDeleteFolderDialog = true
                }
              }
            }
          },
        )
      }
    },
    floatingActionButton = {
      if (!selectionMode && pages.isNotEmpty()) {
        ExtendedFloatingActionButton(
          onClick = onScan,
          expanded = fabExpanded,
          icon = { Icon(Icons.Outlined.CameraAlt, contentDescription = "Scan pages") },
          text = { Text("Scan") },
        )
      }
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
    ) {
      when {
        loadedPages == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        pages.isEmpty() ->
          EmptyState(
            icon = Icons.Outlined.DocumentScanner,
            title = "No pages yet",
            message = "Scan pages with your camera or import photos from your gallery.",
            modifier = Modifier.align(Alignment.Center),
            actions = {
              Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onScan) {
                  Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                  Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                  Text("Scan pages")
                }
                OutlinedButton(onClick = pickImages) {
                  Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                  Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                  Text("Import")
                }
              }
            },
          )
        else -> {
          val analyzedCount = pages.count { it.isAnalyzed }
          val analyzingCount = pages.count { it.id in analyzingIds }
          LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            state = gridState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
          ) {
            item(key = "study-tools", span = { GridItemSpan(maxLineSpan) }) {
              StudyToolsCard(
                pageCount = pages.size,
                analyzedCount = analyzedCount,
                analyzingCount = analyzingCount,
                onAnalyze = { requireApiKey { viewModel.analyze(pages.filterNot { it.isAnalyzed }) } },
                onOpenAssistant = { requireApiKey(onOpenAssistant) },
                modifier = Modifier.padding(bottom = 6.dp),
              )
            }
            itemsIndexed(pages, key = { _, page -> page.id }) { index, page ->
              val selected = page.id in selectedIds
              PageThumbnail(
                file = viewModel.pageFile(page),
                number = index + 1,
                analyzed = page.isAnalyzed,
                analyzing = page.id in analyzingIds,
                selected = selected,
                selectionMode = selectionMode,
                onClick = {
                  if (selectionMode) {
                    selectedIds = if (selected) selectedIds - page.id else selectedIds + page.id
                  } else {
                    onOpenPage(page.id)
                  }
                },
                onLongClick = { selectedIds = if (selected) selectedIds - page.id else selectedIds + page.id },
                modifier = Modifier.animateItem(),
              )
            }
          }
        }
      }
    }
  }

  if (showRenameDialog) {
    TextInputDialog(
      title = "Rename folder",
      label = "Folder name",
      confirmLabel = "Rename",
      initialValue = folderName,
      onConfirm = { viewModel.renameFolder(folderId, it) },
      onDismiss = { showRenameDialog = false },
    )
  }

  if (showDeleteFolderDialog) {
    ConfirmDialog(
      title = "Delete \"$folderName\"?",
      message = if (pages.isEmpty()) {
        "This folder is empty."
      } else {
        "${pageCountLabel(pages.size)} and their study notes will be permanently deleted."
      },
      confirmLabel = "Delete",
      onConfirm = { viewModel.deleteFolder(folderId) },
      onDismiss = { showDeleteFolderDialog = false },
    )
  }

  if (showDeletePagesDialog) {
    ConfirmDialog(
      title = if (selectedPages.size == 1) "Delete this page?" else "Delete ${selectedPages.size} pages?",
      message = "The images and their study notes will be permanently deleted.",
      confirmLabel = "Delete",
      onConfirm = {
        viewModel.deletePages(selectedPages)
        selectedIds = emptySet()
      },
      onDismiss = { showDeletePagesDialog = false },
    )
  }

  if (showMoveDialog) {
    MoveToFolderDialog(
      folders = folders.orEmpty(),
      currentFolderId = folderId,
      coverFile = viewModel::coverFile,
      onSelect = { destination ->
        viewModel.movePages(selectedPages, destination.id, destination.name)
        selectedIds = emptySet()
      },
      onDismiss = { showMoveDialog = false },
    )
  }

  if (showApiKeyDialog) {
    ApiKeyRequiredDialog(onOpenSettings = onOpenSettings, onDismiss = { showApiKeyDialog = false })
  }
}

@Composable
private fun StudyToolsCard(
  pageCount: Int,
  analyzedCount: Int,
  analyzingCount: Int,
  onAnalyze: () -> Unit,
  onOpenAssistant: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val remaining = pageCount - analyzedCount
  Surface(
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.tertiaryContainer),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(20.dp),
          )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text("Study tools", style = MaterialTheme.typography.titleSmall)
          Text(
            when {
              analyzingCount > 0 -> "Analyzing ${pageCountLabel(analyzingCount)}…"
              remaining == 0 -> "All pages analyzed"
              else -> "$analyzedCount of $pageCount pages analyzed"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      if (analyzingCount > 0) {
        LinearProgressIndicator(
          progress = { analyzedCount.toFloat() / pageCount },
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        )
      }
      Spacer(Modifier.height(14.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (remaining > 0) {
          OutlinedButton(onClick = onAnalyze, enabled = analyzingCount == 0, modifier = Modifier.weight(1f)) {
            Text(if (remaining == pageCount) "Analyze all" else "Analyze $remaining more", maxLines = 1)
          }
        }
        Button(onClick = onOpenAssistant, modifier = Modifier.weight(1f)) {
          Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
          Spacer(Modifier.width(ButtonDefaults.IconSpacing))
          Text("Ask", maxLines = 1)
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageThumbnail(
  file: File,
  number: Int,
  analyzed: Boolean,
  analyzing: Boolean,
  selected: Boolean,
  selectionMode: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val shape = MaterialTheme.shapes.medium
  Box(
    modifier = modifier
      .aspectRatio(3f / 4f)
      .clip(shape)
      .background(MaterialTheme.colorScheme.surfaceContainerHigh)
      .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
      .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Select page"),
  ) {
    AsyncImage(
      model = file,
      contentDescription = "Page $number",
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize(),
    )

    Surface(
      color = Color.Black.copy(alpha = 0.6f),
      contentColor = Color.White,
      shape = MaterialTheme.shapes.small,
      modifier = Modifier
        .align(Alignment.BottomStart)
        .padding(6.dp),
    ) {
      Text("$number", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
    }

    if (analyzing) {
      Box(
        modifier = Modifier
          .matchParentSize()
          .background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(26.dp))
      }
    } else if (analyzed && !selectionMode) {
      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(6.dp),
      ) {
        Icon(
          Icons.Filled.AutoAwesome,
          contentDescription = "Analyzed",
          tint = MaterialTheme.colorScheme.onTertiaryContainer,
          modifier = Modifier
            .padding(4.dp)
            .size(14.dp),
        )
      }
    }

    if (selectionMode) {
      if (selected) {
        Box(
          modifier = Modifier
            .matchParentSize()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
        )
      }
      Icon(
        if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
        contentDescription = null,
        tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(6.dp)
          .background(if (selected) Color.White else Color.Black.copy(alpha = 0.3f), CircleShape),
      )
    }
  }
}
