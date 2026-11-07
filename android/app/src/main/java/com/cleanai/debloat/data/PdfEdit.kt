package com.cleanai.debloat.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** PDF tools that run fully on the phone: merge, keep/remove pages, compress, watermark and passwords (editing is in PdfDoc). */
object PdfEdit {
    private var ready = false

    private fun init(ctx: Context) {
        if (!ready) {
            PDFBoxResourceLoader.init(ctx.applicationContext)
            ready = true
        }
    }

    private fun load(ctx: Context, uri: Uri): PDDocument {
        init(ctx)
        val input = ctx.contentResolver.openInputStream(uri) ?: error("Couldn't open the PDF")
        return input.use { PDDocument.load(it, MemoryUsageSetting.setupTempFileOnly()) }
    }

    private fun save(ctx: Context, doc: PDDocument, out: Uri) {
        (ctx.contentResolver.openOutputStream(out, "wt") ?: error("Couldn't write the file")).use { doc.save(it) }
    }

    fun displayName(ctx: Context, uri: Uri): String =
        // My Files keeps each PDF as <folder>/document.pdf, so the folder ("Scan_20261102_2240") is the useful name.
        if (uri.scheme == "file") java.io.File(uri.path.orEmpty()).let { f ->
            if (f.name == "document.pdf") (f.parentFile?.let { MyFiles.Doc(it).name } ?: f.parentFile?.name) + ".pdf" else f.name
        }
        else runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"

