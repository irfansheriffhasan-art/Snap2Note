package io.github.irfansheriffhasan.snap2note.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Folder::class, Page::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
  abstract fun folderDao(): FolderDao

  abstract fun pageDao(): PageDao

  companion object {
    fun create(context: Context): AppDatabase =
      Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "snap2note.db").build()
  }
}
