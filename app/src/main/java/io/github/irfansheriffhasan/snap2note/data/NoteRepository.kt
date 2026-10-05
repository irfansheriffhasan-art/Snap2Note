package io.github.irfansheriffhasan.snap2note.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import io.github.irfansheriffhasan.snap2note.ai.PageAnalysis
import io.github.irfansheriffhasan.snap2note.data.ImageProcessing.flattenedOnWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.min

data class AddPagesResult(val pageIds: List<Long>, val failedCount: Int)

/** Stores folders and pages in Room and the page images in app-private storage. */
class NoteRepository(private val context: Context, private val database: AppDatabase) {
  private val folderDao = database.folderDao()
  private val pageDao = database.pageDao()
  private val fileProviderAuthority = "${context.packageName}.fileprovider"

  private val pagesDir get() = File(context.filesDir, PAGES_DIR).apply { mkdirs() }
  private val capturesDir get() = File(context.cacheDir, CAPTURES_DIR).apply { mkdirs() }
  private val exportsDir get() = File(context.cacheDir, EXPORTS_DIR).apply { mkdirs() }

  val folders: Flow<List<FolderWithStats>> = folderDao.observeAllWithStats()

  fun observePages(folderId: Long): Flow<List<Page>> = pageDao.observeByFolder(folderId)

  suspend fun getPages(folderId: Long): List<Page> = pageDao.getByFolder(folderId)

  fun pageFile(fileName: String): File = File(pagesDir, fileName)

  fun pageFile(page: Page): File = pageFile(page.fileName)

  fun contentUri(file: File): Uri = FileProvider.getUriForFile(context, fileProviderAuthority, file)

  // region Folders

  suspend fun createFolder(name: String): Long = folderDao.insert(Folder(name = name.trim()))

  suspend fun renameFolder(id: Long, name: String) = folderDao.rename(id, name.trim())

  suspend fun deleteFolder(folderId: Long) {
    val pages = pageDao.getByFolder(folderId)
    database.withTransaction {
      pageDao.deleteByFolder(folderId)
      folderDao.deleteById(folderId)
    }
    deleteFiles(pages)
  }

  // endregion

  // region Pages

  /** Copies the given images into [folderId], normalizing their size and orientation. */
  suspend fun addPages(folderId: Long, sources: List<Uri>): AddPagesResult = withContext(Dispatchers.IO) {
    val now = System.currentTimeMillis()
    val pages = sources.mapIndexedNotNull { index, uri ->
      val fileName = "page_${UUID.randomUUID()}.jpg"
      if (savePageImage(uri, File(pagesDir, fileName))) {
        Page(folderId = folderId, fileName = fileName, createdAt = now + index)
      } else {
        null
      }
    }
    val ids = if (pages.isEmpty()) emptyList() else pageDao.insertAll(pages)
    sources.forEach(::discardCapture)
    AddPagesResult(pageIds = ids, failedCount = sources.size - pages.size)
  }

  suspend fun deletePages(pages: List<Page>) {
    pageDao.deleteByIds(pages.map { it.id })
    deleteFiles(pages)
  }

  suspend fun movePages(pageIds: List<Long>, folderId: Long) = pageDao.moveToFolder(pageIds, folderId)

  suspend fun renamePage(pageId: Long, title: String?) = pageDao.updateTitle(pageId, title?.trim()?.ifEmpty { null })

  suspend fun saveAnalysis(pageId: Long, analysis: PageAnalysis, flashcardsJson: String) =
    pageDao.updateAnalysis(
      id = pageId,
      title = analysis.title,
      extractedText = analysis.text,
      summary = analysis.summary,
      flashcardsJson = flashcardsJson,
    )

  /** Decodes a page image for the editor preview, at most [maxSide] pixels on its longest side. */
  suspend fun loadPageBitmap(page: Page, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
    val file = pageFile(page)
    try {
      ImageProcessing.decode(maxSide) { if (file.exists()) file.inputStream() else null }
    } catch (e: Exception) {
      null
    } catch (e: OutOfMemoryError) {
      null
    }
  }

  /**
   * Applies [edits] to the page image. The result is written to a new file so image caches
   * pick up the change, and the old file is removed once the page points at the new one.
   */
  suspend fun editPage(page: Page, edits: PageEdits): Boolean = withContext(Dispatchers.IO) {
    val source = pageFile(page)
    val original = try {
      ImageProcessing.decode(MAX_PAGE_SIDE) { if (source.exists()) source.inputStream() else null }
    } catch (e: OutOfMemoryError) {
      null
    } ?: return@withContext false

    val edited = try {
      ImageProcessing.applyEdits(original, edits)
    } catch (e: Exception) {
      null
    } catch (e: OutOfMemoryError) {
      null
    } finally {
      original.recycle()
    } ?: return@withContext false

    val fileName = "page_${UUID.randomUUID()}.jpg"
    val target = File(pagesDir, fileName)
    if (!writeJpeg(edited, target)) return@withContext false
    pageDao.updateFileName(page.id, fileName)
    if (source.name != fileName) source.delete()
    true
  }

