package io.github.irfansheriffhasan.snap2note.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateLeft
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.InvertColors
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.scale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.irfansheriffhasan.snap2note.data.CropRect
import io.github.irfansheriffhasan.snap2note.data.ImageProcessing.oriented
import io.github.irfansheriffhasan.snap2note.data.PageEdits
import io.github.irfansheriffhasan.snap2note.data.PageFilter
import io.github.irfansheriffhasan.snap2note.ui.NoteViewModel
import io.github.irfansheriffhasan.snap2note.ui.components.ConfirmDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class EditorTool(val label: String, val icon: ImageVector) {
  CROP("Crop & rotate", Icons.Outlined.Crop),
  ADJUST("Adjust", Icons.Outlined.Tune),
  FILTERS("Filters", Icons.Outlined.AutoAwesome),
}

private val EditorBackdrop = Color(0xFF1B1B1F)

private const val PREVIEW_SIDE = 1600
private const val THUMBNAIL_SIDE = 160

/** Smallest crop, as a fraction of the image side, so the handles never collapse together. */
private const val MIN_CROP_FRACTION = 0.08f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditorScreen(
  folderId: Long,
  pageId: Long,
  viewModel: NoteViewModel,
  onClose: () -> Unit,
) {
  val pagesFlow = remember(folderId) { viewModel.pages(folderId) }
  val pages by pagesFlow.collectAsStateWithLifecycle()
  val page = pages?.firstOrNull { it.id == pageId }

  var edits by rememberSaveable(stateSaver = PageEditsSaver) { mutableStateOf(PageEdits()) }
  var tool by rememberSaveable { mutableIntStateOf(0) }
  var saving by remember { mutableStateOf(false) }
  var showDiscardDialog by rememberSaveable { mutableStateOf(false) }

  // Load the preview once per page; later file name changes come from our own save.
  val base by produceState<Bitmap?>(initialValue = null, page?.id) {
    value = page?.let { viewModel.loadPageBitmap(it, PREVIEW_SIDE) }
  }
  val oriented by produceState<Bitmap?>(initialValue = null, base, edits.rotation, edits.flipHorizontal) {
    val source = base ?: return@produceState
    value = withContext(Dispatchers.Default) { source.oriented(edits.rotation, edits.flipHorizontal) }
  }

  LaunchedEffect(pages, page) {
    if (pages != null && page == null) onClose()
  }

  val requestClose = {
    if (edits.isUnchanged || saving) onClose() else showDiscardDialog = true
  }
  BackHandler(onBack = requestClose)

  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(onClick = requestClose) { Icon(Icons.Outlined.Close, contentDescription = "Close editor") }
        },
        title = { Text("Edit page") },
        actions = {
          TextButton(onClick = { edits = PageEdits() }, enabled = !edits.isUnchanged && !saving) { Text("Reset") }
          Button(
            onClick = {
              val current = page ?: return@Button
              saving = true
              viewModel.editPage(current, edits) { saved ->
                saving = false
                if (saved) onClose()
              }
            },
            enabled = !edits.isUnchanged && !saving && page != null,
            modifier = Modifier.padding(end = 8.dp),
          ) {
            if (saving) {
              CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
              Text("Save")
            }
          }
        },
      )
    },
    bottomBar = {
      NavigationBar {
        EditorTool.entries.forEachIndexed { index, entry ->
          NavigationBarItem(
            selected = tool == index,
            onClick = { tool = index },
            icon = { Icon(entry.icon, contentDescription = null) },
            label = { Text(entry.label) },
          )
        }
      }
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
    ) {
      val image = oriented
      val selectedTool = EditorTool.entries[tool]
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          // A dark backdrop keeps the white crop frame visible in both themes.
          .background(EditorBackdrop),
        contentAlignment = Alignment.Center,
      ) {
        if (image == null) {
          CircularProgressIndicator()
        } else {
          val imageBitmap = remember(image) { image.asImageBitmap() }
          val colorFilter = remember(edits) { edits.colorFilter() }
          EditorCanvas(
            image = imageBitmap,
            crop = edits.crop,
            cropping = selectedTool == EditorTool.CROP,
            colorFilter = colorFilter,
            onCropChange = { edits = edits.copy(crop = it) },
            modifier = Modifier
              .fillMaxSize()
              .padding(20.dp),
          )
        }
      }

      when (selectedTool) {
        EditorTool.CROP -> CropTools(
          edits = edits,
          onEditsChange = { edits = it },
        )
        EditorTool.ADJUST -> AdjustTools(edits = edits, onEditsChange = { edits = it })
        EditorTool.FILTERS -> FilterTools(
          image = image,
          edits = edits,
          onSelect = { edits = edits.copy(filter = it) },
        )
      }
    }
  }

  if (showDiscardDialog) {
    ConfirmDialog(
      title = "Discard changes?",
      message = "Your edits to this page haven't been saved.",
      confirmLabel = "Discard",
      onConfirm = onClose,
      onDismiss = { showDiscardDialog = false },
    )
  }
}

