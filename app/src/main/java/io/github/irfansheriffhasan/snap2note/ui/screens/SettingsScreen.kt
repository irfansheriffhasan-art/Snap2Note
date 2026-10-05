package io.github.irfansheriffhasan.snap2note.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.irfansheriffhasan.snap2note.BuildConfig
import io.github.irfansheriffhasan.snap2note.data.ThemeMode
import io.github.irfansheriffhasan.snap2note.ui.NoteViewModel
import io.github.irfansheriffhasan.snap2note.ui.components.openUrl

private const val API_KEY_URL = "https://aistudio.google.com/apikey"
private const val SOURCE_URL = "https://github.com/irfansheriffhasan-art/Snap2Note"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: NoteViewModel) {
  val context = LocalContext.current
  val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
  val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
  val storageUsed by viewModel.storageUsedBytes.collectAsStateWithLifecycle()
  var showApiKeyDialog by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(Unit) { viewModel.refreshStorageUsed() }

  Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { innerPadding ->
    LazyColumn(
      contentPadding = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = innerPadding.calculateTopPadding() + 8.dp,
        bottom = innerPadding.calculateBottomPadding() + 24.dp,
      ),
      verticalArrangement = Arrangement.spacedBy(20.dp),
      modifier = Modifier.fillMaxSize(),
    ) {
      item {
        SettingsGroup("Appearance") {
          SettingsRow(icon = Icons.Outlined.Palette, title = "Theme")
          SingleChoiceSegmentedButtonRow(
            modifier = Modifier
              .fillMaxWidth()
              .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
          ) {
            val options = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")
            options.forEachIndexed { index, (mode, label) ->
              SegmentedButton(
                selected = themeMode == mode,
                onClick = { viewModel.setThemeMode(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(label) },
              )
            }
          }
        }
      }

      item {
        SettingsGroup("AI features") {
          SettingsRow(
            icon = Icons.Outlined.Key,
            title = "Gemini API key",
            subtitle = if (apiKey.isBlank()) {
              "Not set. Needed for page analysis and the study assistant."
            } else {
              "Connected · ending in ${apiKey.takeLast(4)}"
            },
            trailing = {
              TextButton(onClick = { showApiKeyDialog = true }) { Text(if (apiKey.isBlank()) "Add" else "Change") }
            },
            onClick = { showApiKeyDialog = true },
          )
          HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
          SettingsRow(
            icon = Icons.AutoMirrored.Outlined.OpenInNew,
            title = "Get a free API key",
            subtitle = "Create one in Google AI Studio",
            onClick = { context.openUrl(API_KEY_URL) },
          )
        }
      }

      item {
        SettingsGroup("Storage") {
          SettingsRow(
            icon = Icons.Outlined.Storage,
            title = "Pages on this device",
            subtitle = storageUsed?.let { Formatter.formatShortFileSize(context, it) } ?: "Calculating…",
          )
          HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
          SettingsRow(
            icon = Icons.Outlined.DeleteSweep,
            title = "Clear temporary files",
            subtitle = "Removes exported PDFs and unsaved camera captures",
            onClick = viewModel::clearCache,
          )
        }
      }

      item {
        SettingsGroup("About") {
          SettingsRow(
            icon = Icons.Outlined.Lock,
            title = "Privacy",
            subtitle = "Your pages stay on this device. A page is only sent to Google Gemini when you analyze it " +
              "or ask the study assistant about it.",
          )
          HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
          SettingsRow(
            icon = Icons.Outlined.Code,
            title = "Source code",
            subtitle = "View the project on GitHub",
            onClick = { context.openUrl(SOURCE_URL) },
          )
          HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
          SettingsRow(icon = Icons.Outlined.Info, title = "Version", subtitle = BuildConfig.VERSION_NAME)
        }
      }
    }
  }

  if (showApiKeyDialog) {
    ApiKeyDialog(
      currentKey = apiKey,
      onSave = viewModel::setApiKey,
      onGetKey = { context.openUrl(API_KEY_URL) },
      onDismiss = { showApiKeyDialog = false },
    )
  }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
  Column {
    Text(
      title,
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
    )
    Surface(
      shape = MaterialTheme.shapes.large,
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column { content() }
    }
  }
}

@Composable
private fun SettingsRow(
  icon: ImageVector,
  title: String,
  subtitle: String? = null,
  trailing: @Composable (() -> Unit)? = null,
  onClick: (() -> Unit)? = null,
) {
  val base = Modifier.fillMaxWidth()
  ListItem(
    headlineContent = { Text(title) },
    supportingContent = subtitle?.let { { Text(it) } },
    leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
    trailingContent = trailing,
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    modifier = if (onClick != null) base.clickable(onClick = onClick) else base,
  )
}

@Composable
private fun ApiKeyDialog(
  currentKey: String,
  onSave: (String) -> Unit,
  onGetKey: () -> Unit,
  onDismiss: () -> Unit,
) {
  var key by rememberSaveable { mutableStateOf(currentKey) }
  var visible by rememberSaveable { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    icon = { Icon(Icons.Outlined.Key, contentDescription = null) },
    title = { Text("Gemini API key") },
    text = {
      Column {
        Text(
          "Snap2Note uses your own Gemini API key. It's stored only on this device and is never included in backups.",
          style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
          value = key,
          onValueChange = { key = it.trim() },
          label = { Text("API key") },
          singleLine = true,
          visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
          trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
              Icon(
                if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                contentDescription = if (visible) "Hide key" else "Show key",
              )
            }
          },
          modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = onGetKey, contentPadding = PaddingValues(horizontal = 0.dp)) {
          Text("Get a free key from Google AI Studio")
        }
      }
    },
    confirmButton = {
      Button(onClick = {
        onSave(key)
        onDismiss()
      }) { Text("Save") }
    },
    dismissButton = {
      if (currentKey.isNotEmpty()) {
        TextButton(onClick = {
          onSave("")
          onDismiss()
        }) { Text("Remove") }
      } else {
        TextButton(onClick = onDismiss) { Text("Cancel") }
      }
    },
  )
}
