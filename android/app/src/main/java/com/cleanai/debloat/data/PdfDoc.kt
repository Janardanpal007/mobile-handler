package com.cleanai.debloat.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix as AMatrix
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.blend.BlendMode
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.AnnotationFilter
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------- geometry
//
// Everything the editor places is stored in 0..1 of the page *as shown on screen* (x right, y down),
// so it doesn't matter how big the page is drawn. Sizes (font size, line width) are a fraction of the
// page's "unit" = (shown width + shown height) / 2, which stays the same when a page is turned.

data class NPoint(val x: Float, val y: Float)

data class NRect(val l: Float, val t: Float, val r: Float, val b: Float) {
    val w get() = r - l
    val h get() = b - t
    val cx get() = (l + r) / 2
    val cy get() = (t + b) / 2
    fun contains(x: Float, y: Float, pad: Float = 0f) = x >= l - pad && x <= r + pad && y >= t - pad && y <= b + pad
    fun offset(dx: Float, dy: Float) = NRect(l + dx, t + dy, r + dx, b + dy)
    fun union(o: NRect) = NRect(min(l, o.l), min(t, o.t), max(r, o.r), max(b, o.b))

    /** Maps a point inside this rect to the same relative place inside [to]. */
    fun mapTo(to: NRect, p: NPoint) = NPoint(
        to.l + (if (w == 0f) 0f else (p.x - l) / w) * to.w,
        to.t + (if (h == 0f) 0f else (p.y - t) / h) * to.h,
    )

    companion object {
        fun of(a: NPoint, b: NPoint) = NRect(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))
        fun around(points: List<NPoint>) = NRect(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
    }
}

/** Turns a point a quarter turn clockwise with its page. */
fun NPoint.turned() = NPoint(1f - y, x)
fun NRect.turned() = NRect.of(NPoint(l, t).turned(), NPoint(r, b).turned())

enum class MarkKind { HIGHLIGHT, UNDERLINE, STRIKE }
enum class ShapeKind { RECT, OVAL, LINE, ARROW }
enum class FontFamilyKind { SANS, SERIF, MONO }

/** Something the user added to a page. Items can be selected, moved, resized, recoloured and deleted until saved. */
sealed interface EditItem {
    val id: Long
    val bounds: NRect
    fun moved(dx: Float, dy: Float): EditItem
    fun resized(to: NRect): EditItem
    fun turned(): EditItem
    fun recolored(color: Int): EditItem = this
}

data class InkItem(override val id: Long, val points: List<NPoint>, val color: Int, val width: Float, val alpha: Float) : EditItem {
    override val bounds get() = NRect.around(points)
    override fun moved(dx: Float, dy: Float) = copy(points = points.map { NPoint(it.x + dx, it.y + dy) })
    override fun resized(to: NRect) = copy(points = points.map { bounds.mapTo(to, it) })
    override fun turned() = copy(points = points.map { it.turned() })
    override fun recolored(color: Int) = copy(color = color)
}

/** Highlight, underline or strike-through laid over lines of text. */
data class MarkItem(override val id: Long, val rects: List<NRect>, val color: Int, val kind: MarkKind) : EditItem {
    override val bounds get() = rects.reduce { a, b -> a.union(b) }
    override fun moved(dx: Float, dy: Float) = copy(rects = rects.map { it.offset(dx, dy) })
    override fun resized(to: NRect) = copy(rects = rects.map { NRect.of(bounds.mapTo(to, NPoint(it.l, it.t)), bounds.mapTo(to, NPoint(it.r, it.b))) })
    override fun turned() = copy(rects = rects.map { it.turned() })
    override fun recolored(color: Int) = copy(color = color)
}

data class ShapeItem(override val id: Long, val start: NPoint, val end: NPoint, val kind: ShapeKind, val color: Int, val width: Float) : EditItem {
    override val bounds get() = NRect.of(start, end)
    override fun moved(dx: Float, dy: Float) = copy(start = NPoint(start.x + dx, start.y + dy), end = NPoint(end.x + dx, end.y + dy))
    override fun resized(to: NRect) = copy(start = bounds.mapTo(to, start), end = bounds.mapTo(to, end))
    override fun turned() = copy(start = start.turned(), end = end.turned())
    override fun recolored(color: Int) = copy(color = color)
}

/** A text box. [size] is the font size; [turns] quarter turns the text is rotated on the page. */
data class TextBoxItem(
    override val id: Long, val box: NRect, val text: String, val color: Int, val size: Float,
    val bold: Boolean = false, val turns: Int = 0,
) : EditItem {
    override val bounds get() = box
    override fun moved(dx: Float, dy: Float) = copy(box = box.offset(dx, dy))
    override fun resized(to: NRect): TextBoxItem {
        // The text grows or shrinks with the box.
        val along = if (turns % 2 == 0) to.h / box.h else to.w / box.w
        return copy(box = to, size = (size * along).coerceIn(0.004f, 0.3f))
    }
    override fun turned() = copy(box = box.turned(), turns = (turns + 1) % 4)
    override fun recolored(color: Int) = copy(color = color)
}

