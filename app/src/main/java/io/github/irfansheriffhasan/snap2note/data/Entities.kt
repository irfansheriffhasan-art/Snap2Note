package io.github.irfansheriffhasan.snap2note.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class Folder(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "pages",
  foreignKeys = [
    ForeignKey(
      entity = Folder::class,
      parentColumns = ["id"],
      childColumns = ["folderId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index("folderId")],
)
data class Page(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val folderId: Long,
  /** Name of the image file inside the app's private pages directory. */
  val fileName: String,
  val createdAt: Long = System.currentTimeMillis(),
  val title: String? = null,
  val extractedText: String? = null,
  val summary: String? = null,
  val flashcardsJson: String? = null,
)

val Page.isAnalyzed: Boolean
  get() = summary != null || extractedText != null

/** A folder together with the numbers shown on folder cards. */
data class FolderWithStats(
  val id: Long,
  val name: String,
  val createdAt: Long,
  val pageCount: Int,
  val analyzedCount: Int,
  /** File name of the folder's first page, used as its cover. */
  val coverFileName: String?,
  val updatedAt: Long,
)
