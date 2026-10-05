package io.github.irfansheriffhasan.snap2note.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
  @Query(
    """
    SELECT f.id, f.name, f.createdAt,
           (SELECT COUNT(*) FROM pages p WHERE p.folderId = f.id) AS pageCount,
           (SELECT COUNT(*) FROM pages p
             WHERE p.folderId = f.id AND (p.summary IS NOT NULL OR p.extractedText IS NOT NULL)) AS analyzedCount,
           (SELECT p.fileName FROM pages p
             WHERE p.folderId = f.id ORDER BY p.createdAt ASC, p.id ASC LIMIT 1) AS coverFileName,
           COALESCE((SELECT MAX(p.createdAt) FROM pages p WHERE p.folderId = f.id), f.createdAt) AS updatedAt
    FROM folders f
    ORDER BY updatedAt DESC
    """,
  )
  fun observeAllWithStats(): Flow<List<FolderWithStats>>

  @Insert
  suspend fun insert(folder: Folder): Long

  @Query("UPDATE folders SET name = :name WHERE id = :id")
  suspend fun rename(id: Long, name: String)

  @Query("DELETE FROM folders WHERE id = :id")
  suspend fun deleteById(id: Long)
}

@Dao
interface PageDao {
  @Query("SELECT * FROM pages WHERE folderId = :folderId ORDER BY createdAt ASC, id ASC")
  fun observeByFolder(folderId: Long): Flow<List<Page>>

  @Query("SELECT * FROM pages WHERE folderId = :folderId ORDER BY createdAt ASC, id ASC")
  suspend fun getByFolder(folderId: Long): List<Page>

  @Insert
  suspend fun insertAll(pages: List<Page>): List<Long>

  @Query("UPDATE pages SET title = :title WHERE id = :id")
  suspend fun updateTitle(id: Long, title: String?)

  @Query("UPDATE pages SET fileName = :fileName WHERE id = :id")
  suspend fun updateFileName(id: Long, fileName: String)

  @Query(
    """
    UPDATE pages
    SET title = :title, extractedText = :extractedText, summary = :summary, flashcardsJson = :flashcardsJson
    WHERE id = :id
    """,
  )
  suspend fun updateAnalysis(
    id: Long,
    title: String?,
    extractedText: String?,
    summary: String?,
    flashcardsJson: String?,
  )

  @Query("UPDATE pages SET folderId = :folderId WHERE id IN (:ids)")
  suspend fun moveToFolder(ids: List<Long>, folderId: Long)

  @Query("DELETE FROM pages WHERE id IN (:ids)")
  suspend fun deleteByIds(ids: List<Long>)

  @Query("DELETE FROM pages WHERE folderId = :folderId")
  suspend fun deleteByFolder(folderId: Long)
}