/** A picture or a signature, kept as a PNG file in the app's cache until saved. */
data class PictureItem(override val id: Long, val box: NRect, val file: String, val signature: Boolean, val turns: Int = 0) : EditItem {
    override val bounds get() = box
    override fun moved(dx: Float, dy: Float) = copy(box = box.offset(dx, dy))
    override fun resized(to: NRect) = copy(box = to)
    override fun turned() = copy(box = box.turned(), turns = (turns + 1) % 4)
}

/**
 * New wording for text that is already on the page. [box] is covered with [background] and the new
 * [text] is written on the old baseline ([baseline] = 0..1 down the box) in a matching font.
 */
data class TextEditItem(
    override val id: Long, val box: NRect, val original: String, val text: String, val color: Int, val background: Int,
    val size: Float, val baseline: Float, val bold: Boolean, val family: FontFamilyKind, val turns: Int = 0,
) : EditItem {
    override val bounds get() = box
    override fun moved(dx: Float, dy: Float) = this // stays where the old text was
    override fun resized(to: NRect) = this
    override fun turned() = copy(box = box.turned(), turns = (turns + 1) % 4)
    override fun recolored(color: Int) = copy(color = color)
}

/** One run of text on a page, with the left/right edge of every character (all 0..1 of the shown page). */
data class TextLine(
    val box: NRect, val baseline: Float, val text: String, val lefts: List<Float>, val rights: List<Float>,
    val sizePt: Float, val bold: Boolean, val family: FontFamilyKind,
)

/** A page of the document being edited: an original page (by index), or a new blank one; [turns] quarter turns added. */
data class PageRef(val id: Long, val source: Int, val turns: Int, val widthPt: Float, val heightPt: Float) {
    /** Size as shown, after the extra turns. */
    val shownW get() = if (turns % 2 == 0) widthPt else heightPt
    val shownH get() = if (turns % 2 == 0) heightPt else widthPt
    val unitPt get() = (widthPt + heightPt) / 2
}

/** Everything the user has done to the document: page order and the items on each page (by [PageRef.id]). */
data class EditState(val pages: List<PageRef>, val items: Map<Long, List<EditItem>>) {
    fun itemsOn(page: PageRef) = items[page.id].orEmpty()
    fun withItems(page: PageRef, list: List<EditItem>) = copy(items = items + (page.id to list))
}

/** Rects covering characters [from]..[to] of the page's lines (in reading order), for highlighting text. */
fun selectionRects(lines: List<TextLine>, from: Pair<Int, Int>, to: Pair<Int, Int>): List<NRect> {
    val (a, b) = if (from.first < to.first || (from.first == to.first && from.second <= to.second)) from to to else to to from
    return (a.first..b.first).mapNotNull { li ->
        val line = lines[li]
        val first = if (li == a.first) a.second else 0
        val last = if (li == b.first) b.second else line.lefts.lastIndex
        if (first > last || line.lefts.isEmpty()) null
        else NRect(line.lefts[first], line.box.t, line.rights[last], line.box.b)
    }
}

/** Nearest line and character to a point, or null when the page has no text near it. */
fun charAt(lines: List<TextLine>, p: NPoint, reach: Float = 0.03f): Pair<Int, Int>? {
    val li = lines.indices.minByOrNull { i ->
        val bx = lines[i].box
        val dy = if (p.y < bx.t) bx.t - p.y else if (p.y > bx.b) p.y - bx.b else 0f
        val dx = if (p.x < bx.l) bx.l - p.x else if (p.x > bx.r) p.x - bx.r else 0f
        dy * 4 + dx
    } ?: return null
    val line = lines[li]
    if (!line.box.contains(p.x, p.y, reach)) return null
    val ci = line.lefts.indices.minByOrNull { abs((line.lefts[it] + line.rights[it]) / 2 - p.x) } ?: return null
    return li to ci
}

/** Distance from [p] to the polyline, in page fractions. */
fun distanceToPath(points: List<NPoint>, p: NPoint): Float {
    if (points.size == 1) return hypot(points[0].x - p.x, points[0].y - p.y)
    var best = Float.MAX_VALUE
    for (i in 1 until points.size) {
        val a = points[i - 1]; val b = points[i]
        val dx = b.x - a.x; val dy = b.y - a.y
        val len = dx * dx + dy * dy
        val t = if (len == 0f) 0f else (((p.x - a.x) * dx + (p.y - a.y) * dy) / len).coerceIn(0f, 1f)
        best = min(best, hypot(a.x + t * dx - p.x, a.y + t * dy - p.y))
    }
    return best
}

// ---------------------------------------------------------------------------------------------- items <-> JSON

/** Our annotations carry the editable item in this key, so they can be edited again after reopening. */
private val MH_KEY = COSName.getPDFName("MHItem")

