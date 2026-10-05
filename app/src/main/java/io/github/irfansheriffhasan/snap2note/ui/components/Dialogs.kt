package io.github.irfansheriffhasan.snap2note.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.irfansheriffhasan.snap2note.data.FolderWithStats
import java.io.File

@Composable
fun ConfirmDialog(
  title: String,
  message: String,
  confirmLabel: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  destructive: Boolean = true,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(message) },
    confirmButton = {
      Button(
        onClick = {
          onConfirm()
          onDismiss()
        },
        colors = if (destructive) {
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          )
        } else {
          ButtonDefaults.buttonColors()
        },
      ) { Text(confirmLabel) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

/** A dialog with a single text field, used for creating and renaming things. */
@Composable
fun TextInputDialog(
  title: String,
  label: String,
  confirmLabel: String,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit,
  initialValue: String = "",
  allowEmpty: Boolean = false,
) {
  var value by remember {
    mutableStateOf(TextFieldValue(initialValue, selection = TextRange(0, initialValue.length)))
  }
  val focusRequester = remember { FocusRequester() }
  val canConfirm = allowEmpty || value.text.isNotBlank()
  val confirm = {
    if (canConfirm) {
      onConfirm(value.text.trim())
      onDismiss()
    }
  }

  LaunchedEffect(Unit) { focusRequester.requestFocus() }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { confirm() }),
        modifier = Modifier
          .fillMaxWidth()
          .focusRequester(focusRequester),
      )
    },
    confirmButton = { Button(onClick = confirm, enabled = canConfirm) { Text(confirmLabel) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
fun MoveToFolderDialog(
  folders: List<FolderWithStats>,
  currentFolderId: Long,
  coverFile: (FolderWithStats) -> File?,
  onSelect: (FolderWithStats) -> Unit,
  onDismiss: () -> Unit,
) {
  val destinations = folders.filter { it.id != currentFolderId }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Move to folder") },
    text = {
      if (destinations.isEmpty()) {
        Text("Create another folder first to move pages into it.")
      } else {
        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
          items(destinations, key = { it.id }) { folder ->
            ListItem(
              headlineContent = { Text(folder.name) },
              supportingContent = { Text(pageCountLabel(folder.pageCount)) },
              leadingContent = {
                FolderCover(
                  name = folder.name,
                  coverFile = coverFile(folder),
                  iconSize = 20.dp,
                  modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small),
                )
              },
              colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
              modifier = Modifier
                .clip(MaterialTheme.shapes.medium)
                .clickable {
                  onSelect(folder)
                  onDismiss()
                },
            )
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
fun ApiKeyRequiredDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    icon = { Icon(Icons.Outlined.Key, contentDescription = null) },
    title = { Text("Add a Gemini API key") },
    text = {
      Text("Page analysis and the study assistant use Google Gemini. Add your free API key in Settings to turn them on.")
    },
    confirmButton = {
      Button(onClick = {
        onDismiss()
        onOpenSettings()
      }) { Text("Open settings") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
  )
}