    fun sizeOf(ctx: Context, uri: Uri): Long =
        runCatching { ctx.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } }.getOrNull() ?: -1L

    suspend fun merge(ctx: Context, inputs: List<Uri>, out: Uri) = withContext(Dispatchers.IO) {
        init(ctx)
        val streams = inputs.map { ctx.contentResolver.openInputStream(it) ?: error("Couldn't open ${displayName(ctx, it)}") }
        try {
            (ctx.contentResolver.openOutputStream(out, "wt") ?: error("Couldn't write the file")).use { dest ->
                PDFMergerUtility().apply {
                    streams.forEach { addSource(it) }
                    destinationStream = dest
                }.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
            }
        } finally {
            streams.forEach { runCatching { it.close() } }
        }
    }

    /** Saves only [pages] (0-based, in that order) to [out]: used for both "extract pages" and "remove pages". */
    suspend fun keepPages(ctx: Context, src: Uri, pages: List<Int>, out: Uri) = withContext(Dispatchers.IO) {
        load(ctx, src).use { doc ->
            PDDocument().use { result ->
                pages.forEach { result.importPage(doc.getPage(it)) }
                save(ctx, result, out)
            }
        }
    }

    /**
     * Shrinks a PDF by re-saving its pictures as JPEG at [quality] (0..1), no larger than [maxSide] pixels.
     * Text and vector drawings stay as they are. Returns the sizes before and after.
     */
    suspend fun compress(ctx: Context, src: Uri, out: Uri, quality: Float, maxSide: Int): Pair<Long, Long> = withContext(Dispatchers.IO) {
        load(ctx, src).use { doc ->
            for (page in doc.pages) {
                val res = page.resources ?: continue
                for (name in res.xObjectNames.toList()) {
                    val img = runCatching { res.getXObject(name) as? PDImageXObject }.getOrNull() ?: continue
                    // Pictures with transparency would lose it as JPEG, so they're left alone.
                    if (img.softMask != null || img.isStencil) continue
                    val before = img.stream.length
                    val bmp = runCatching { img.image }.getOrNull() ?: continue
                    val scale = maxSide.toFloat() / max(bmp.width, bmp.height)
                    val small = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).roundToInt().coerceAtLeast(1), (bmp.height * scale).roundToInt().coerceAtLeast(1), true) else bmp
                    val jpeg = runCatching { JPEGFactory.createFromImage(doc, small, quality) }.getOrNull() ?: continue
                    if (jpeg.stream.length < before) res.put(name, jpeg)
                }
            }
            save(ctx, doc, out)
        }
        sizeOf(ctx, src) to sizeOf(ctx, out)
    }

    /** Writes [text] faintly and diagonally across every page, in [color] at [opacity] (0..1). */
    suspend fun watermark(ctx: Context, src: Uri, out: Uri, text: String, color: Int, opacity: Float) = withContext(Dispatchers.IO) {
        load(ctx, src).use { doc ->
            val font = fontFor(doc, text)
            val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = opacity }
            val rgb = floatArrayOf(android.graphics.Color.red(color) / 255f, android.graphics.Color.green(color) / 255f, android.graphics.Color.blue(color) / 255f)
            val textWidth = font.getStringWidth(text) / 1000f
            for (page in doc.pages) {
                val box = page.cropBox
                val (w, h) = box.width to box.height
                // Corner to corner as the page is shown, even when the page is stored turned.
                val turned = page.rotation % 180 != 0
                val angle = Math.atan2((if (turned) w else h).toDouble(), (if (turned) h else w).toDouble())
                val diagonal = Math.hypot(w.toDouble(), h.toDouble()).toFloat()
                val size = minOf(diagonal * 0.7f / textWidth.coerceAtLeast(0.01f), minOf(w, h) * 0.3f)
                val capHeight = size * 0.7f
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.setGraphicsStateParameters(gs)
                    cs.setNonStrokingColor(PDColor(rgb, PDDeviceRGB.INSTANCE))
                    cs.beginText()
                    cs.setFont(font, size)
                    val m = com.tom_roush.pdfbox.util.Matrix.getRotateInstance(angle, box.lowerLeftX + w / 2, box.lowerLeftY + h / 2)
                    m.translate(-textWidth * size / 2, -capHeight / 2)
                    cs.setTextMatrix(m)
                    cs.showText(text)
                    cs.endText()
                }
            }
            save(ctx, doc, out)
        }
    }

    /** Helvetica when the text fits it (nothing embedded), otherwise the phone's own font so any language works. */
    private fun fontFor(doc: PDDocument, text: String): com.tom_roush.pdfbox.pdmodel.font.PDFont {
        if (runCatching { PDType1Font.HELVETICA_BOLD.encode(text) }.isSuccess) return PDType1Font.HELVETICA_BOLD
        return listOf("RobotoStatic-Bold.ttf", "Roboto-Bold.ttf", "NotoSans-Bold.ttf", "DroidSans-Bold.ttf")
            .map { java.io.File("/system/fonts", it) }.firstOrNull { it.exists() }
            ?.let { f -> runCatching { PDType0Font.load(doc, f) }.getOrNull() }
            ?: error("This phone has no font for that text")
    }

    /**
     * Saves a copy that asks for [password] before it opens (AES-256). Anyone with the password can open,
     * print and copy from it as normal.
     */
    suspend fun lock(ctx: Context, src: Uri, out: Uri, password: String) = withContext(Dispatchers.IO) {
        load(ctx, src).use { doc ->
            if (doc.isEncrypted) error("This PDF already has a password")
            val policy = StandardProtectionPolicy(password, password, AccessPermission()).apply { encryptionKeyLength = 256 }
            doc.protect(policy)
            save(ctx, doc, out)
        }
    }

    /** True when the PDF asks for a password before it opens. */
    fun needsPassword(ctx: Context, uri: Uri): Boolean = runCatching {
        init(ctx)
        ctx.contentResolver.openInputStream(uri)?.use { PDDocument.load(it, MemoryUsageSetting.setupTempFileOnly()).close() }
        false
    }.getOrElse { it is InvalidPasswordException }

    /** True when [password] opens this PDF. */
    fun passwordOpens(ctx: Context, uri: Uri, password: String): Boolean = runCatching {
        init(ctx)
        ctx.contentResolver.openInputStream(uri)?.use { PDDocument.load(it, password, MemoryUsageSetting.setupTempFileOnly()).close() } != null
    }.getOrDefault(false)

    /** Saves a copy of a password-protected PDF that opens without one. Fails with [InvalidPasswordException] for a wrong password. */
    suspend fun unlock(ctx: Context, src: Uri, out: Uri, password: String) = withContext(Dispatchers.IO) {
        init(ctx)
        val input = ctx.contentResolver.openInputStream(src) ?: error("Couldn't open the PDF")
        input.use { PDDocument.load(it, password, MemoryUsageSetting.setupTempFileOnly()) }.use { doc ->
            doc.isAllSecurityToBeRemoved = true
            save(ctx, doc, out)
        }
    }

    /** "1-3, 5, 8-" -> [0, 1, 2, 4, 7, 8, …] (0-based), or null if it doesn't make sense for [count] pages. */
    fun parsePages(text: String, count: Int): List<Int>? {
        val out = mutableListOf<Int>()
        for (part in text.split(',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }) {
            val m = Regex("^(\\d*)\\s*-\\s*(\\d*)$").matchEntire(part)
            if (m != null) {
                val a = m.groupValues[1].toIntOrNull() ?: 1
                val b = m.groupValues[2].toIntOrNull() ?: count
                if (a < 1 || b > count || a > b) return null
                (a..b).forEach { out += it - 1 }
            } else {
                val n = part.toIntOrNull() ?: return null
                if (n < 1 || n > count) return null
                out += n - 1
            }
        }
        return out.distinct().takeIf { it.isNotEmpty() }
    }

    fun pageCount(ctx: Context, uri: Uri): Int =
        runCatching { ctx.contentResolver.openFileDescriptor(uri, "r")!!.use { fd -> PdfRenderer(fd).use { it.pageCount } } }.getOrDefault(0)
}

/** Draws pages of an open PDF for the reader, one at a time (PdfRenderer allows only one open page). */
class PdfPages(ctx: Context, uri: Uri) : AutoCloseable {
    private val fd: ParcelFileDescriptor = ctx.contentResolver.openFileDescriptor(uri, "r") ?: error("Couldn't open the PDF")
    private val renderer = try {
        PdfRenderer(fd)
    } catch (e: Exception) {
        fd.close()
        throw e
    }
    private val lock = Mutex()
    val count = renderer.pageCount

    /** Width / height of each page, read once when the PDF opens. */
    val ratios: List<Float> = (0 until count).map { i -> renderer.openPage(i).use { it.width.toFloat() / it.height } }

    suspend fun render(index: Int, widthPx: Int): Bitmap = withContext(Dispatchers.IO) {
        lock.withLock {
            renderer.openPage(index).use { page ->
                val w = widthPx.coerceIn(200, 2400)
                val h = (w * page.height.toFloat() / page.width).roundToInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { fd.close() }
    }
}
