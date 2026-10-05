package io.github.irfansheriffhasan.snap2note.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.irfansheriffhasan.snap2note.ui.NoteViewModel
import io.github.irfansheriffhasan.snap2note.ui.components.EmptyState
import io.github.irfansheriffhasan.snap2note.ui.components.FolderListItem
import io.github.irfansheriffhasan.snap2note.ui.components.LogoMark
import io.github.irfansheriffhasan.snap2note.ui.components.SectionHeader
import io.github.irfansheriffhasan.snap2note.ui.components.rememberPickImages
import io.github.irfansheriffhasan.snap2note.ui.components.rememberTakePicture

private const val RECENT_FOLDER_COUNT = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  viewModel: NoteViewModel,
  onOpenFolder: (Long) -> Unit,
  onSeeAllFolders: () -> Unit,
  onPagesReady: () -> Unit,
) {
  val folders by viewModel.folders.collectAsStateWithLifecycle()
  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

  val takePicture = rememberTakePicture(
    createUri = viewModel::newCaptureUri,
    onCaptured = { uri ->
      viewModel.setPendingUris(listOf(uri))
      onPagesReady()
    },
    onCancelled = viewModel::discardCapture,
  )
  val pickImages = rememberPickImages { uris ->
    viewModel.setPendingUris(uris)
    onPagesReady()
  }

  Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            LogoMark(size = 30.dp)
            Spacer(Modifier.width(12.dp))
            Text("Snap2Note")
          }
        },
        scrollBehavior = scrollBehavior,
      )
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = innerPadding.calculateTopPadding() + 8.dp,
        bottom = innerPadding.calculateBottomPadding() + 24.dp,
      ),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item(key = "capture") {
        CaptureCard(onScan = takePicture, onImport = pickImages)
      }

      val loaded = folders ?: return@LazyColumn
      item(key = "stats") {
        StatsRow(
          folderCount = loaded.size,
          pageCount = loaded.sumOf { it.pageCount },
          analyzedCount = loaded.sumOf { it.analyzedCount },
        )
      }
      item(key = "recent-header") {
        SectionHeader(
          title = "Recent folders",
          actionLabel = if (loaded.size > RECENT_FOLDER_COUNT) "See all" else null,
          onAction = onSeeAllFolders,
        )
      }
      if (loaded.isEmpty()) {
        item(key = "empty") {
          EmptyState(
            icon = Icons.Outlined.CreateNewFolder,
            title = "No folders yet",
            message = "Scan your first page and Snap2Note will help you file it into a folder.",
            modifier = Modifier.fillMaxWidth(),
          )
        }
      } else {
        items(loaded.take(RECENT_FOLDER_COUNT), key = { it.id }) { folder ->
          FolderListItem(
            folder = folder,
            coverFile = viewModel.coverFile(folder),
            onClick = { onOpenFolder(folder.id) },
            modifier = Modifier.animateItem(),
          )
        }
      }
    }
  }
}

@Composable
private fun CaptureCard(onScan: () -> Unit, onImport: () -> Unit) {
  val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
  Surface(
    shape = MaterialTheme.shapes.extraLarge,
    color = MaterialTheme.colorScheme.primaryContainer,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(20.dp)) {
      Text("Capture your notes", style = MaterialTheme.typography.titleLarge, color = onContainer)
      Spacer(Modifier.height(6.dp))
      Text(
        "Scan handouts, slides and notebook pages, then turn them into study notes and flashcards.",
        style = MaterialTheme.typography.bodyMedium,
        color = onContainer.copy(alpha = 0.8f),
      )
      Spacer(Modifier.height(18.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = onScan, modifier = Modifier.weight(1f)) {
          Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
          Spacer(Modifier.width(ButtonDefaults.IconSpacing))
          Text("Scan page")
        }
        OutlinedButton(
          onClick = onImport,
          modifier = Modifier.weight(1f),
          border = BorderStroke(1.dp, onContainer.copy(alpha = 0.3f)),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = onContainer),
        ) {
          Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
          Spacer(Modifier.width(ButtonDefaults.IconSpacing))
          Text("Import")
        }
      }
    }
  }
}

@Composable
private fun StatsRow(folderCount: Int, pageCount: Int, analyzedCount: Int) {
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
    StatTile(value = folderCount, label = if (folderCount == 1) "Folder" else "Folders", modifier = Modifier.weight(1f))
    StatTile(value = pageCount, label = if (pageCount == 1) "Page" else "Pages", modifier = Modifier.weight(1f))
    StatTile(value = analyzedCount, label = "Analyzed", modifier = Modifier.weight(1f))
  }
}

@Composable
private fun StatTile(value: Int, label: String, modifier: Modifier = Modifier) {
  Surface(
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    modifier = modifier,
  ) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
      Text(value.toString(), style = MaterialTheme.typography.headlineSmall)
      Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}
