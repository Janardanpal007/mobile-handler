package com.cleanai.debloat.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The app's own "My Files" folder, in private app storage on the phone.
 * Each scan or photo PDF is a folder holding document.pdf plus one JPEG per page,
 * so it can be shared either as a PDF or as images.
 */
object MyFiles {
    data class Doc(val dir: File) {
        val pdf get() = File(dir, "document.pdf")
        val images get() = dir.listFiles { f -> f.name.startsWith("page_") && f.extension == "jpg" }
            .orEmpty().sortedBy { it.nameWithoutExtension.removePrefix("page_").toIntOrNull() ?: 0 }
        val kind get() = when {
            dir.name.startsWith("Scan") -> "Scan"
            dir.name.startsWith("Photo") -> "Enhanced photo"
            else -> "Photos PDF"
        }
        val hasPdf get() = pdf.exists()
        val created get() = dir.lastModified()
        val title get() = "$kind · " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(created))
        val sizeText: String get() {
            val bytes = dir.listFiles().orEmpty().sumOf { it.length() }
            return if (bytes > 1_000_000) String.format(Locale.US, "%.1f MB", bytes / 1e6) else "${bytes / 1000} KB"
        }
    }

    private fun root(ctx: Context) = File(ctx.filesDir, "MyFiles").apply { mkdirs() }

    fun list(ctx: Context): List<Doc> =
        root(ctx).listFiles { f -> f.isDirectory && (File(f, "document.pdf").exists() || File(f, "page_1.jpg").exists()) }
            .orEmpty().map { Doc(it) }.sortedByDescending { it.created }

    /** A new, empty folder for a document, e.g. MyFiles/Scan_20261030_143312. */
    fun newDoc(ctx: Context, prefix: String): Doc {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
        var dir = File(root(ctx), "${prefix}_$stamp")
        var n = 2
        while (dir.exists()) dir = File(root(ctx), "${prefix}_${stamp}_${n++}")
        dir.mkdirs()
        return Doc(dir)
    }

    fun byPath(path: String): Doc? = File(path).takeIf { it.isDirectory }?.let { Doc(it) }

    fun delete(doc: Doc) = doc.dir.deleteRecursively()

    private fun uri(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)

    /** Keeps an enhanced photo in My Files (as page_1.jpg) and returns its document. */
    fun savePhoto(ctx: Context, bitmap: android.graphics.Bitmap): Doc {
        val doc = newDoc(ctx, "Photo")
        File(doc.dir, "page_1.jpg").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it) }
        return doc
    }

    /** Copies a photo into the phone's gallery (Pictures/Mobile Handler). */
    fun saveToGallery(ctx: Context, file: File): Boolean = runCatching {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "MobileHandler_${System.currentTimeMillis()}.jpg")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Mobile Handler")
            }
        }
        val target = ctx.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        ctx.contentResolver.openOutputStream(target)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
        true
    }.getOrDefault(false)

    fun open(ctx: Context, doc: Doc) {
        if (!doc.hasPdf) {
            doc.images.firstOrNull()?.let { img ->
                val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri(ctx, img), "image/jpeg")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { ctx.startActivity(view) }
            }
            return
        }
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri(ctx, doc.pdf), "application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(view)
        } catch (e: Exception) {
            Toast.makeText(ctx, "No PDF viewer installed — use Share instead.", Toast.LENGTH_LONG).show()
        }
    }

    fun sharePdf(ctx: Context, doc: Doc) {
        val u = uri(ctx, doc.pdf)
        val send = Intent(Intent.ACTION_SEND).setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, u)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri("", u)
        ctx.startActivity(Intent.createChooser(send, "Share PDF").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun shareImages(ctx: Context, doc: Doc) {
        val uris = ArrayList(doc.images.map { uri(ctx, it) })
        if (uris.isEmpty()) {
            Toast.makeText(ctx, "This document has no page images.", Toast.LENGTH_SHORT).show()
            return
        }
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        send.setType("image/jpeg").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // Grant read access to every page, not just the first.
        send.clipData = ClipData.newRawUri("", uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        ctx.startActivity(Intent.createChooser(send, "Share as images").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
