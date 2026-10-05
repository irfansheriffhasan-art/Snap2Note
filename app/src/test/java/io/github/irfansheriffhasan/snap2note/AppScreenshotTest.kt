package io.github.irfansheriffhasan.snap2note

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.irfansheriffhasan.snap2note.ai.Flashcard
import io.github.irfansheriffhasan.snap2note.ai.FlashcardsJson
import io.github.irfansheriffhasan.snap2note.ai.PageAnalysis
import io.github.irfansheriffhasan.snap2note.data.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

/**
 * Walks through the main screens with sample notes and records screenshots
 * (used in the README). Run with `./gradlew recordRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7, sdk = [36])
class AppScreenshotTest {
  @get:Rule val compose = createAndroidComposeRule<MainActivity>()

  private val app: Snap2NoteApplication get() = ApplicationProvider.getApplicationContext()

  @Before
  fun setUp() {
    Coil.setImageLoader(ImageLoader.Builder(app).dispatcher(Dispatchers.Unconfined).build())
  }

  @Test
  fun walkThroughMainScreens() {
    seedSampleNotes()
    app.settings.setApiKey("sample-key")

    waitForText("Biology")
    capture("home")

    compose.onAllNodes(hasText("Biology")).onFirst().performClick()
    waitForText("Study tools")
    capture("folder")

    compose.onAllNodes(hasContentDescription("Page 1")).onFirst().performClick()
    waitForText("Photosynthesis basics")
    capture("page-notes")

    compose.onAllNodes(hasText("Flashcards (3)")).onFirst().performClick()
    waitForText("Question 1")
    compose.onAllNodes(hasText("Question 1")).onFirst().performClick()
    capture("page-flashcards")

    pressBack()
    waitForText("Study tools")
    compose.onAllNodes(hasText("Ask")).onFirst().performClick()
    waitForText("Ask about your notes")
    capture("assistant")

    pressBack()
    pressBack()
    waitForText("Capture your notes")
    app.settings.setApiKey("")
    compose.onAllNodes(hasText("Settings")).onFirst().performClick()
    waitForText("Gemini API key")
    capture("settings")

    app.settings.setThemeMode(ThemeMode.DARK)
    compose.onAllNodes(hasText("Folders")).onFirst().performClick()
    waitForText("World History")
    compose.onAllNodes(hasContentDescription("New folder")).onFirst().assertExists()
    capture("folders-dark")

    compose.onAllNodes(hasText("Biology")).onFirst().performClick()
    waitForText("Study tools")
    compose.onAllNodes(hasContentDescription("Page 1")).onFirst().performClick()
    waitForText("Photosynthesis basics")
    capture("page-dark")
  }

  private fun waitForText(text: String) {
    compose.waitUntil(timeoutMillis = 15_000) {
      compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }
    settle()
  }

  private fun settle() {
    repeat(5) {
      compose.mainClock.advanceTimeBy(500)
      compose.waitForIdle()
    }
  }

  private fun pressBack() {
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    settle()
  }

  private fun capture(name: String) {
    settle()
    compose.onRoot().captureRoboImage("../docs/images/screenshots/$name.png")
  }

  private fun seedSampleNotes() = runBlocking {
    val repository = app.repository
    val history = repository.createFolder("World History")
    val physics = repository.createFolder("Physics 101")
    val biology = repository.createFolder("Biology")

    repository.addPages(history, listOf(note("The Industrial Revolution", HistoryLines)))
    repository.addPages(physics, listOf(note("Newton's Laws of Motion", PhysicsLines), note("Momentum", PhysicsLines.reversed())))
    repository.addPages(
      biology,
      listOf(
        note("Photosynthesis", BiologyLines),
        note("Cell Structure", BiologyLines.shuffled(Random(1))),
        note("Mitochondria", BiologyLines.reversed()),
        note("DNA Replication", BiologyLines.drop(2) + BiologyLines.take(2)),
        note("Enzymes", BiologyLines.shuffled(Random(7))),
      ),
    )

    val firstPage = repository.getPages(biology).first()
    repository.saveAnalysis(
      firstPage.id,
      PageAnalysis(
        title = "Photosynthesis basics",
        text = BiologyLines.joinToString("\n"),
        summary = """
          **Photosynthesis** is how plants turn light energy into chemical energy stored in glucose.

          - It takes place in the **chloroplasts**, using the green pigment **chlorophyll**.
          - The **light-dependent reactions** split water and release oxygen.
          - The **Calvin cycle** uses ATP and NADPH to fix carbon dioxide into glucose.

          Overall: 6CO2 + 6H2O + light → C6H12O6 + 6O2
        """.trimIndent(),
        flashcards = emptyList(),
      ),
      FlashcardsJson.encode(
        listOf(
          Flashcard("Where does photosynthesis take place?", "In the chloroplasts of plant cells."),
          Flashcard("Which gas is released by the light-dependent reactions?", "Oxygen, from splitting water."),
          Flashcard("What does the Calvin cycle produce?", "Glucose, made by fixing carbon dioxide."),
        ),
      ),
    )
  }

  private var noteCounter = 0

  /** Draws a ruled notebook page with a heading and a few lines of text. */
  private fun note(title: String, lines: List<String>): Uri {
    val width = 1200
    val height = 1600
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.rgb(253, 252, 247))

    val rule = Paint().apply { color = Color.rgb(196, 214, 238); strokeWidth = 3f }
    var y = 260f
    while (y < height) {
      canvas.drawLine(0f, y, width.toFloat(), y, rule)
      y += 80f
    }
    canvas.drawLine(150f, 0f, 150f, height.toFloat(), Paint().apply { color = Color.rgb(236, 150, 150); strokeWidth = 3f })

    val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 45, 95) }
    ink.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    ink.textSize = 72f
    canvas.drawText(title, 190f, 230f, ink)
    ink.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    ink.textSize = 46f
    lines.forEachIndexed { index, line -> canvas.drawText(line, 190f, 325f + index * 160f, ink) }

    val file = File(app.cacheDir, "sample_${noteCounter++}.jpg")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    bitmap.recycle()
    return Uri.fromFile(file)
  }

  private companion object {
    val BiologyLines = listOf(
      "Light energy -> chemical energy",
      "Happens in chloroplasts (chlorophyll)",
      "Light reactions: H2O split, O2 out",
      "Calvin cycle: CO2 fixed into glucose",
      "Needs ATP + NADPH from light stage",
      "6CO2 + 6H2O -> C6H12O6 + 6O2",
      "Rate limited by light, CO2, temp",
      "Stomata control gas exchange",
    )
    val PhysicsLines = listOf(
      "1st law: inertia",
      "2nd law: F = m a",
      "3rd law: action = reaction",
      "Momentum p = m v",
      "Impulse = change in momentum",
      "Units: newton = kg m / s^2",
    )
    val HistoryLines = listOf(
      "Began in Britain, c. 1760",
      "Steam engine (James Watt)",
      "Textile factories, urbanization",
      "Railways connect markets",
      "Social change: working class",
    )
  }
}

