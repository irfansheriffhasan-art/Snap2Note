package io.github.irfansheriffhasan.snap2note

import android.app.Application
import io.github.irfansheriffhasan.snap2note.ai.GeminiClient
import io.github.irfansheriffhasan.snap2note.data.AppDatabase
import io.github.irfansheriffhasan.snap2note.data.NoteRepository
import io.github.irfansheriffhasan.snap2note.data.SettingsRepository

/** Holds the app-wide singletons used by the view models. */
class Snap2NoteApplication : Application() {
  val settings by lazy { SettingsRepository(this) }
  val repository by lazy { NoteRepository(this, AppDatabase.create(this)) }
  val gemini by lazy { GeminiClient(apiKey = { settings.apiKey.value }) }
}