private fun PageEdits.colorFilter(): ColorFilter? =
  if (hasColorChanges) ColorFilter.colorMatrix(ColorMatrix(colorMatrix())) else null

private enum class CropHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, LEFT, TOP, RIGHT, BOTTOM, MOVE }

/**
 * Draws the page. While [cropping], the whole image is shown with a movable crop frame;
 * otherwise only the cropped area is shown, as it will be saved.
 */
@Composable
private fun EditorCanvas(
  image: ImageBitmap,
  crop: CropRect,
  cropping: Boolean,
  colorFilter: ColorFilter?,
  onCropChange: (CropRect) -> Unit,
  modifier: Modifier = Modifier,
) {
  val currentCrop by rememberUpdatedState(crop)
  val currentOnCropChange by rememberUpdatedState(onCropChange)
  val frameColor = Color.White
  val scrim = Color.Black.copy(alpha = 0.55f)

  Canvas(
    modifier = modifier.then(
      if (cropping) {
        Modifier.pointerInput(image) {
          val touchRadius = 28.dp.toPx()
          var handle: CropHandle? = null
          var touchStart = Offset.Zero
          var cropAtStart = currentCrop
          detectDragGestures(
            onDragStart = { start ->
              handle = pickHandle(start, imageRect(size.width.toFloat(), size.height.toFloat(), image), currentCrop, touchRadius)
              touchStart = start
              cropAtStart = currentCrop
            },
            onDragEnd = { handle = null },
            onDragCancel = { handle = null },
          ) { change, _ ->
            val active = handle ?: return@detectDragGestures
            change.consume()
            // Measure from where the finger went down, so the frame follows it exactly
            // instead of lagging behind by the touch slop.
            val bounds = imageRect(size.width.toFloat(), size.height.toFloat(), image)
            val moved = change.position - touchStart
            currentOnCropChange(
              dragCrop(cropAtStart, active, moved.x / bounds.width, moved.y / bounds.height),
            )
          }
        }
      } else {
        Modifier
      },
    ),
  ) {
    if (cropping) {
      val bounds = imageRect(size.width, size.height, image)
      drawImageInto(image, srcRect = null, dst = bounds, colorFilter = colorFilter)
      val frame = Rect(
        left = bounds.left + crop.left * bounds.width,
        top = bounds.top + crop.top * bounds.height,
        right = bounds.left + crop.right * bounds.width,
        bottom = bounds.top + crop.bottom * bounds.height,
      )
      drawCropOverlay(bounds, frame, scrim, frameColor)
    } else {
      val src = Rect(
        left = crop.left * image.width,
        top = crop.top * image.height,
        right = crop.right * image.width,
        bottom = crop.bottom * image.height,
      )
      val dst = fitRect(size.width, size.height, src.width, src.height)
      drawImageInto(image, srcRect = src, dst = dst, colorFilter = colorFilter)
    }
  }
}