internal object ItemJson {
    private fun JSONArray.floats() = (0 until length()).map { getDouble(it).toFloat() }
    private fun rectArray(r: NRect) = JSONArray(listOf(r.l, r.t, r.r, r.b).map { it.toDouble() })
    private fun rectOf(a: JSONArray) = a.floats().let { NRect(it[0], it[1], it[2], it[3]) }

    fun write(item: EditItem): String? = when (item) {
        is InkItem -> JSONObject().put("t", "ink").put("c", item.color).put("w", item.width.toDouble()).put("a", item.alpha.toDouble())
            .put("p", JSONArray(item.points.flatMap { listOf(it.x.toDouble(), it.y.toDouble()) }))
        is MarkItem -> JSONObject().put("t", "mark").put("c", item.color).put("k", item.kind.name)
            .put("r", JSONArray(item.rects.flatMap { listOf(it.l, it.t, it.r, it.b).map(Float::toDouble) }))
        is ShapeItem -> JSONObject().put("t", "shape").put("c", item.color).put("k", item.kind.name).put("w", item.width.toDouble())
            .put("s", JSONArray(listOf(item.start.x, item.start.y, item.end.x, item.end.y).map(Float::toDouble)))
        is TextBoxItem -> JSONObject().put("t", "text").put("c", item.color).put("z", item.size.toDouble()).put("x", item.text)
            .put("b", rectArray(item.box)).put("n", item.turns).put("bold", item.bold)
        is PictureItem -> JSONObject().put("t", "pic").put("b", rectArray(item.box)).put("sig", item.signature).put("n", item.turns)
        is TextEditItem -> null // written into the page itself
    }?.toString()

    /** [picture] supplies the image file for a picture item (taken from the annotation's appearance). */
    fun read(json: String, id: Long, picture: () -> String?): EditItem? = runCatching {
        val o = JSONObject(json)
        when (o.getString("t")) {
            "ink" -> InkItem(id, o.getJSONArray("p").floats().chunked(2) { NPoint(it[0], it[1]) }, o.getInt("c"), o.getDouble("w").toFloat(), o.getDouble("a").toFloat())
            "mark" -> MarkItem(id, o.getJSONArray("r").floats().chunked(4) { NRect(it[0], it[1], it[2], it[3]) }, o.getInt("c"), MarkKind.valueOf(o.getString("k")))
            "shape" -> o.getJSONArray("s").floats().let { s ->
                ShapeItem(id, NPoint(s[0], s[1]), NPoint(s[2], s[3]), ShapeKind.valueOf(o.getString("k")), o.getInt("c"), o.getDouble("w").toFloat())
            }
            "text" -> TextBoxItem(id, rectOf(o.getJSONArray("b")), o.getString("x"), o.getInt("c"), o.getDouble("z").toFloat(), o.optBoolean("bold"), o.optInt("n"))
            "pic" -> picture()?.let { PictureItem(id, rectOf(o.getJSONArray("b")), it, o.optBoolean("sig"), o.optInt("n")) }
            else -> null
        }
    }.getOrNull()
}

// ---------------------------------------------------------------------------------------------- open document

/**
 * An open PDF in the editor. Pages are drawn with Android's fast PdfRenderer, except pages that carry
 * annotations from other apps (highlights, form fields…), which PdfBox draws so those stay visible.
 */