  private suspend fun savePageImage(source: Uri, target: File): Boolean {
    val bytes = readCompleteImage(source) ?: return false
    val bitmap = try {
      ImageProcessing.decode(MAX_PAGE_SIDE) { ByteArrayInputStream(bytes) }?.flattenedOnWhite()
    } catch (e: Exception) {
      null
    } catch (e: OutOfMemoryError) {
      null
    } ?: return false
    return writeJpeg(bitmap, target)
  }

  /**
   * Reads the whole image, waiting briefly while it is still being written. Camera apps on
   * some phones return before the photo is fully saved, and decoding it at that point gives a
   * page whose lower part is black.
   */
  private suspend fun readCompleteImage(source: Uri): ByteArray? {
    var bytes: ByteArray? = null
    repeat(INCOMPLETE_IMAGE_RETRIES) { attempt ->
      bytes = try {
        context.contentResolver.openInputStream(source)?.use { it.readBytes() }
      } catch (e: Exception) {
        null
      } catch (e: OutOfMemoryError) {
        return null
      }
      if (bytes?.let(ImageProcessing::isCompleteImage) == true) return bytes
      if (attempt < INCOMPLETE_IMAGE_RETRIES - 1) delay(INCOMPLETE_IMAGE_RETRY_DELAY_MS)
    }
    // Still incomplete: keep what is there rather than losing the page. It can be cropped later.
    return bytes?.takeIf { it.isNotEmpty() }
  }

  private fun writeJpeg(bitmap: Bitmap, target: File): Boolean =
    try {
      FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
    } catch (e: Exception) {
      target.delete()
      false
    } finally {
      bitmap.recycle()
    }

  private suspend fun deleteFiles(pages: List<Page>) = withContext(Dispatchers.IO) {
    pages.forEach { pageFile(it).delete() }
  }

  // endregion

  // region Camera captures

  /** Returns a fresh file-provider URI for the camera app to write a photo into. */
  fun newCaptureUri(): Uri {
    val cutoff = System.currentTimeMillis() - CAPTURE_MAX_AGE_MS
    capturesDir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    val file = File(capturesDir, "capture_${System.currentTimeMillis()}.jpg")
    return contentUri(file)
  }

  /** Deletes a temporary camera capture once it has been saved or discarded. */
  fun discardCapture(uri: Uri) {
    if (uri.authority != fileProviderAuthority) return
    val segments = uri.pathSegments
    if (segments.size == 2 && segments[0] == CAPTURES_DIR) {
      File(capturesDir, segments[1]).delete()
    }
  }

  // endregion

  // region Export

  /** Renders the pages into an A4 PDF, one image per page, and returns the file. */
  suspend fun exportPdf(folderName: String, pages: List<Page>): File = withContext(Dispatchers.IO) {
    val document = PdfDocument()
    try {
      val paint = Paint(Paint.FILTER_BITMAP_FLAG)
      var pageNumber = 0
      for (page in pages) {
        val file = pageFile(page)
        if (!file.exists()) continue
        val bitmap = ImageProcessing.decode(PDF_IMAGE_SIDE) { file.inputStream() } ?: continue
        val landscape = bitmap.width > bitmap.height
        val width = if (landscape) A4_LONG_SIDE else A4_SHORT_SIDE
        val height = if (landscape) A4_SHORT_SIDE else A4_LONG_SIDE
        val pdfPage = document.startPage(PdfDocument.PageInfo.Builder(width, height, ++pageNumber).create())

        val scale = min(
          (width - 2 * PDF_MARGIN).toFloat() / bitmap.width,
          (height - 2 * PDF_MARGIN).toFloat() / bitmap.height,
        )
        val drawnWidth = bitmap.width * scale
        val drawnHeight = bitmap.height * scale
        val left = (width - drawnWidth) / 2
        val top = (height - drawnHeight) / 2
        pdfPage.canvas.drawBitmap(bitmap, null, RectF(left, top, left + drawnWidth, top + drawnHeight), paint)
        document.finishPage(pdfPage)
        bitmap.recycle()
      }
      check(pageNumber > 0) { "No pages could be exported" }

      exportsDir.listFiles()?.forEach { it.delete() }
      val output = File(exportsDir, "${safeFileName(folderName)}.pdf")
      FileOutputStream(output).use { document.writeTo(it) }
      output
    } finally {
      document.close()
    }
  }

  // endregion

  // region Storage

  suspend fun storageUsedBytes(): Long = withContext(Dispatchers.IO) {
    pagesDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
  }

  suspend fun clearCache() = withContext(Dispatchers.IO) {
    capturesDir.listFiles()?.forEach { it.delete() }
    exportsDir.listFiles()?.forEach { it.delete() }
  }

  // endregion

  private companion object {
    const val PAGES_DIR = "pages"
    const val CAPTURES_DIR = "captures"
    const val EXPORTS_DIR = "exports"

    const val MAX_PAGE_SIDE = 2560
    const val JPEG_QUALITY = 90
    const val CAPTURE_MAX_AGE_MS = 24 * 60 * 60 * 1000L
    const val INCOMPLETE_IMAGE_RETRIES = 16
    const val INCOMPLETE_IMAGE_RETRY_DELAY_MS = 250L

    // A4 in PostScript points (1/72 inch).
    const val A4_SHORT_SIDE = 595
    const val A4_LONG_SIDE = 842
    const val PDF_MARGIN = 18
    const val PDF_IMAGE_SIDE = 1800
  }
}
