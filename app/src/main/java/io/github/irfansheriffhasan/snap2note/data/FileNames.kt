package io.github.irfansheriffhasan.snap2note.data

private val UnsafeFileNameChars = Regex("[^\\p{L}\\p{N} ._-]")
private val RepeatedSpaces = Regex("\\s+")

/** Turns a user-entered folder name into something safe to use as a shared file name. */
fun safeFileName(name: String, fallback: String = "Snap2Note"): String =
  name
    .replace(UnsafeFileNameChars, "_")
    .replace(RepeatedSpaces, " ")
    .trim(' ', '.', '_')
    .take(60)
    .trim()
    .ifEmpty { fallback }
