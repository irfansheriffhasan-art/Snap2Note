package io.github.irfansheriffhasan.snap2note.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.irfansheriffhasan.snap2note.R
import io.github.irfansheriffhasan.snap2note.data.FolderWithStats
import io.github.irfansheriffhasan.snap2note.ui.theme.BrandBlue
import io.github.irfansheriffhasan.snap2note.ui.theme.BrandCyan
import java.io.File

fun pageCountLabel(count: Int): String = if (count == 1) "1 page" else "$count pages"

fun FolderWithStats.statsLabel(): String = buildString {
  append(pageCountLabel(pageCount))
  if (analyzedCount > 0) append(" · $analyzedCount analyzed")
}

/** The app's logo: the launcher icon artwork on the brand gradient. */
@Composable
fun LogoMark(size: Dp = 32.dp) {
  Box(
    modifier = Modifier
      .size(size)
      .clip(RoundedCornerShape(size * 0.28f))
      .background(Brush.linearGradient(listOf(BrandBlue, BrandCyan))),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      painter = painterResource(R.drawable.ic_launcher_foreground),
      contentDescription = null,
      tint = Color.Unspecified,
      modifier = Modifier.requiredSize(size * 1.65f),
    )
  }
}

@Composable
fun EmptyState(
  icon: ImageVector,
  title: String,
  message: String,
  modifier: Modifier = Modifier,
  actions: @Composable (() -> Unit)? = null,
) {
  Column(
    modifier = modifier.padding(horizontal = 32.dp, vertical = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier = Modifier
        .size(72.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.secondaryContainer),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(32.dp))
    }
    Spacer(Modifier.height(20.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(6.dp))
    Text(
      message,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    if (actions != null) {
      Spacer(Modifier.height(20.dp))
      actions()
    }
  }
}

@Composable
fun SectionHeader(
  title: String,
  modifier: Modifier = Modifier,
  actionLabel: String? = null,
  onAction: () -> Unit = {},
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(top = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
    if (actionLabel != null) {
      TextButton(onClick = onAction) { Text(actionLabel) }
    }
  }
}

/** Shows the folder's first page, or a tinted placeholder for empty folders. */
@Composable
fun FolderCover(name: String, coverFile: File?, modifier: Modifier = Modifier, iconSize: Dp = 28.dp) {
  val (container, content) = when (Math.floorMod(name.hashCode(), 3)) {
    0 -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    1 -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    else -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
  }
  Box(modifier.background(container), contentAlignment = Alignment.Center) {
    if (coverFile != null) {
      AsyncImage(
        model = coverFile,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.matchParentSize(),
      )
    } else {
      Icon(Icons.Outlined.Folder, contentDescription = null, tint = content, modifier = Modifier.size(iconSize))
    }
  }
}

@Composable
fun FolderListItem(
  folder: FolderWithStats,
  coverFile: File?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  Surface(
    onClick = onClick,
    enabled = enabled,
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
      FolderCover(
        name = folder.name,
        coverFile = coverFile,
        modifier = Modifier
          .size(52.dp)
          .clip(MaterialTheme.shapes.medium),
        iconSize = 24.dp,
      )
      Spacer(Modifier.width(16.dp))
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(folder.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(folder.statsLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
