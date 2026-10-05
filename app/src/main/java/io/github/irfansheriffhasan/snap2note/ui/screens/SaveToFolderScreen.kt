package io.github.irfansheriffhasan.snap2note.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.github.irfansheriffhasan.snap2note.ui.NoteViewModel
import io.github.irfansheriffhasan.snap2note.ui.components.FolderListItem
import io.github.irfansheriffhasan.snap2note.ui.components.SectionHeader
import io.github.irfansheriffhasan.snap2note.ui.components.TextInputDialog
import io.github.irfansheriffhasan.snap2note.ui.components.pageCountLabel

/** Lets the user choose where newly captured or imported pages go. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveToFolderScreen(
  viewModel: NoteViewModel,
  onClose: () -> Unit,
  onSaved: (Long) -> Unit,
) {
  val pendingUris by viewModel.pendingUris.collectAsStateWithLifecycle()
  val folders by viewModel.folders.collectAsStateWithLifecycle()
  val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
  var showCreateDialog by rememberSaveable { mutableStateOf(false) }

  // Nothing to save, e.g. when the app was restored after the pages were already saved.
  LaunchedEffect(Unit) {
    if (viewModel.pendingUris.value.isEmpty()) onClose()
  }

  val discardAndClose = {
    viewModel.discardPendingUris()
    onClose()
  }
  BackHandler(enabled = !isSaving, onBack = discardAndClose)

  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(onClick = discardAndClose, enabled = !isSaving) {
            Icon(Icons.Filled.Close, contentDescription = "Discard")
          }
        },
        title = { Text("Save to folder") },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
    ) {
      if (isSaving) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
      LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
      ) {
        item(key = "preview") { PendingPreview(pendingUris) }

        item(key = "new-folder") {
          Surface(
            onClick = { showCreateDialog = true },
            enabled = !isSaving,
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
              Spacer(Modifier.width(16.dp))
              Text("New folder", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
          }
        }

        val list = folders.orEmpty()
        if (list.isNotEmpty()) {
          item(key = "header") { SectionHeader("Your folders") }
          items(list, key = { it.id }) { folder ->
            FolderListItem(
              folder = folder,
              coverFile = viewModel.coverFile(folder),
              enabled = !isSaving,
              onClick = { viewModel.savePendingTo(folder.id) { onSaved(folder.id) } },
            )
          }
        }
      }
    }
  }

  if (showCreateDialog) {
    TextInputDialog(
      title = "New folder",
      label = "Folder name",
      confirmLabel = "Create & save",
      onConfirm = { name -> viewModel.savePendingToNewFolder(name, onSaved) },
      onDismiss = { showCreateDialog = false },
    )
  }
}

@Composable
private fun PendingPreview(uris: List<Uri>) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.padding(bottom = 8.dp),
  ) {
    Box {
      uris.take(3).reversed().forEachIndexed { index, uri ->
        val offset = (uris.take(3).size - 1 - index) * 10
        AsyncImage(
          model = uri,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .padding(start = offset.dp, top = offset.dp)
            .size(width = 84.dp, height = 112.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
      }
    }
    Spacer(Modifier.width(20.dp))
    Column {
      Text(
        if (uris.size == 1) "1 page ready" else "${pageCountLabel(uris.size)} ready",
        style = MaterialTheme.typography.titleLarge,
      )
      Spacer(Modifier.height(4.dp))
      Text(
        "Choose a folder to save to.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
