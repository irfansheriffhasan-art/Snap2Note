package io.github.irfansheriffhasan.snap2note.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Small user preferences store. The Gemini API key lives in app-private storage and is
 * excluded from backups (see res/xml/backup_rules.xml and data_extraction_rules.xml).
 */
class SettingsRepository(context: Context) {
  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _themeMode = MutableStateFlow(readThemeMode())
  val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

  private val _apiKey = MutableStateFlow(prefs.getString(KEY_API_KEY, null).orEmpty())
  val apiKey: StateFlow<String> = _apiKey.asStateFlow()

  fun setThemeMode(mode: ThemeMode) {
    prefs.edit { putString(KEY_THEME_MODE, mode.name) }
    _themeMode.value = mode
  }

  fun setApiKey(key: String) {
    val trimmed = key.trim()
    prefs.edit { if (trimmed.isEmpty()) remove(KEY_API_KEY) else putString(KEY_API_KEY, trimmed) }
    _apiKey.value = trimmed
  }

  private fun readThemeMode(): ThemeMode =
    prefs.getString(KEY_THEME_MODE, null)
      ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
      ?: ThemeMode.SYSTEM

  private companion object {
    const val PREFS_NAME = "settings"
    const val KEY_THEME_MODE = "theme_mode"
    const val KEY_API_KEY = "gemini_api_key"
  }
}
