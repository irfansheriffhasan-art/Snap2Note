package io.github.irfansheriffhasan.snap2note.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.irfansheriffhasan.snap2note.data.FolderWithStats
import io.github.irfansheriffhasan.snap2note.ui.NoteViewModel
import io.github.irfansheriffhasan.snap2note.ui.components.ConfirmDialog
import io.github.irfansheriffhasan.snap2note.ui.components.EmptyState
import io.github.irfansheriffhasan.snap2note.ui.components.FolderCover
import io.github.irfansheriffhasan.snap2note.ui.components.TextInputDialog
import io.github.irfansheriffhasan.snap2note.ui.components.pageCountLabel
import io.github.irfansheriffhasan.snap2note.ui.components.shareImages
import io.github.irfansheriffhasan.snap2note.ui.components.sharePdf
import io.github.irfansheriffhasan.snap2note.ui.components.statsLabel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
  viewModel: NoteViewModel,
  onOpenFolder: (Long) -> Unit,
  onScanInto: (Long) -> Unit,
) {
  val context = LocalContext.current
  val folders by viewModel.searchResults.collectAsStateWithLifecycle()
  val query by viewModel.searchQuery.collectAsStateWithLifecycle()

  var showCreateDialog by rememberSaveable { mutableStateOf(false) }
  var folderToRename by remember { mutableStateOf<FolderWithStats?>(null) }
  var folderToDelete by remember { mutableStateOf<FolderWithStats?>(null) }

  val gridState = rememberLazyGridState()
  val fabExpanded by remember { derivedStateOf { gridState.firstVisibleItemIndex == 0 } }

  Scaffold(
    topBar = { TopAppBar(title = { Text("Folders") }) },
    floatingActionButton = {
      ExtendedFloatingActionButton(
        onClick = { showCreateDialog = true },
        expanded = fabExpanded,
        icon = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = "New folder") },
        text = { Text("New folder") },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
    ) {
      SearchField(
        query = query,
        onQueryChange = viewModel::setSearchQuery,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
      )

      val list = folders
      when {
        list == null -> Unit
        list.isEmpty() && query.isNotBlank() ->
          EmptyState(
            icon = Icons.Outlined.SearchOff,
            title = "No matching folders",
            message = "No folder names contain \"${query.trim()}\".",
            modifier = Modifier.fillMaxWidth(),
          )
        list.isEmpty() ->
          EmptyState(
            icon = Icons.Outlined.CreateNewFolder,
            title = "No folders yet",
            message = "Create a folder for each subject or course to keep your notes organized.",
            modifier = Modifier.fillMaxWidth(),
            actions = { Button(onClick = { showCreateDialog = true }) { Text("Create a folder") } },
          )
        else ->
          LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            state = gridState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
          ) {
            items(list, key = { it.id }) { folder ->
              FolderCard(
                folder = folder,
                coverFile = viewModel.coverFile(folder),
                onOpen = { onOpenFolder(folder.id) },
                onScan = { onScanInto(folder.id) },
                onExportPdf = { viewModel.exportPdf(folder.id, folder.name) { context.sharePdf(it, folder.name) } },
                onShare = { viewModel.sharePages(folder.id) { context.shareImages(it, folder.name) } },
                onRename = { folderToRename = folder },
                onDelete = { folderToDelete = folder },
                modifier = Modifier.animateItem(),
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
      confirmLabel = "Create",
      onConfirm = { viewModel.createFolder(it) },
      onDismiss = { showCreateDialog = false },
    )
  }

  folderToRename?.let { folder ->
    TextInputDialog(
      title = "Rename folder",
      label = "Folder name",
      confirmLabel = "Rename",
      initialValue = folder.name,
      onConfirm = { viewModel.renameFolder(folder.id, it) },
      onDismiss = { folderToRename = null },
    )
  }

  folderToDelete?.let { folder ->
    ConfirmDialog(
      title = "Delete \"${folder.name}\"?",
      message = if (folder.pageCount == 0) {
        "This folder is empty."
      } else {
        "${pageCountLabel(folder.pageCount)} and their study notes will be permanently deleted."
      },
      confirmLabel = "Delete",
      onConfirm = { viewModel.deleteFolder(folder.id) },
      onDismiss = { folderToDelete = null },
    )
  }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
  TextField(
    value = query,
    onValueChange = onQueryChange,
    placeholder = { Text("Search folders") },
    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
    trailingIcon = {
      if (query.isNotEmpty()) {
        IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") }
      }
    },
    singleLine = true,
    shape = CircleShape,
    colors = TextFieldDefaults.colors(
      focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      focusedIndicatorColor = Color.Transparent,
      unfocusedIndicatorColor = Color.Transparent,
    ),
    modifier = modifier.fillMaxWidth(),
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderCard(
  folder: FolderWithStats,
  coverFile: File?,
  onOpen: () -> Unit,
  onScan: () -> Unit,
  onExportPdf: () -> Unit,
  onShare: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var menuExpanded by remember { mutableStateOf(false) }

  Card(
    onClick = onOpen,
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = modifier,
  ) {
    FolderCover(
      name = folder.name,
      coverFile = coverFile,
      iconSize = 36.dp,
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(4f / 3f),
    )
    Row(
      modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(folder.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
          folder.statsLabel(),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Box {
        IconButton(onClick = { menuExpanded = true }) {
          Icon(Icons.Outlined.MoreVert, contentDescription = "More options for ${folder.name}")
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
          MenuItem("Scan pages", Icons.Outlined.CameraAlt) { menuExpanded = false; onScan() }
          if (folder.pageCount > 0) {
            MenuItem("Export as PDF", Icons.Outlined.PictureAsPdf) { menuExpanded = false; onExportPdf() }
            MenuItem("Share pages", Icons.Outlined.Share) { menuExpanded = false; onShare() }
          }
          MenuItem("Rename", Icons.Outlined.Edit) { menuExpanded = false; onRename() }
          HorizontalDivider()
          MenuItem("Delete", Icons.Outlined.Delete, destructive = true) { menuExpanded = false; onDelete() }
        }
      }
    }
  }
}

@Composable
internal fun MenuItem(
  label: String,
  icon: ImageVector,
  destructive: Boolean = false,
  onClick: () -> Unit,
) {
  val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
  DropdownMenuItem(
    text = { Text(label, color = color) },
    leadingIcon = { Icon(icon, contentDescription = null, tint = color) },
    onClick = onClick,
  )
}