private fun imageRect(width: Float, height: Float, image: ImageBitmap): Rect =
  fitRect(width, height, image.width.toFloat(), image.height.toFloat())

/** The largest rectangle with the given aspect ratio, centered in the available space. */
private fun fitRect(width: Float, height: Float, contentWidth: Float, contentHeight: Float): Rect {
  if (contentWidth <= 0f || contentHeight <= 0f) return Rect(0f, 0f, width, height)
  val scale = min(width / contentWidth, height / contentHeight)
  val w = contentWidth * scale
  val h = contentHeight * scale
  val left = (width - w) / 2f
  val top = (height - h) / 2f
  return Rect(left, top, left + w, top + h)
}

private fun DrawScope.drawImageInto(image: ImageBitmap, srcRect: Rect?, dst: Rect, colorFilter: ColorFilter?) {
  val src = srcRect ?: Rect(0f, 0f, image.width.toFloat(), image.height.toFloat())
  drawImage(
    image = image,
    srcOffset = IntOffset(src.left.roundToInt(), src.top.roundToInt()),
    srcSize = IntSize(
      src.width.roundToInt().coerceIn(1, image.width - src.left.roundToInt()),
      src.height.roundToInt().coerceIn(1, image.height - src.top.roundToInt()),
    ),
    dstOffset = IntOffset(dst.left.roundToInt(), dst.top.roundToInt()),
    dstSize = IntSize(dst.width.roundToInt().coerceAtLeast(1), dst.height.roundToInt().coerceAtLeast(1)),
    colorFilter = colorFilter,
  )
}

private fun DrawScope.drawCropOverlay(bounds: Rect, frame: Rect, scrim: Color, frameColor: Color) {
  // Dim everything outside the crop frame.
  drawRect(scrim, Offset(bounds.left, bounds.top), Size(bounds.width, frame.top - bounds.top))
  drawRect(scrim, Offset(bounds.left, frame.bottom), Size(bounds.width, bounds.bottom - frame.bottom))
  drawRect(scrim, Offset(bounds.left, frame.top), Size(frame.left - bounds.left, frame.height))
  drawRect(scrim, Offset(frame.right, frame.top), Size(bounds.right - frame.right, frame.height))

  // Rule-of-thirds guides.
  val guide = frameColor.copy(alpha = 0.45f)
  val thin = 1.dp.toPx()
  for (i in 1..2) {
    val x = frame.left + frame.width * i / 3f
    val y = frame.top + frame.height * i / 3f
    drawLine(guide, Offset(x, frame.top), Offset(x, frame.bottom), thin)
    drawLine(guide, Offset(frame.left, y), Offset(frame.right, y), thin)
  }
  drawRect(frameColor, frame.topLeft, frame.size, style = Stroke(1.5.dp.toPx()))

  // Corner and edge handles.
  val stroke = 4.dp.toPx()
  val arm = min(22.dp.toPx(), min(frame.width, frame.height) / 2f)
  val corners = listOf(
    Triple(frame.topLeft, 1f, 1f),
    Triple(frame.topRight, -1f, 1f),
    Triple(frame.bottomLeft, 1f, -1f),
    Triple(frame.bottomRight, -1f, -1f),
  )
  corners.forEach { (point, dx, dy) ->
    drawLine(frameColor, point, point + Offset(arm * dx, 0f), stroke)
    drawLine(frameColor, point, point + Offset(0f, arm * dy), stroke)
  }
  val bar = min(18.dp.toPx(), min(frame.width, frame.height) / 4f)
  val center = frame.center
  drawLine(frameColor, Offset(center.x - bar, frame.top), Offset(center.x + bar, frame.top), stroke)
  drawLine(frameColor, Offset(center.x - bar, frame.bottom), Offset(center.x + bar, frame.bottom), stroke)
  drawLine(frameColor, Offset(frame.left, center.y - bar), Offset(frame.left, center.y + bar), stroke)
  drawLine(frameColor, Offset(frame.right, center.y - bar), Offset(frame.right, center.y + bar), stroke)
}

