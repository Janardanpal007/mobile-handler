package com.cleanai.debloat.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A PDF saved for the user in My Files: where it lives and what to call it in the chat. */
data class SavedPdf(val uri: Uri, val name: String, val pages: Int, val where: String, val dir: String)

/** Builds PDFs from photos and saves scans and PDFs into the app's My Files folder (on the phone only). */
object PdfTools {
    // A4 at 72 dpi, with a small margin so photos don't touch the edge.
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 24f

    suspend fun fromImages(ctx: Context, images: List<Uri>): SavedPdf = withContext(Dispatchers.IO) {
        val doc = MyFiles.newDoc(ctx, "Photos")
        val pdf = PdfDocument()
        try {
            images.forEach { uri ->
                val bmp = loadScaled(ctx, uri) ?: return@forEach
                val n = pdf.pages.size + 1
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, n).create())
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)
                val box = RectF(MARGIN, MARGIN, PAGE_W - MARGIN, PAGE_H - MARGIN)
                val scale = minOf(box.width() / bmp.width, box.height() / bmp.height)
                val w = bmp.width * scale
                val h = bmp.height * scale
                val dst = RectF(box.centerX() - w / 2, box.centerY() - h / 2, box.centerX() + w / 2, box.centerY() + h / 2)
                canvas.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
                pdf.finishPage(page)
                File(doc.dir, "page_$n.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                bmp.recycle()
            }
            if (pdf.pages.isEmpty()) {
                MyFiles.delete(doc)
                error("None of the selected images could be opened.")
            }
            doc.pdf.outputStream().use { pdf.writeTo(it) }
            saved(ctx, doc, pdf.pages.size)
        } finally {
            pdf.close()
        }
    }

    /** Copies what the document scanner made (PDF + page images) into My Files. */
    suspend fun keep(ctx: Context, pdf: Uri, pages: Int, pageImages: List<Uri>): SavedPdf = withContext(Dispatchers.IO) {
        val doc = MyFiles.newDoc(ctx, "Scan")
        ctx.contentResolver.openInputStream(pdf)!!.use { input -> doc.pdf.outputStream().use { input.copyTo(it) } }
        pageImages.forEachIndexed { i, img ->
            runCatching {
                ctx.contentResolver.openInputStream(img)!!.use { input ->
                    File(doc.dir, "page_${i + 1}.jpg").outputStream().use { input.copyTo(it) }
                }
            }
        }
        saved(ctx, doc, pages)
    }

    private fun saved(ctx: Context, doc: MyFiles.Doc, pages: Int) = SavedPdf(
        uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", doc.pdf),
        name = doc.title,
        pages = pages,
        where = "My Files",
        dir = doc.dir.absolutePath,
    )

    /** Decodes at most ~2000 px on the long side and applies the photo's rotation. */
    internal fun loadScaled(ctx: Context, uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2
        val bmp = ctx.contentResolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = ctx.contentResolver.openInputStream(uri)!!.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }
        if (rotation == 0f) bmp
        else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
            .also { if (it != bmp) bmp.recycle() }
    }.getOrNull()
}
