# R8 rules for the release build (see "Release build is minified" in android/README.md).
# Most libraries (Compose, coroutines, ML Kit / Play services, Shizuku API) ship their own
# consumer rules, so only what this app needs on top of those is listed here.

# Readable crash traces: keep file names and line numbers, hide the real source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Shizuku: ShizukuShell calls Shizuku.newProcess by name through reflection (it is hidden/private
# in API 13). Renaming or removing it would make every Phone Care command fail.
-keepclassmembers class rikka.shizuku.Shizuku {
    *** newProcess(java.lang.String[], java.lang.String[], java.lang.String);
}
# ShizukuProvider is named in AndroidManifest.xml (AAPT keeps it); its binder classes must stay too.
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.api.** { *; }

# LiteRT / TensorFlow Lite (Photo Enhancer): the native library calls back into these Java classes
# by name via JNI, so they must not be renamed or stripped.
-keep class org.tensorflow.lite.** { *; }
-keep class com.google.ai.edge.litert.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
-dontwarn org.tensorflow.lite.gpu.**

# Enums saved by name (valueOf(name) on read): prefs in ChangeStore, JSON annotations in PdfDoc.
# Renaming their constants would silently drop users' saved decisions and PDF marks.
-keepclassmembers enum com.cleanai.debloat.data.Decision { *; }
-keepclassmembers enum com.cleanai.debloat.data.MarkKind { *; }
-keepclassmembers enum com.cleanai.debloat.data.ShapeKind { *; }

# PDFBox-Android (PDF tools and editor): no consumer rules are shipped.
# The security handler factory instantiates handlers/policies reflectively (password PDFs, Protect).
-keep class com.tom_roush.pdfbox.pdmodel.encryption.** { *; }
# Optional JPEG2000 decoder that PDFBox references but the app does not bundle.
-dontwarn com.gemalto.jp2.**
-dontwarn com.tom_roush.pdfbox.filter.JPXFilter
