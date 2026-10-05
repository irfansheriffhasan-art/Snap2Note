package io.github.irfansheriffhasan.snap2note.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.net.toUri

fun Context.sharePdf(uri: Uri, title: String) {
  val intent = Intent(Intent.ACTION_SEND).apply {
    type = "application/pdf"
    putExtra(Intent.EXTRA_STREAM, uri)
    putExtra(Intent.EXTRA_SUBJECT, title)
    clipData = ClipData.newRawUri(title, uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }
  startActivity(Intent.createChooser(intent, "Share PDF"))
}

fun Context.shareImages(uris: List<Uri>, title: String) {
  if (uris.isEmpty()) return
  val intent = if (uris.size == 1) {
    Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
  } else {
    Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
  }.apply {
    type = "image/jpeg"
    putExtra(Intent.EXTRA_SUBJECT, title)
    clipData = ClipData.newRawUri(title, uris.first()).also { clip ->
      uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
    }
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }
  startActivity(Intent.createChooser(intent, "Share pages"))
}

fun Context.openUrl(url: String) {
  try {
    startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
  } catch (e: ActivityNotFoundException) {
    Toast.makeText(this, "No browser found to open $url", Toast.LENGTH_SHORT).show()
  }
}

fun Context.copyToClipboard(label: String, text: String) {
  val clipboard = getSystemService(ClipboardManager::class.java) ?: return
  clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
  // Android 13+ shows its own confirmation when something is copied.
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
    Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
  }
}