class PdfSession private constructor(
    private val ctx: Context,
    val uri: Uri,
    private val doc: PDDocument,
    private val fast: PdfPages,
) : AutoCloseable {
    private val lock = Mutex()
    private val renderer = PDFRenderer(doc).apply {
        // Our own annotations are drawn live by the editor, so leave them out of the page picture.
        setAnnotationsFilter(AnnotationFilter { it.cosObject.getDictionaryObject(MH_KEY) == null })
    }
    private val lineCache = HashMap<Int, List<TextLine>>()

    val pageCount = doc.numberOfPages

    /** Shown size of each original page in points (after its own /Rotate). */
    val sizes: List<Pair<Float, Float>> = doc.pages.map { p ->
        val box = p.cropBox
        if (rotationOf(p) % 180 == 90) box.height to box.width else box.width to box.height
    }

    private val foreign: List<Boolean> = doc.pages.map { p ->
        runCatching {
            p.annotations.any { a ->
                a.cosObject.getDictionaryObject(MH_KEY) == null && a.subtype != "Link" && a.subtype != "Popup" && a.normalAppearanceStream != null
            }
        }.getOrDefault(false)
    }

    fun initialState(): EditState {
        val pages = sizes.mapIndexed { i, (w, h) -> PageRef(i + 1L, i, 0, w, h) }
        return EditState(pages, pages.associate { it.id to readItems(it.source) })
    }

    /** Draws an original page [widthPx] wide (blank pages are drawn by the caller). */
    suspend fun render(source: Int, widthPx: Int): Bitmap = withContext(Dispatchers.IO) {
        if (foreign[source]) lock.withLock {
            val scale = widthPx / sizes[source].first
            renderer.renderImage(source, scale)
        } else fast.render(source, widthPx)
    }

    /** Lines of text on an original page (empty for scanned pages). */
    suspend fun lines(source: Int): List<TextLine> = withContext(Dispatchers.IO) {
        lock.withLock { lineCache.getOrPut(source) { runCatching { readLines(source) }.getOrDefault(emptyList()) } }
    }

    private fun readLines(source: Int): List<TextLine> {
        val (sw, sh) = sizes[source]
        val runs = mutableListOf<List<TextPosition>>()
        val stripper = object : PDFTextStripper() {
            override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
                val list = textPositions?.filter { it.unicode != null } ?: return
                // Split a line where there's a wide gap (columns, tables) or the baseline jumps.
                var run = mutableListOf<TextPosition>()
                for (tp in list) {
                    val prev = run.lastOrNull()
                    if (prev != null) {
                        val gap = tp.x - (prev.x + prev.width)
                        val size = max(prev.fontSizeInPt, prev.heightDir)
                        if (gap > max(prev.widthOfSpace * 3f, size * 1.6f) || abs(tp.y - prev.y) > size * 0.6f) {
                            runs += run; run = mutableListOf()
                        }
                    }
                    run += tp
                }
                if (run.isNotEmpty()) runs += run
            }
        }
        stripper.sortByPosition = true
        stripper.startPage = source + 1
        stripper.endPage = source + 1
        stripper.getText(doc)
        return runs.mapNotNull { run ->
            val text = run.joinToString("") { it.unicode }
            if (text.isBlank()) return@mapNotNull null
            val size = run.maxOf { tp -> tp.fontSizeInPt.takeIf { it > 1f } ?: (tp.heightDir * 1.4f) }.coerceAtLeast(2f)
            val baseline = run.map { it.y }.average().toFloat()
            val left = run.minOf { it.x }
            val right = run.maxOf { it.x + it.width }
            val fontName = run.first().font?.name.orEmpty()
            TextLine(
                box = NRect(left / sw, (baseline - size * 0.82f) / sh, right / sw, (baseline + size * 0.24f) / sh),
                baseline = baseline / sh,
                text = text,
                lefts = run.flatMap { tp -> List(tp.unicode.length) { tp.x / sw } },
                rights = run.flatMap { tp -> List(tp.unicode.length) { (tp.x + tp.width) / sw } },
                sizePt = size,
                bold = Regex("bold|black|heavy|semibold", RegexOption.IGNORE_CASE).containsMatchIn(fontName),
                family = familyOf(fontName),
            )
        }
    }

    /** Turns our saved annotations on an original page back into editable items. */
    private fun readItems(source: Int): List<EditItem> = runCatching {
        doc.getPage(source).annotations.mapNotNull { a ->
            val json = (a.cosObject.getDictionaryObject(MH_KEY) as? com.tom_roush.pdfbox.cos.COSString)?.string ?: return@mapNotNull null
            ItemJson.read(json, newId()) { pictureOf(a) }
        }
    }.getOrDefault(emptyList())

    private fun pictureOf(a: PDAnnotation): String? = runCatching {
        val res = a.normalAppearanceStream.resources
        val name = res.xObjectNames.first()
        val bmp = (res.getXObject(name) as PDImageXObject).image
        val f = File(PdfDoc.workDir(ctx), "pic_${newId()}.png")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        f.path
    }.getOrNull()

    override fun close() {
        runCatching { fast.close() }
        runCatching { doc.close() }
    }

    companion object {
        suspend fun open(ctx: Context, uri: Uri): PdfSession = withContext(Dispatchers.IO) {
            PdfDoc.init(ctx)
            val fast = PdfPages(ctx, uri)
            try {
                val doc = (ctx.contentResolver.openInputStream(uri) ?: error("Couldn't open the PDF")).use {
                    PDDocument.load(it, MemoryUsageSetting.setupTempFileOnly())
                }
                PdfSession(ctx, uri, doc, fast)
            } catch (e: Exception) {
                fast.close()
                throw e
            }
        }
    }
}

private var idCounter = System.currentTimeMillis()
@Synchronized fun newId(): Long = ++idCounter

private fun rotationOf(p: PDPage) = ((p.rotation % 360) + 360) % 360

private fun familyOf(fontName: String) = when {
    Regex("courier|mono|consol", RegexOption.IGNORE_CASE).containsMatchIn(fontName) -> FontFamilyKind.MONO
    Regex("times|serif|georgia|garamond|cambria|book", RegexOption.IGNORE_CASE).containsMatchIn(fontName) &&
        !fontName.contains("sans", ignoreCase = true) -> FontFamilyKind.SERIF
    else -> FontFamilyKind.SANS
}

// ---------------------------------------------------------------------------------------------- saving

object PdfDoc {
    private var ready = false

    fun init(ctx: Context) {
        if (!ready) {
            PDFBoxResourceLoader.init(ctx.applicationContext)
            ready = true
        }
    }

