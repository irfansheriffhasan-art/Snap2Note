package io.github.irfansheriffhasan.snap2note.ui.components

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Returns a function that opens the system camera app. The pending photo URI is saved
 * across process death, which is common on low-memory devices while the camera is open.
 */
@Composable
fun rememberTakePicture(
  createUri: () -> Uri,
  onCaptured: (Uri) -> Unit,
  onCancelled: (Uri) -> Unit = {},
): () -> Unit {
  val context = LocalContext.current
  var pendingUri by rememberSaveable { mutableStateOf<Uri?>(null) }
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
    val uri = pendingUri ?: return@rememberLauncherForActivityResult
    pendingUri = null
    if (success) onCaptured(uri) else onCancelled(uri)
  }
  return {
    val uri = createUri()
    pendingUri = uri
    try {
      launcher.launch(uri)
    } catch (e: ActivityNotFoundException) {
      pendingUri = null
      Toast.makeText(context, "No camera app found on this device.", Toast.LENGTH_SHORT).show()
    } catch (e: IllegalStateException) {
      // The screen was closed before the camera could be opened.
      pendingUri = null
    }
  }
}

/** Returns a function that opens the system photo picker for one or more images. */
@Composable
fun rememberPickImages(onPicked: (List<Uri>) -> Unit): () -> Unit {
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
    if (uris.isNotEmpty()) onPicked(uris)
  }
  return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}
