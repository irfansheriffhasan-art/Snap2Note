# Keep line numbers so release crash reports are readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Gemini request/response models are serialized by Moshi's generated adapters,
# which look up properties by name.
-keep class io.github.irfansheriffhasan.snap2note.ai.** { *; }