    /** Pictures, signatures and the save-in-progress file live here. */
    fun workDir(ctx: Context) = File(ctx.cacheDir, "pdf_edit").apply { mkdirs() }

    private const val MAX_SIGNATURES = 6

    private fun signatureDir(ctx: Context) = File(ctx.filesDir, "signatures").apply { mkdirs() }

    /** Signatures the user chose to keep, newest first, stored privately on this phone. */
    fun savedSignatures(ctx: Context): List<File> {
        val dir = signatureDir(ctx)
        // Earlier versions kept a single signature here; it joins the list.
        File(ctx.filesDir, "signature.png").takeIf { it.exists() }?.let { old -> old.renameTo(File(dir, "sig_${old.lastModified()}.png")) }
        return dir.listFiles { f -> f.name.endsWith(".png") }.orEmpty().sortedByDescending { it.lastModified() }
    }

    /** Keeps a copy of [file] for next time; only the newest [MAX_SIGNATURES] are kept. */
    fun keepSignature(ctx: Context, file: File) {
        file.copyTo(File(signatureDir(ctx), "sig_${System.currentTimeMillis()}.png"), overwrite = true)
        savedSignatures(ctx).drop(MAX_SIGNATURES).forEach { it.delete() }
    }

    /**
     * Writes the edited document to [out]. The source is read fully first, so [out] may be the same file.
     * Text edits become part of the page; everything else is saved as annotations that other PDF apps show.
     */
    suspend fun save(ctx: Context, src: Uri, state: EditState, out: Uri) = withContext(Dispatchers.IO) {
        init(ctx)
        val temp = File(workDir(ctx), "saving.pdf")
        (ctx.contentResolver.openInputStream(src) ?: error("Couldn't open the PDF")).use { input ->
            PDDocument.load(input, MemoryUsageSetting.setupTempFileOnly())
        }.use { doc ->
            arrangePages(doc, state.pages)
            val fonts = Fonts(doc)
            doc.pages.forEachIndexed { i, page -> writePage(doc, page, state.itemsOn(state.pages[i]), state.pages[i], fonts) }
            doc.save(temp)
        }
        // Copy over the destination only once the new file is complete.
        if (out.scheme == "file") temp.copyTo(File(out.path!!), overwrite = true)
        else (ctx.contentResolver.openOutputStream(out, "wt") ?: error("Couldn't write the file")).use { o -> temp.inputStream().use { it.copyTo(o) } }
        temp.delete()
    }

    /** Puts pages in the order of [pages], dropping removed ones, adding blank ones and applying turns. */
    private fun arrangePages(doc: PDDocument, pages: List<PageRef>) {
        val original = doc.pages.toList()
        val unchanged = pages.size == original.size && pages.withIndex().all { (i, p) -> p.source == i }
        if (!unchanged) {
            // Copy inherited settings onto each page before taking it out of the page tree.
            original.forEach { p ->
                p.resources = p.resources
                p.mediaBox = p.mediaBox
                p.cropBox = p.cropBox
                p.rotation = rotationOf(p)
            }
            original.forEach { doc.pages.remove(it) }
            pages.forEach { ref ->
                val page = if (ref.source >= 0) original[ref.source] else PDPage(PDRectangle(ref.widthPt, ref.heightPt))
                doc.addPage(page)
            }
        }
        doc.pages.forEachIndexed { i, page ->
            if (pages[i].turns != 0) page.rotation = (rotationOf(page) + pages[i].turns * 90) % 360
        }
    }