private fun pickHandle(touch: Offset, bounds: Rect, crop: CropRect, radius: Float): CropHandle? {
  val left = bounds.left + crop.left * bounds.width
  val top = bounds.top + crop.top * bounds.height
  val right = bounds.left + crop.right * bounds.width
  val bottom = bounds.top + crop.bottom * bounds.height
  fun near(a: Float, b: Float) = abs(a - b) <= radius
  val withinX = touch.x in (left - radius)..(right + radius)
  val withinY = touch.y in (top - radius)..(bottom + radius)
  return when {
    near(touch.x, left) && near(touch.y, top) -> CropHandle.TOP_LEFT
    near(touch.x, right) && near(touch.y, top) -> CropHandle.TOP_RIGHT
    near(touch.x, left) && near(touch.y, bottom) -> CropHandle.BOTTOM_LEFT
    near(touch.x, right) && near(touch.y, bottom) -> CropHandle.BOTTOM_RIGHT
    near(touch.x, left) && withinY -> CropHandle.LEFT
    near(touch.x, right) && withinY -> CropHandle.RIGHT
    near(touch.y, top) && withinX -> CropHandle.TOP
    near(touch.y, bottom) && withinX -> CropHandle.BOTTOM
    touch.x in left..right && touch.y in top..bottom -> CropHandle.MOVE
    else -> null
  }
}

/** Moves the dragged edges by the given fractions, keeping the crop inside the image. */
private fun dragCrop(crop: CropRect, handle: CropHandle, dx: Float, dy: Float): CropRect {
  if (handle == CropHandle.MOVE) {
    val moveX = dx.coerceIn(-crop.left, 1f - crop.right)
    val moveY = dy.coerceIn(-crop.top, 1f - crop.bottom)
    return CropRect(crop.left + moveX, crop.top + moveY, crop.right + moveX, crop.bottom + moveY)
  }
  var (left, top, right, bottom) = crop
  if (handle in setOf(CropHandle.LEFT, CropHandle.TOP_LEFT, CropHandle.BOTTOM_LEFT)) {
    left = (left + dx).coerceIn(0f, right - MIN_CROP_FRACTION)
  }
  if (handle in setOf(CropHandle.RIGHT, CropHandle.TOP_RIGHT, CropHandle.BOTTOM_RIGHT)) {
    right = (right + dx).coerceIn(left + MIN_CROP_FRACTION, 1f)
  }
  if (handle in setOf(CropHandle.TOP, CropHandle.TOP_LEFT, CropHandle.TOP_RIGHT)) {
    top = (top + dy).coerceIn(0f, bottom - MIN_CROP_FRACTION)
  }
  if (handle in setOf(CropHandle.BOTTOM, CropHandle.BOTTOM_LEFT, CropHandle.BOTTOM_RIGHT)) {
    bottom = (bottom + dy).coerceIn(top + MIN_CROP_FRACTION, 1f)
  }
  return CropRect(left, top, right, bottom)
}

@Composable
private fun CropTools(edits: PageEdits, onEditsChange: (PageEdits) -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      "Drag the corners or edges to crop. Drag inside the frame to move it.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
      ToolButton(Icons.AutoMirrored.Outlined.RotateLeft, "Left") { onEditsChange(edits.rotatedCounterClockwise()) }
      ToolButton(Icons.AutoMirrored.Outlined.RotateRight, "Right") { onEditsChange(edits.rotatedClockwise()) }
      ToolButton(Icons.Outlined.Flip, "Flip") { onEditsChange(edits.flipped()) }
      ToolButton(Icons.Outlined.CropFree, "Full") { onEditsChange(edits.copy(crop = CropRect.Full)) }
    }
  }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, onClick: () -> Unit) {
  FilledTonalButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(6.dp))
    Text(label)
  }
}

