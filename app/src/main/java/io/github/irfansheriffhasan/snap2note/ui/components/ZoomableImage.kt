package io.github.irfansheriffhasan.snap2note.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

/** Full-screen image viewer with pinch-to-zoom, pan and double-tap to zoom. */
@Composable
fun FullScreenImageDialog(model: Any, contentDescription: String?, onDismiss: () -> Unit) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black),
    ) {
      ZoomableImage(model = model, contentDescription = contentDescription, modifier = Modifier.fillMaxSize())
      FilledTonalIconButton(
        onClick = onDismiss,
        modifier = Modifier
          .align(Alignment.TopEnd)
          .statusBarsPadding()
          .padding(12.dp),
      ) {
        Icon(Icons.Filled.Close, contentDescription = "Close")
      }
    }
  }
}

@Composable
fun ZoomableImage(model: Any, contentDescription: String?, modifier: Modifier = Modifier) {
  var scale by remember { mutableFloatStateOf(1f) }
  var offset by remember { mutableStateOf(Offset.Zero) }
  var size by remember { mutableStateOf(IntSize.Zero) }

  fun clampOffset(value: Offset, currentScale: Float): Offset {
    val maxX = size.width * (currentScale - 1f) / 2f
    val maxY = size.height * (currentScale - 1f) / 2f
    return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
  }

  Box(
    modifier = modifier
      .clipToBounds()
      .onSizeChanged { size = it }
      .pointerInput(Unit) {
        detectTapGestures(
          onDoubleTap = { tap ->
            if (scale > 1f) {
              scale = 1f
              offset = Offset.Zero
            } else {
              scale = 2.5f
              val center = Offset(size.width / 2f, size.height / 2f)
              offset = clampOffset((center - tap) * 1.5f, scale)
            }
          },
        )
      }
      .pointerInput(Unit) {
        detectTransformGestures { _, pan, zoom, _ ->
          scale = (scale * zoom).coerceIn(1f, 5f)
          offset = if (scale == 1f) Offset.Zero else clampOffset(offset + pan, scale)
        }
      },
  ) {
    AsyncImage(
      model = model,
      contentDescription = contentDescription,
      contentScale = ContentScale.Fit,
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
          translationX = offset.x
          translationY = offset.y
        },
    )
  }
}