    private fun writePage(doc: PDDocument, page: PDPage, items: List<EditItem>, ref: PageRef, fonts: Fonts) {
        // Replace our old annotations with the current items; other apps' annotations stay.
        val kept = page.annotations.filter { it.cosObject.getDictionaryObject(MH_KEY) == null }
        val geo = PageGeometry(page)
        val edits = items.filterIsInstance<TextEditItem>()
        if (edits.isNotEmpty()) {
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                edits.forEach { drawTextEdit(cs, it, geo, fonts) }
            }
        }
        val added = items.filter { it !is TextEditItem }.mapNotNull { item -> runCatching { annotationFor(doc, item, geo, fonts) }.getOrNull() }
        page.annotations = kept + added
    }

    private fun annotationFor(doc: PDDocument, item: EditItem, g: PageGeometry, fonts: Fonts): PDAnnotation {
        val pad = when (item) {
            is InkItem -> item.width * g.unit
            is ShapeItem -> item.width * g.unit * 4
            else -> 2f
        }
        val rect = g.rectOf(item.bounds, pad)
        val dict = COSDictionary()
        dict.setItem(COSName.TYPE, COSName.ANNOT)
        val subtype = when (item) {
            is InkItem -> "Ink"
            is MarkItem -> when (item.kind) { MarkKind.HIGHLIGHT -> "Highlight"; MarkKind.UNDERLINE -> "Underline"; MarkKind.STRIKE -> "StrikeOut" }
            is ShapeItem -> when (item.kind) { ShapeKind.RECT -> "Square"; ShapeKind.OVAL -> "Circle"; else -> "Line" }
            is TextBoxItem -> "FreeText"
            is PictureItem -> "Stamp"
            is TextEditItem -> error("not an annotation")
        }
        dict.setName(COSName.SUBTYPE, subtype)
        dict.setString(MH_KEY, ItemJson.write(item))
        when (item) {
            is InkItem -> dict.setItem(COSName.getPDFName("InkList"), COSArray().apply { add(floats(item.points.flatMap { g.map(it).toList() })) })
            is MarkItem -> dict.setItem(COSName.getPDFName("QuadPoints"), floats(item.rects.flatMap { r ->
                listOf(NPoint(r.l, r.t), NPoint(r.r, r.t), NPoint(r.l, r.b), NPoint(r.r, r.b)).flatMap { g.map(it).toList() }
            }))
            is ShapeItem -> if (item.kind == ShapeKind.LINE || item.kind == ShapeKind.ARROW) {
                dict.setItem(COSName.L, floats(g.map(item.start).toList() + g.map(item.end).toList()))
                if (item.kind == ShapeKind.ARROW) dict.setItem(COSName.getPDFName("LE"), COSArray().apply {
                    add(COSName.getPDFName("None")); add(COSName.getPDFName("OpenArrow"))
                })
            }
            is TextBoxItem -> dict.setString(COSName.DA, "/Helv ${(item.size * g.unit).roundToInt()} Tf 0 g")
            else -> Unit
        }
        val annotation = PDAnnotation.createAnnotation(dict)
        annotation.rectangle = rect
        annotation.isPrinted = true
        annotation.annotationName = "mh-${item.id}"
        (item as? TextBoxItem)?.let { annotation.contents = it.text }
        colorOf(item)?.let { annotation.color = pdColor(it) }

        val ap = PDAppearanceStream(doc)
        ap.bBox = rect
        ap.resources = PDResources()
        PDPageContentStream(doc, ap).use { cs -> drawItem(doc, cs, item, g, fonts) }
        annotation.appearance = PDAppearanceDictionary().apply { setNormalAppearance(ap) }
        return annotation
    }

    private fun colorOf(item: EditItem): Int? = when (item) {
        is InkItem -> item.color
        is MarkItem -> item.color
        is ShapeItem -> item.color
        is TextBoxItem -> item.color
        else -> null
    }

    private fun drawItem(doc: PDDocument, cs: PDPageContentStream, item: EditItem, g: PageGeometry, fonts: Fonts) {
        when (item) {
            is InkItem -> {
                alpha(cs, stroke = item.alpha)
                cs.setStrokingColor(pdColor(item.color))
                cs.setLineWidth(item.width * g.unit)
                cs.setLineCapStyle(1); cs.setLineJoinStyle(1)
                val first = g.map(item.points.first())
                cs.moveTo(first.first, first.second)
                if (item.points.size == 1) cs.lineTo(first.first + 0.1f, first.second)
                item.points.drop(1).forEach { p -> val (x, y) = g.map(p); cs.lineTo(x, y) }
                cs.stroke()
            }
            is MarkItem -> item.rects.forEach { r ->
                when (item.kind) {
                    MarkKind.HIGHLIGHT -> {
                        val gs = PDExtendedGraphicsState().apply { blendMode = BlendMode.MULTIPLY; nonStrokingAlphaConstant = 0.55f }
                        cs.setGraphicsStateParameters(gs)
                        cs.setNonStrokingColor(pdColor(item.color))
                        polygon(cs, g, listOf(NPoint(r.l, r.t), NPoint(r.r, r.t), NPoint(r.r, r.b), NPoint(r.l, r.b)))
                        cs.fill()
                    }
                    else -> {
                        val y = if (item.kind == MarkKind.UNDERLINE) r.b - r.h * 0.12f else r.cy + r.h * 0.08f
                        cs.setStrokingColor(pdColor(item.color))
                        cs.setLineWidth(max(0.8f, r.h * g.shownH * 0.07f))
                        val (x0, y0) = g.map(NPoint(r.l, y)); val (x1, y1) = g.map(NPoint(r.r, y))
                        cs.moveTo(x0, y0); cs.lineTo(x1, y1); cs.stroke()
                    }
                }
            }
            is ShapeItem -> {
                cs.setStrokingColor(pdColor(item.color))
                cs.setLineWidth(item.width * g.unit)
                cs.setLineCapStyle(1); cs.setLineJoinStyle(1)
                when (item.kind) {
                    ShapeKind.RECT -> { val b = item.bounds; polygon(cs, g, listOf(NPoint(b.l, b.t), NPoint(b.r, b.t), NPoint(b.r, b.b), NPoint(b.l, b.b))); cs.stroke() }
                    ShapeKind.OVAL -> {
                        val b = item.bounds
                        val pts = (0 until 72).map { i ->
                            val a = i / 72.0 * 2 * Math.PI
                            NPoint(b.cx + (b.w / 2) * kotlin.math.cos(a).toFloat(), b.cy + (b.h / 2) * kotlin.math.sin(a).toFloat())
                        }
                        polygon(cs, g, pts); cs.stroke()
                    }
                    ShapeKind.LINE, ShapeKind.ARROW -> {
                        val (x0, y0) = g.map(item.start); val (x1, y1) = g.map(item.end)
                        cs.moveTo(x0, y0); cs.lineTo(x1, y1)
                        if (item.kind == ShapeKind.ARROW) {
                            val ang = kotlin.math.atan2((y1 - y0).toDouble(), (x1 - x0).toDouble())
                            val len = max(8f, item.width * g.unit * 5)
                            for (side in listOf(-1, 1)) {
                                val a = ang + Math.PI + side * Math.PI / 7
                                cs.moveTo(x1, y1)
                                cs.lineTo(x1 + (len * kotlin.math.cos(a)).toFloat(), y1 + (len * kotlin.math.sin(a)).toFloat())
                            }
                        }
                        cs.stroke()
                    }
                }
            }
            is TextBoxItem -> {
                val font = fonts.sans(item.bold)
                val size = item.size * g.unit
                cs.setNonStrokingColor(pdColor(item.color))
                cs.saveGraphicsState()
                cs.transform(g.frame(item.box, item.turns))
                item.text.split('\n').forEachIndexed { i, line ->
                    cs.beginText()
                    cs.setFont(font, size)
                    cs.newLineAtOffset(size * 0.15f, -(size * (0.93f + 1.2f * i)))
                    cs.showText(Fonts.safe(font, line))
                    cs.endText()
                }
                cs.restoreGraphicsState()
            }
            is PictureItem -> {
                val bmp = BitmapFactory.decodeFile(item.file) ?: return
                val image = if (item.signature || bmp.hasAlpha()) LosslessFactory.createFromImage(doc, bmp) else JPEGFactory.createFromImage(doc, bmp, 0.88f)
                val (cw, ch) = g.frameSize(item.box, item.turns)
                cs.saveGraphicsState()
                cs.transform(g.frame(item.box, item.turns))
                cs.drawImage(image, 0f, -ch, cw, ch)
                cs.restoreGraphicsState()
            }
            is TextEditItem -> Unit
        }
    }

    private fun drawTextEdit(cs: PDPageContentStream, item: TextEditItem, g: PageGeometry, fonts: Fonts) {
        // Cover the old words with the page colour, then write the new ones on the same baseline.
        cs.saveGraphicsState()
        cs.setNonStrokingColor(pdColor(item.background))
        val b = item.box
        polygon(cs, g, listOf(NPoint(b.l, b.t), NPoint(b.r, b.t), NPoint(b.r, b.b), NPoint(b.l, b.b)))
        cs.fill()
        cs.restoreGraphicsState()
        if (item.text.isBlank()) return
        val font = fonts.forFamily(item.family, item.bold, item.text)
        val size = item.size * g.unit
        val (_, ch) = g.frameSize(item.box, item.turns)
        cs.saveGraphicsState()
        cs.transform(g.frame(item.box, item.turns))
        cs.setNonStrokingColor(pdColor(item.color))
        cs.beginText()
        cs.setFont(font, size)
        cs.newLineAtOffset(0f, -ch * item.baseline)
        cs.showText(Fonts.safe(font, item.text))
        cs.endText()
        cs.restoreGraphicsState()
    }

    private fun polygon(cs: PDPageContentStream, g: PageGeometry, pts: List<NPoint>) {
        val (x0, y0) = g.map(pts[0])
        cs.moveTo(x0, y0)
        pts.drop(1).forEach { val (x, y) = g.map(it); cs.lineTo(x, y) }
        cs.closePath()
    }

    private fun alpha(cs: PDPageContentStream, stroke: Float) {
        if (stroke < 1f) cs.setGraphicsStateParameters(PDExtendedGraphicsState().apply { strokingAlphaConstant = stroke })
    }

    private fun floats(values: List<Float>) = COSArray().apply { values.forEach { add(COSFloat(it)) } }

    private fun pdColor(argb: Int) = PDColor(
        floatArrayOf(((argb shr 16) and 0xFF) / 255f, ((argb shr 8) and 0xFF) / 255f, (argb and 0xFF) / 255f),
        PDDeviceRGB.INSTANCE,
    )
}

