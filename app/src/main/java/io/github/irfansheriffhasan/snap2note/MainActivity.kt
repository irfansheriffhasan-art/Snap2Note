package io.github.irfansheriffhasan.snap2note

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.irfansheriffhasan.snap2note.data.ThemeMode
import io.github.irfansheriffhasan.snap2note.ui.NoteViewModel
import io.github.irfansheriffhasan.snap2note.ui.Snap2NoteApp
import io.github.irfansheriffhasan.snap2note.ui.theme.Snap2NoteTheme

class MainActivity : ComponentActivity() {
  private val viewModel: NoteViewModel by viewModels { NoteViewModel.Factory }

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    setContent {
      val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
      val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
      }

      // Keep the system bar icons legible when the in-app theme differs from the system theme.
      DisposableEffect(darkTheme) {
        enableEdgeToEdge(
          statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
          navigationBarStyle = SystemBarStyle.auto(LightScrim, DarkScrim) { darkTheme },
        )
        onDispose {}
      }

      Snap2NoteTheme(darkTheme = darkTheme) {
        Snap2NoteApp(viewModel = viewModel)
      }
    }
  }

  private companion object {
    val LightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
    val DarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
  }
}