@Composable
private fun AdjustTools(edits: PageEdits, onEditsChange: (PageEdits) -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 4.dp),
  ) {
    AdjustSlider(Icons.Outlined.BrightnessMedium, "Brightness", edits.brightness) { onEditsChange(edits.copy(brightness = it)) }
    AdjustSlider(Icons.Outlined.Contrast, "Contrast", edits.contrast) { onEditsChange(edits.copy(contrast = it)) }
    AdjustSlider(Icons.Outlined.InvertColors, "Saturation", edits.saturation) { onEditsChange(edits.copy(saturation = it)) }
    AdjustSlider(Icons.Outlined.Thermostat, "Warmth", edits.warmth) { onEditsChange(edits.copy(warmth = it)) }
  }
}

@Composable
private fun AdjustSlider(icon: ImageVector, label: String, value: Float, onChange: (Float) -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    Text(
      label,
      style = MaterialTheme.typography.labelLarge,
      modifier = Modifier
        .padding(start = 8.dp)
        .width(84.dp)
        // Tapping the label resets the slider.
        .clickable { onChange(0f) },
    )
    Slider(
      value = value,
      onValueChange = { onChange(if (abs(it) < 0.03f) 0f else it) },
      valueRange = -1f..1f,
      modifier = Modifier.weight(1f),
    )
    Text(
      (value * 100).roundToInt().let { if (it > 0) "+$it" else "$it" },
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.End,
      modifier = Modifier.width(40.dp),
    )
  }
}

@Composable
private fun FilterTools(image: Bitmap?, edits: PageEdits, onSelect: (PageFilter) -> Unit) {
  val thumbnail = remember(image, edits.crop) { image?.let { thumbnailOf(it, edits.crop)?.asImageBitmap() } }
  LazyRow(
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    items(PageFilter.entries) { filter ->
      val selected = filter == edits.filter
      val preview = edits.copy(filter = filter)
      val colorFilter = remember(preview) { preview.colorFilter() }
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onSelect(filter) },
      ) {
        Box(
          modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
              width = if (selected) 3.dp else 1.dp,
              color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
              shape = RoundedCornerShape(12.dp),
            )
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
          if (thumbnail != null) {
            Image(
              bitmap = thumbnail,
              contentDescription = null,
              contentScale = ContentScale.Crop,
              colorFilter = colorFilter,
              modifier = Modifier.fillMaxSize(),
            )
          }
        }
        Spacer(Modifier.height(6.dp))
        Text(
          filter.label,
          style = MaterialTheme.typography.labelMedium,
          color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }
}

private fun thumbnailOf(image: Bitmap, crop: CropRect): Bitmap? {
  val x = (crop.left * image.width).roundToInt().coerceIn(0, image.width - 1)
  val y = (crop.top * image.height).roundToInt().coerceIn(0, image.height - 1)
  val w = (crop.width * image.width).roundToInt().coerceIn(1, image.width - x)
  val h = (crop.height * image.height).roundToInt().coerceIn(1, image.height - y)
  val ratio = THUMBNAIL_SIDE.toFloat() / max(w, h)
  return try {
    val region = Bitmap.createBitmap(image, x, y, w, h)
    if (ratio >= 1f) region else region.scale((w * ratio).roundToInt().coerceAtLeast(1), (h * ratio).roundToInt().coerceAtLeast(1))
  } catch (e: IllegalArgumentException) {
    null
  }
}

private val PageEditsSaver = Saver<PageEdits, List<Any>>(
  save = {
    listOf(
      it.rotation, it.flipHorizontal, it.crop.left, it.crop.top, it.crop.right, it.crop.bottom,
      it.filter.name, it.brightness, it.contrast, it.saturation, it.warmth,
    )
  },
  restore = {
    PageEdits(
      rotation = it[0] as Int,
      flipHorizontal = it[1] as Boolean,
      crop = CropRect(it[2] as Float, it[3] as Float, it[4] as Float, it[5] as Float),
      filter = PageFilter.valueOf(it[6] as String),
      brightness = it[7] as Float,
      contrast = it[8] as Float,
      saturation = it[9] as Float,
      warmth = it[10] as Float,
    )
  },
)