/** Converts between "0..1 of the page as shown" and PDF points, honouring the page's /Rotate. */
private class PageGeometry(page: PDPage) {
    private val box = page.cropBox
    private val rot = rotationOf(page)
    val shownW = if (rot % 180 == 90) box.height else box.width
    val shownH = if (rot % 180 == 90) box.width else box.height
    val unit = (box.width + box.height) / 2

    fun map(p: NPoint): Pair<Float, Float> = when (rot) {
        90 -> box.lowerLeftX + p.y * box.width to box.lowerLeftY + p.x * box.height
        180 -> box.lowerLeftX + (1 - p.x) * box.width to box.lowerLeftY + p.y * box.height
        270 -> box.lowerLeftX + (1 - p.y) * box.width to box.lowerLeftY + (1 - p.x) * box.height
        else -> box.lowerLeftX + p.x * box.width to box.lowerLeftY + (1 - p.y) * box.height
    }

    fun rectOf(r: NRect, pad: Float): PDRectangle {
        val a = map(NPoint(r.l, r.t)); val b = map(NPoint(r.r, r.b))
        val x0 = min(a.first, b.first) - pad; val y0 = min(a.second, b.second) - pad
        return PDRectangle(x0, y0, abs(a.first - b.first) + 2 * pad, abs(a.second - b.second) + 2 * pad)
    }

