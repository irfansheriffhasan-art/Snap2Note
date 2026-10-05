package io.github.irfansheriffhasan.snap2note.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val BaseTypography = Typography()

private val Snap2NoteTypography = Typography(
  headlineLarge = BaseTypography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
  headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
  headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
  titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
  titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
  titleSmall = BaseTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
  labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

private val Snap2NoteShapes = Shapes(
  extraSmall = RoundedCornerShape(6.dp),
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(14.dp),
  large = RoundedCornerShape(20.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun Snap2NoteTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = if (darkTheme) DarkColors else LightColors,
    typography = Snap2NoteTypography,
    shapes = Snap2NoteShapes,
    content = content,
  )
}
