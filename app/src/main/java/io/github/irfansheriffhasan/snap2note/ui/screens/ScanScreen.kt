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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.github.irfansheriffhasan.snap2note.ui.NoteViewModel
import io.github.irfansheriffhasan.snap2note.ui.components.pageCountLabel
import io.github.irfansheriffhasan.snap2note.ui.components.rememberTakePicture

/**
 * Multi-page scanning: capture a page, review it, keep it and the camera opens again
 * for the next page. Finish with Done.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
  folderId: Long,
  viewModel: NoteViewModel,
  onDone: () -> Unit,
) {
  val folders by viewModel.folders.collectAsStateWithLifecycle()
  val pagesFlow = remember(folderId) { viewModel.pages(folderId) }
  val pages by pagesFlow.collectAsStateWithLifecycle()
  val folderName = folders?.firstOrNull { it.id == folderId }?.name.orEmpty()

  var reviewUri by rememberSaveable { mutableStateOf<Uri?>(null) }
  var sessionPageIds by rememberSaveable { mutableStateOf(listOf<Long>()) }
  var cameraOpenedOnce by rememberSaveable { mutableStateOf(false) }
  var isSaving by remember { mutableStateOf(false) }

  val takePicture = rememberTakePicture(
    createUri = viewModel::newCaptureUri,
    onCaptured = { reviewUri = it },
    onCancelled = viewModel::discardCapture,
  )

  LaunchedEffect(Unit) {
    if (!cameraOpenedOnce) {
      cameraOpenedOnce = true
      takePicture()
    }
  }

  fun discardReview() {
    reviewUri?.let(viewModel::discardCapture)
    reviewUri = null
  }

  BackHandler(enabled = reviewUri != null || isSaving) {
    if (!isSaving) discardReview()
  }

  val sessionPages = pages.orEmpty().filter { it.id in sessionPageIds }

  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(onClick = {
            discardReview()
            onDone()
          }) { Icon(Icons.Filled.Close, contentDescription = "Close scanner") }
        },
        title = {
          Column {
            Text("Scan pages")
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
          TextButton(
            onClick = {
              discardReview()
              onDone()
            },
            enabled = !isSaving,
          ) { Text("Done") }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .navigationBarsPadding()
        .padding(16.dp),
    ) {
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .clip(MaterialTheme.shapes.extraLarge)
          .background(if (reviewUri != null) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center,
      ) {
        val uri = reviewUri
        if (uri != null) {
          AsyncImage(
            model = uri,
            contentDescription = "Captured page",
            contentScale = ContentScale.Fit,
            modifier = Modifier
              .fillMaxSize()
              .padding(8.dp),
          )
        } else {
          ReadyToScan(nextPageNumber = sessionPageIds.size + 1, onOpenCamera = takePicture)
        }
      }

      Spacer(Modifier.height(16.dp))

      if (reviewUri != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          OutlinedButton(
            onClick = {
              discardReview()
              takePicture()
            },
            enabled = !isSaving,
            modifier = Modifier
              .weight(1f)
              .height(52.dp),
          ) {
            Icon(Icons.Outlined.Replay, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Retake")
          }
          Button(
            onClick = {
              val uri = reviewUri ?: return@Button
              isSaving = true
              viewModel.addPages(folderId, listOf(uri)) { ids ->
                sessionPageIds = sessionPageIds + ids
                reviewUri = null
                isSaving = false
                takePicture()
              }
            },
            enabled = !isSaving,
            modifier = Modifier
              .weight(1f)
              .height(52.dp),
          ) {
            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Keep & next")
          }
        }
      }

      if (sessionPages.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        Text(
          "${pageCountLabel(sessionPages.size)} added",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
          items(sessionPages, key = { it.id }) { page ->
            AsyncImage(
              model = viewModel.pageFile(page),
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier
                .height(72.dp)
                .aspectRatio(3f / 4f)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ReadyToScan(nextPageNumber: Int, onOpenCamera: () -> Unit) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.padding(32.dp),
  ) {
    Box(
      modifier = Modifier
        .size(88.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.primaryContainer),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        Icons.Outlined.CameraAlt,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(40.dp),
      )
    }
    Spacer(Modifier.height(20.dp))
    Text("Ready for page $nextPageNumber", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(6.dp))
    Text(
      "Lay the page flat in good light and keep the whole page in frame.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(20.dp))
    Button(onClick = onOpenCamera) {
      Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
      Spacer(Modifier.width(ButtonDefaults.IconSpacing))
      Text("Open camera")
    }
  }
}