    /** Width and height, in points, of the content of a box whose content is turned [turns] quarter turns. */
    fun frameSize(r: NRect, turns: Int): Pair<Float, Float> {
        val w = r.w * shownW; val h = r.h * shownH
        return if (turns % 2 == 0) w to h else h to w
    }

    /**
     * A transform whose origin is the top-left of the (turned) content of [r], x to the right and y up,
     * in points: draw text at negative y to go down the box.
     */
    fun frame(r: NRect, turns: Int): Matrix {
        // Screen points (x right, y down) -> page points is affine: P0 + sx*U + sy*V.
        val p0 = map(NPoint(0f, 0f))
        val px = map(NPoint(1f, 0f)); val py = map(NPoint(0f, 1f))
        val ux = (px.first - p0.first) / shownW; val uy = (px.second - p0.second) / shownW
        val vx = (py.first - p0.first) / shownH; val vy = (py.second - p0.second) / shownH
        val l = r.l * shownW; val t = r.t * shownH; val rr = r.r * shownW; val b = r.b * shownH
        // Content axes on screen for each quarter turn: origin, x direction, y-down direction.
        val (o, ax, ay) = when (((turns % 4) + 4) % 4) {
            1 -> Triple(rr to t, 0f to 1f, -1f to 0f)
            2 -> Triple(rr to b, -1f to 0f, 0f to -1f)
            3 -> Triple(l to b, 0f to -1f, 1f to 0f)
            else -> Triple(l to t, 1f to 0f, 0f to 1f)
        }
        fun lin(x: Float, y: Float) = (x * ux + y * vx) to (x * uy + y * vy)
        val a = lin(ax.first, ax.second)
        val down = lin(ay.first, ay.second)
        val origin = lin(o.first, o.second)
        return Matrix(a.first, a.second, -down.first, -down.second, p0.first + origin.first, p0.second + origin.second)
    }
}

/** Fonts for new text: the PDF standard Times/Courier where they fit, otherwise the phone's Roboto, embedded. */
private class Fonts(private val doc: PDDocument) {
    private val loaded = HashMap<String, PDFont>()

    fun sans(bold: Boolean): PDFont = loaded.getOrPut(if (bold) "sans-b" else "sans") {
        val names = if (bold) listOf("RobotoStatic-Bold.ttf", "Roboto-Bold.ttf", "NotoSans-Bold.ttf", "DroidSans-Bold.ttf")
        else listOf("RobotoStatic-Regular.ttf", "Roboto-Regular.ttf", "NotoSans-Regular.ttf", "DroidSans.ttf")
        names.map { File("/system/fonts", it) }.firstOrNull { it.exists() }
            ?.let { f -> runCatching { PDType0Font.load(doc, f) }.getOrNull() }
            ?: if (bold) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA
    }

    fun forFamily(family: FontFamilyKind, bold: Boolean, text: String): PDFont {
        val standard = when (family) {
            FontFamilyKind.SERIF -> if (bold) PDType1Font.TIMES_BOLD else PDType1Font.TIMES_ROMAN
            FontFamilyKind.MONO -> if (bold) PDType1Font.COURIER_BOLD else PDType1Font.COURIER
            FontFamilyKind.SANS -> null
        }
        // The standard fonts only cover Western letters; anything else needs the embedded font.
        return if (standard != null && runCatching { standard.encode(text) }.isSuccess) standard else sans(bold)
    }

    companion object {
        /** Replaces letters the font can't draw with "?", so saving never fails on them. */
        fun safe(font: PDFont, text: String): String {
            if (runCatching { font.encode(text) }.isSuccess) return text
            return text.map { c -> if (runCatching { font.encode(c.toString()) }.isSuccess) c else '?' }.joinToString("")
        }
    }
}

/** Turns a page picture a number of quarter turns clockwise. */
fun Bitmap.turnedBy(turns: Int): Bitmap =
    if (turns % 4 == 0) this else Bitmap.createBitmap(this, 0, 0, width, height, AMatrix().apply { postRotate(90f * (turns % 4)) }, true)
