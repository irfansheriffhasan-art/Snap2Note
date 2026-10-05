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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
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

  private fun savePageImage(source: Uri, target: File): Boolean {
    val bitmap = try {
      ImageProcessing.decode(MAX_PAGE_SIDE) { context.contentResolver.openInputStream(source) }
    } catch (e: Exception) {
      null
    } catch (e: OutOfMemoryError) {
      null
    } ?: return false

    return try {
      FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
    } catch (e: Exception) {
      target.delete()
      false
    } finally {
      bitmap.recycle()
    }
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

    // A4 in PostScript points (1/72 inch).
    const val A4_SHORT_SIDE = 595
    const val A4_LONG_SIDE = 842
    const val PDF_MARGIN = 18
    const val PDF_IMAGE_SIDE = 1800
  }
}
