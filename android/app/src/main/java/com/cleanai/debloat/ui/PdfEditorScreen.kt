package com.cleanai.debloat.ui

import com.cleanai.debloat.data.FileNames
import com.cleanai.debloat.data.MyFiles
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanai.debloat.data.EditItem
import com.cleanai.debloat.data.EditState
import com.cleanai.debloat.data.FontFamilyKind
import com.cleanai.debloat.data.InkItem
import com.cleanai.debloat.data.MarkItem
import com.cleanai.debloat.data.MarkKind
import com.cleanai.debloat.data.NPoint
import com.cleanai.debloat.data.NRect
import com.cleanai.debloat.data.PageRef
import com.cleanai.debloat.data.PdfDoc
import com.cleanai.debloat.data.PdfEdit
import com.cleanai.debloat.data.PdfSession
import com.cleanai.debloat.data.PdfTools
import com.cleanai.debloat.data.PictureItem
import com.cleanai.debloat.data.ShapeItem
import com.cleanai.debloat.data.ShapeKind
import com.cleanai.debloat.data.TextBoxItem
import com.cleanai.debloat.data.TextEditItem
import com.cleanai.debloat.data.TextLine
import com.cleanai.debloat.data.charAt
import com.cleanai.debloat.data.distanceToPath
import com.cleanai.debloat.data.newId
import com.cleanai.debloat.data.selectionRects
import com.cleanai.debloat.data.turnedBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private enum class Tool(val label: String, val icon: String) {
    SELECT("Select", "☝"),
    EDIT_TEXT("Edit text", "Aa"),
    TEXT("Add text", "T"),
    PEN("Pen", "✎"),
    MARK("Highlight", "▍"),
    SHAPE("Shapes", "◻"),
    ERASER("Eraser", "⌫"),
}

private val Palette = listOf(0xFF1E1E1E, 0xFFD64535, 0xFF2F6FDB, 0xFF2E9E5B, 0xFFF08C00, 0xFF8E44AD).map { it.toInt() }
private val MarkPalette = listOf(0xFFFFE14D, 0xFF8CF08C, 0xFF7FD8FF, 0xFFFF9EC8).map { it.toInt() }
private val PenWidths = listOf("Thin" to 0.0022f, "Medium" to 0.0045f, "Thick" to 0.009f)
private val TextSizes = listOf("S" to 0.016f, "M" to 0.022f, "L" to 0.03f, "XL" to 0.042f)

/** What the text dialog is working on: a new text box at [at], or an existing one. */
private data class TextJob(val page: PageRef, val at: NPoint?, val item: TextBoxItem?)

/** Editing words already on the page: the line found under the finger, or an earlier edit. */
private data class LineJob(val page: PageRef, val item: TextEditItem)

/** What the editor does once a PDF is open: just show it, or go straight to signing or stamping. */
enum class EditorStart { READ, SIGN, STAMP }

/**
 * The PDF editor: pages scroll like a reader, and the tools work right on the page.
 * Edit existing text, add text, draw, highlight / underline / strike text, shapes, signatures and pictures;
 * select anything you added to move, resize, recolour or delete it; rotate, reorder, delete and add pages.
 * Save writes back to the same file (or a copy).
 */
@Composable
fun PdfEditorScreen(start: Uri, onClose: () -> Unit, startWith: EditorStart = EditorStart.READ, onOpenOther: (Uri) -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()

    var fileUri by remember { mutableStateOf(start) }
    var version by remember { mutableIntStateOf(0) }
    var session by remember { mutableStateOf<PdfSession?>(null) }
    var openError by remember { mutableStateOf<String?>(null) }
    val history = remember { mutableStateListOf<EditState>() }
    var at by remember { mutableIntStateOf(0) }
    var savedAt by remember { mutableIntStateOf(0) }

    LaunchedEffect(fileUri, version) {
        openError = null
        val s = runCatching { PdfSession.open(ctx, fileUri) }
        s.onSuccess { opened ->
            session = opened
            history.clear(); history += opened.initialState(); at = 0; savedAt = 0
        }.onFailure { e ->
            openError = if (e is SecurityException || e.javaClass.simpleName.contains("Password", true))
                "This PDF is password-protected. Open it in an app that can unlock it first."
            else "Couldn't open this PDF: ${e.message}"
        }
    }
    DisposableEffect(Unit) { onDispose { session?.close() } }

    val state = history.getOrNull(at)
    val dirty = at != savedAt
    fun push(next: EditState) {
        while (history.lastIndex > at) history.removeAt(history.lastIndex)
        history += next
        at = history.lastIndex
    }

    var tool by remember { mutableStateOf(Tool.SELECT) }
    var selected by remember { mutableStateOf<Pair<Long, Long>?>(null) } // page id to item id
    var penColor by remember { mutableIntStateOf(Palette[2]) }
    var penWidth by remember { mutableStateOf(PenWidths[1].second) }
    var markColor by remember { mutableIntStateOf(MarkPalette[0]) }
    var markKind by remember { mutableStateOf(MarkKind.HIGHLIGHT) }
    var shapeKind by remember { mutableStateOf(ShapeKind.RECT) }
    var textColor by remember { mutableIntStateOf(Palette[0]) }
    var textSize by remember { mutableStateOf(TextSizes[1].second) }

    var textJob by remember { mutableStateOf<TextJob?>(null) }
    var lineJob by remember { mutableStateOf<LineJob?>(null) }
    var signing by remember { mutableStateOf(false) }
    var stamping by remember { mutableStateOf(false) }
    var showPages by remember { mutableStateOf(false) }
    var finding by remember { mutableStateOf(false) }
    // Opened from the PDF Editor's Sign or Stamp: show that tool once the pages are ready.
    var startDone by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf<NameAsk?>(null) }
    var renamedTimes by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf<String?>(null) }
    var askCopy by remember { mutableStateOf(false) }

    val bitmaps = remember { mutableStateMapOf<Long, Bitmap>() }
    val lines = remember { mutableStateMapOf<Long, List<TextLine>>() }
    val pagePx = remember { mutableStateMapOf<Long, IntSize>() }
    val pictures = remember { mutableStateMapOf<String, ImageBitmap>() }
    val list = rememberLazyListState()
    val name = remember(fileUri, renamedTimes) { PdfEdit.displayName(ctx, fileUri) }

    fun replaceItems(page: PageRef, f: (List<EditItem>) -> List<EditItem>) {
        val s = history[at]
        push(s.withItems(page, f(s.itemsOn(page))))
    }

    /**
     * The page under the middle of the screen and how far down that page (0..1) the middle falls.
     * The top-most visible page is often only a sliver, so it isn't the page being read.
     */
    fun pageInView(): Pair<Int, Float> {
        val info = list.layoutInfo
        val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
        val item = info.visibleItemsInfo.firstOrNull { mid >= it.offset && mid < it.offset + it.size }
            ?: info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - mid) }
            ?: return list.firstVisibleItemIndex to 0.5f
        return item.index to ((mid - item.offset).toFloat() / item.size.coerceAtLeast(1)).coerceIn(0f, 1f)
    }
    val pageShown by remember { derivedStateOf { pageInView().first } }

    fun currentPage(): PageRef? = state?.pages?.let { it.getOrNull(pageInView().first.coerceIn(0, (it.size - 1).coerceAtLeast(0))) }

    /** Fits a text box around its text, using the page's size on screen. */
    fun fitText(page: PageRef, item: TextBoxItem): TextBoxItem {
        val px = pagePx[page.id] ?: return item
        val unit = (px.width + px.height) / 2f
        val fs = item.size * unit
        val style = TextStyle(fontSize = with(density) { fs.toSp() }, fontWeight = if (item.bold) FontWeight.Bold else FontWeight.Normal)
        val lineList = item.text.split('\n')
        val w = lineList.maxOf { measurer.measure(it.ifEmpty { " " }, style).size.width.toFloat() } + fs * 0.3f
        val h = fs * (1.2f * lineList.size + 0.15f)
        val (cw, ch) = if (item.turns % 2 == 0) w / px.width to h / px.height else h / px.width to w / px.height
        return item.copy(box = NRect(item.box.l, item.box.t, item.box.l + cw, item.box.t + ch))
    }

    /** Puts a picture on the page the user is looking at, selected so it can be moved straight away. */
    fun placePicture(file: String, aspect: Float, signature: Boolean, width: Float = if (signature) 0.35f else 0.5f) {
        val page = currentPage() ?: return
        val w = width
        val h = min(w * page.shownW / page.shownH / aspect, 0.9f)
        // Centred where the user is looking on that page, kept inside the page.
        val cy = pageInView().second.coerceIn(h / 2, 1f - h / 2)
        val item = PictureItem(newId(), NRect(0.5f - w / 2, cy - h / 2, 0.5f + w / 2, cy + h / 2), file, signature)
        replaceItems(page) { it + item }
        tool = Tool.SELECT
        selected = page.id to item.id
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                PdfTools.loadScaled(ctx, uri)?.let { bmp ->
                    val f = File(PdfDoc.workDir(ctx), "pic_${newId()}.png")
                    FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    f.path to bmp.width.toFloat() / bmp.height
                }
            }
            if (saved == null) Toast.makeText(ctx, "Couldn't open that picture.", Toast.LENGTH_SHORT).show()
            else placePicture(saved.first, saved.second, signature = false)
        }
    }

    fun reopen(target: Uri) {
        session?.close(); session = null
        bitmaps.clear(); lines.clear(); selected = null
        fileUri = target
        version++
    }

    fun save(target: Uri?) {
        val s = state ?: return
        val src = fileUri
        scope.launch {
            busy = "Saving…"
            // Let go of the open file first, so it can be written over.
            session?.close(); session = null
            val result = runCatching { PdfDoc.save(ctx, src, s, target ?: src) }
            busy = null
            result.onSuccess {
                Toast.makeText(ctx, if (target == null) "Saved." else "Saved a copy.", Toast.LENGTH_SHORT).show()
                reopen(target ?: src)
            }.onFailure { e ->
                if (target == null && (e is SecurityException || e is java.io.FileNotFoundException || e is UnsupportedOperationException)) {
                    // Some folders don't allow changing a file in place: keep the edits and offer a copy.
                    session = runCatching { PdfSession.open(ctx, src) }.getOrNull()
                    askCopy = true
                } else {
                    session = runCatching { PdfSession.open(ctx, src) }.getOrNull()
                    Toast.makeText(ctx, "⚠️ Couldn't save: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val saveCopy = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { out ->
        if (out != null) save(out)
    }

    // A file opened from another app while this PDF is open; asks first if there are unsaved changes.
    DocLaunch.Receiver(DocLaunch.Kind.PDF, dirty, PdfEdit.displayName(ctx, fileUri), onOpenOther)

    BackHandler {
        when {
            showPages -> showPages = false
            selected != null -> selected = null
            dirty -> leaving = true
            else -> onClose()
        }
    }

    if (showPages && state != null && session != null) {
        PagesSheet(state, session!!, onChange = { push(it); selected = null }, onClose = { showPages = false })
        return
    }

    LaunchedEffect(state != null) {
        if (state == null || startDone) return@LaunchedEffect
        startDone = true
        when (startWith) {
            EditorStart.SIGN -> signing = true
            EditorStart.STAMP -> stamping = true
            EditorStart.READ -> Unit
        }
    }

    val findIn = state
    val findWith = session
    if (finding && findIn != null && findWith != null) {
        FindTextDialog(findIn, findWith, onGo = { page -> finding = false; scope.launch { list.animateScrollToItem(page) } }, onClose = { finding = false })
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF2A2F2C)).systemBarsPadding()) {
        // ---- top bar
        Row(
            Modifier.fillMaxWidth().background(Bg).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarButton("←") { if (dirty) leaving = true else onClose() }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val total = state?.pages?.size ?: 0
                Text(
                    if (total == 0) "Opening…" else "Page ${(pageShown + 1).coerceAtMost(total)} of $total" + if (dirty) " · not saved" else "",
                    color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            BarButton("↶", enabled = at > 0) { at--; selected = null }
            BarButton("↷", enabled = at < history.lastIndex) { at++; selected = null }
            Spacer(Modifier.width(6.dp))
            PillButton("Save", if (dirty) Accent else Card, if (dirty) OnAccent else TextSecondary, enabled = dirty && busy == null) { save(null) }
            Box {
                BarButton("⋮") { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Find text") }, onClick = { menu = false; finding = true })
                    DropdownMenuItem(text = { Text("Pages: rotate, reorder, delete") }, onClick = { menu = false; showPages = true })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; naming = NameAsk(NameFor.RENAME, ".pdf", name) })
                    DropdownMenuItem(text = { Text("Save as a copy…") }, onClick = { menu = false; naming = NameAsk(NameFor.COPY, ".pdf", name.removeSuffix(".pdf") + " (edited)") })
                    DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; sharePdf(ctx, fileUri) })
                }
            }
        }

        // ---- pages
        val s = state
        val sess = session
        if (s == null || sess == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                openError?.let { Text(it, color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(24.dp)) }
                    ?: CircularProgressIndicator(color = Accent)
            }
        } else {
            var scale by remember { mutableStateOf(1f) }
            var panX by remember { mutableStateOf(0f) }
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Two fingers zoom and move around in every tool; one finger is for the tool.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.count { it.pressed } >= 2) {
                                    scale = (scale * event.calculateZoom()).coerceIn(1f, 4f)
                                    val pan = event.calculatePan()
                                    val limit = (scale - 1f) * size.width / 2f
                                    panX = if (scale <= 1f) 0f else (panX + pan.x).coerceIn(-limit, limit)
                                    list.dispatchRawDelta(-pan.y / scale)
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
            ) {
                val widthPx = with(density) { (maxWidth - 24.dp).toPx() }.toInt()
                LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = panX },
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    itemsIndexed(s.pages, key = { _, p -> p.id }) { _, page ->
                        EditorPage(
                            page = page,
                            items = s.itemsOn(page),
                            session = sess,
                            widthPx = (widthPx * 1.5f).toInt(),
                            tool = tool,
                            selectedId = selected?.takeIf { it.first == page.id }?.second,
                            pen = InkItem(0, emptyList(), penColor, penWidth, 1f),
                            markColor = markColor, markKind = markKind, shapeKind = shapeKind, shapeColor = penColor,
                            bitmaps = bitmaps, lines = lines, pagePx = pagePx, pictures = pictures, measurer = measurer,
                            onSelect = { id -> selected = id?.let { page.id to it } },
                            onReplace = { item -> replaceItems(page) { l -> l.map { if (it.id == item.id) item else it } } },
                            onAdd = { item -> replaceItems(page) { it + item } },
                            onErase = { ids -> if (ids.isNotEmpty()) replaceItems(page) { l -> l.filter { it.id !in ids } } },
                            onTextAt = { p, existing -> textJob = TextJob(page, p, existing) },
                            onLine = { item -> lineJob = LineJob(page, item) },
                            onMessage = { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() },
                        )
                    }
                }
            }
        }

        // ---- options for the tool, or for the selected item
        val sel = selected?.let { (pid, iid) -> state?.pages?.firstOrNull { it.id == pid }?.let { p -> p to state.itemsOn(p).firstOrNull { it.id == iid } } }
        Column(Modifier.fillMaxWidth().background(Bg).navigationBarsPadding()) {
            val row = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)
            if (sel?.second != null) {
                val (page, item) = sel.first to sel.second!!
                Row(row, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    when (item) {
                        is TextBoxItem -> Chip("✎ Edit") { textJob = TextJob(page, null, item) }
                        is TextEditItem -> Chip("✎ Edit") { lineJob = LineJob(page, item) }
                        else -> Unit
                    }
                    if (item !is PictureItem && item !is TextEditItem) {
                        (if (item is MarkItem) MarkPalette else Palette).forEach { c ->
                            Swatch(c, selected = false) { replaceItems(page) { l -> l.map { if (it.id == item.id) it.recolored(c) else it } } }
                        }
                    }
                    Chip(if (item is TextEditItem) "↺ Undo this edit" else "🗑 Delete", danger = true) {
                        replaceItems(page) { l -> l.filter { it.id != item.id } }
                        selected = null
                    }
                    Chip("Done") { selected = null }
                }
            } else {
                Row(row, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    when (tool) {
                        Tool.SELECT -> Text("Tap anything you added to move, resize or delete it. Two fingers zoom.", color = TextSecondary, fontSize = 13.sp)
                        Tool.EDIT_TEXT -> Text("Tap a line of text to change or delete it.", color = TextSecondary, fontSize = 13.sp)
                        Tool.TEXT -> {
                            Text("Tap where the text goes", color = TextSecondary, fontSize = 13.sp)
                            Palette.forEach { c -> Swatch(c, c == textColor) { textColor = c } }
                            TextSizes.forEach { (label, v) -> Chip(label, on = textSize == v) { textSize = v } }
                        }
                        Tool.PEN -> {
                            Palette.forEach { c -> Swatch(c, c == penColor) { penColor = c } }
                            PenWidths.forEach { (label, v) -> Chip(label, on = penWidth == v) { penWidth = v } }
                        }
                        Tool.MARK -> {
                            listOf(MarkKind.HIGHLIGHT to "Highlight", MarkKind.UNDERLINE to "Underline", MarkKind.STRIKE to "Strike").forEach { (k, label) ->
                                Chip(label, on = markKind == k) { markKind = k }
                            }
                            (if (markKind == MarkKind.HIGHLIGHT) MarkPalette else Palette).forEach { c -> Swatch(c, c == markColor) { markColor = c } }
                        }
                        Tool.SHAPE -> {
                            listOf(ShapeKind.RECT to "▭", ShapeKind.OVAL to "◯", ShapeKind.LINE to "╱", ShapeKind.ARROW to "➚").forEach { (k, label) ->
                                Chip(label, on = shapeKind == k) { shapeKind = k }
                            }
                            Palette.forEach { c -> Swatch(c, c == penColor) { penColor = c } }
                        }
                        Tool.ERASER -> Text("Rub over drawings, shapes or notes to remove them.", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Tool.entries.forEach { t ->
                    ToolButton(t.icon, t.label, on = tool == t) {
                        tool = t; selected = null
                        if (t == Tool.MARK && markKind == MarkKind.HIGHLIGHT && markColor !in MarkPalette) markColor = MarkPalette[0]
                    }
                }
                ToolButton("✍", "Sign", on = false) { signing = true }
                ToolButton("▣", "Stamp", on = false) { stamping = true }
                ToolButton("🖼", "Image", on = false) { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                ToolButton("▤", "Pages", on = false) { showPages = true }
            }
        }
    }

    // ---- dialogs
    textJob?.let { job ->
        var text by remember(job) { mutableStateOf(job.item?.text.orEmpty()) }
        var color by remember(job) { mutableIntStateOf(job.item?.color ?: textColor) }
        var size by remember(job) { mutableStateOf(job.item?.size ?: textSize) }
        var bold by remember(job) { mutableStateOf(job.item?.bold ?: false) }
        AlertDialog(
            onDismissRequest = { textJob = null },
            title = { Text(if (job.item == null) "Add text" else "Edit text") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(text, { text = it }, minLines = 2, label = { Text("Text") }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Palette.forEach { c -> Swatch(c, c == color) { color = c } }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextSizes.forEach { (label, v) -> Chip(label, on = size == v) { size = v } }
                        Chip("B", on = bold) { bold = !bold }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    textJob = null
                    if (text.isBlank()) {
                        job.item?.let { old -> replaceItems(job.page) { l -> l.filter { it.id != old.id } } }
                        return@TextButton
                    }
                    textColor = color; textSize = size
                    val base = job.item?.copy(text = text.trimEnd(), color = color, size = size, bold = bold)
                        ?: TextBoxItem(newId(), NRect(job.at!!.x, job.at.y, job.at.x + 0.1f, job.at.y + 0.05f), text.trimEnd(), color, size, bold)
                    val fitted = fitText(job.page, base)
                    if (job.item == null) replaceItems(job.page) { it + fitted }
                    else replaceItems(job.page) { l -> l.map { if (it.id == fitted.id) fitted else it } }
                    // Selected, ready to drag into place.
                    tool = Tool.SELECT
                    selected = job.page.id to fitted.id
                }) { Text(if (job.item == null) "Add" else "Done") }
            },
            dismissButton = { TextButton(onClick = { textJob = null }) { Text("Cancel") } },
        )
    }

    lineJob?.let { job ->
        var text by remember(job) { mutableStateOf(job.item.text) }
        AlertDialog(
            onDismissRequest = { lineJob = null },
            title = { Text("Edit text") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Was: “${job.item.original}”", color = TextSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("New text") }, modifier = Modifier.fillMaxWidth())
                    Text("Leave it empty to delete this text.", color = TextSecondary, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    lineJob = null
                    val item = job.item.copy(text = text)
                    val exists = history[at].itemsOn(job.page).any { it.id == item.id }
                    if (exists) replaceItems(job.page) { l -> l.map { if (it.id == item.id) item else it } }
                    else if (text != job.item.original) replaceItems(job.page) { it + item }
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { lineJob = null }) { Text("Cancel") } },
        )
    }

    if (signing) {
        SignatureDialog(onDismiss = { signing = false }) { file, aspect ->
            signing = false
            placePicture(file, aspect, signature = true)
        }
    }

    if (stamping) {
        StampDialog(onDismiss = { stamping = false }) { file, aspect ->
            stamping = false
            placePicture(file, aspect, signature = false, width = 0.4f)
        }
    }

    if (leaving) {
        AlertDialog(
            onDismissRequest = { leaving = false },
            title = { Text("Save your changes?") },
            text = { Text("Your edits to $name aren't saved yet.") },
            confirmButton = { TextButton(onClick = { leaving = false; save(null) }) { Text("Save") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { leaving = false; onClose() }) { Text("Discard") }
                    TextButton(onClick = { leaving = false }) { Text("Keep editing") }
                }
            },
        )
    }

    naming?.let { ask ->
        NameDialog(ask.why.title, ask.initial, ask.ext, confirm = ask.why.confirm, onDismiss = { naming = null }, onDone = { n ->
            naming = null
            if (ask.why != NameFor.RENAME) { saveCopy.launch(n + ask.ext); return@NameDialog }
            val myDoc = fileUri.takeIf { it.scheme == "file" }?.path?.let { java.io.File(it).parentFile }?.let { MyFiles.Doc(it) }
            when {
                myDoc != null -> {
                    if (MyFiles.rename(myDoc, n)) renamedTimes++ else Toast.makeText(ctx, "Couldn't rename this file.", Toast.LENGTH_SHORT).show()
                }
                dirty -> Toast.makeText(ctx, "Save your changes first, then rename.", Toast.LENGTH_LONG).show()
                else -> scope.launch {
                    val renamed = withContext(Dispatchers.IO) { FileNames.renameDocument(ctx, fileUri, n + ask.ext) }
                    if (renamed == null) Toast.makeText(ctx, "This folder doesn't allow renaming. Use Save as a copy instead.", Toast.LENGTH_LONG).show()
                    else reopen(renamed)
                }
            }
        })
    }
    if (askCopy) {
        AlertDialog(
            onDismissRequest = { askCopy = false },
            title = { Text("Save as a copy?") },
            text = { Text("This file's folder doesn't let apps change it. Your edits are still here; save them as a new PDF instead.") },
            confirmButton = { TextButton(onClick = { askCopy = false; naming = NameAsk(NameFor.COPY, ".pdf", name.removeSuffix(".pdf") + " (edited)") }) { Text("Choose where") } },
            dismissButton = { TextButton(onClick = { askCopy = false }) { Text("Cancel") } },
        )
    }

    busy?.let { BusyDialog(it) }
}

// -------------------------------------------------------------------------------------------- one page

@Composable
private fun EditorPage(
    page: PageRef,
    items: List<EditItem>,
    session: PdfSession,
    widthPx: Int,
    tool: Tool,
    selectedId: Long?,
    pen: InkItem,
    markColor: Int,
    markKind: MarkKind,
    shapeKind: ShapeKind,
    shapeColor: Int,
    bitmaps: MutableMap<Long, Bitmap>,
    lines: MutableMap<Long, List<TextLine>>,
    pagePx: MutableMap<Long, IntSize>,
    pictures: MutableMap<String, ImageBitmap>,
    measurer: TextMeasurer,
    onSelect: (Long?) -> Unit,
    onReplace: (EditItem) -> Unit,
    onAdd: (EditItem) -> Unit,
    onErase: (Set<Long>) -> Unit,
    onTextAt: (NPoint, TextBoxItem?) -> Unit,
    onLine: (TextEditItem) -> Unit,
    onMessage: (String) -> Unit,
) {
    val ctx = LocalContext.current
    LaunchedEffect(page.source, page.turns, widthPx) {
        val bmp = runCatching {
            if (page.source < 0) Bitmap.createBitmap(widthPx, (widthPx * page.shownH / page.shownW).toInt().coerceAtLeast(1), Bitmap.Config.RGB_565).apply { eraseColor(android.graphics.Color.WHITE) }
            else session.render(page.source, widthPx).turnedBy(page.turns)
        }.getOrNull()
        if (bmp != null) bitmaps[page.id] = bmp
    }
    val needsLines = tool == Tool.EDIT_TEXT || tool == Tool.MARK
    LaunchedEffect(needsLines, page.source, page.turns) {
        if (needsLines && page.id !in lines) {
            lines[page.id] = if (page.source < 0 || page.turns != 0) emptyList() else session.lines(page.source)
        }
    }
    LaunchedEffect(items) {
        items.filterIsInstance<PictureItem>().forEach { p ->
            if (p.file !in pictures) BitmapFactory.decodeFile(p.file)?.let { pictures[p.file] = it.asImageBitmap() }
        }
    }

    // Live feedback while a finger is down.
    var livePoints by remember { mutableStateOf<List<NPoint>>(emptyList()) }
    var liveItem by remember { mutableStateOf<EditItem?>(null) }
    var erasing by remember { mutableStateOf<Set<Long>>(emptySet()) }

    val current by rememberUpdatedState(items)
    val currentTool by rememberUpdatedState(tool)
    val currentSel by rememberUpdatedState(selectedId)
    val currentPen by rememberUpdatedState(pen)
    val currentMark by rememberUpdatedState(markColor to markKind)
    val currentShape by rememberUpdatedState(shapeKind to shapeColor)

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(page.shownW / page.shownH)
            .background(Color.White)
            .onSizeChanged { pagePx[page.id] = it }
            .pointerInput(page.id) {
                val handle = 26.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun n(o: Offset) = NPoint((o.x / size.width).coerceIn(0f, 1f), (o.y / size.height).coerceIn(0f, 1f))
                    val p0 = n(down.position)
                    val list = current
                    when (currentTool) {
                        Tool.SELECT -> {
                            val sel = list.firstOrNull { it.id == currentSel }
                            val b = sel?.bounds
                            val onHandle = b != null && sel !is TextEditItem &&
                                abs(down.position.x - b.r * size.width) < handle && abs(down.position.y - b.b * size.height) < handle
                            val inside = b != null && sel !is TextEditItem && b.contains(p0.x, p0.y, 0.01f)
                            // Grab the selected item, or whatever was added under the finger.
                            val target = if (sel != null && (onHandle || inside)) sel
                            else list.lastOrNull { it !is TextEditItem && hits(it, p0) }
                            if (target != null) {
                                down.consume()
                                if (target.id != currentSel) onSelect(target.id)
                                var last = p0
                                val done = drag(down) { o ->
                                    val p = n(o)
                                    liveItem = if (onHandle) resizeTo(target, p) else target.moved(p.x - p0.x, p.y - p0.y)
                                    last = p
                                }
                                val result = liveItem
                                liveItem = null
                                if (done && result != null && (abs(last.x - p0.x) > 0.002f || abs(last.y - p0.y) > 0.002f)) onReplace(result)
                            } else if (awaitTap(down)) {
                                onSelect(list.lastOrNull { hits(it, p0) }?.id)
                            }
                        }
                        Tool.TEXT -> if (awaitTap(down)) {
                            val hit = list.lastOrNull { it is TextBoxItem && hits(it, p0) } as? TextBoxItem
                            onTextAt(p0, hit)
                        }
                        Tool.EDIT_TEXT -> if (awaitTap(down)) {
                            val earlier = list.lastOrNull { it is TextEditItem && it.box.contains(p0.x, p0.y, 0.005f) } as? TextEditItem
                            if (earlier != null) onLine(earlier)
                            else {
                                val pageLines = lines[page.id]
                                val line = pageLines?.firstOrNull { it.box.contains(p0.x, p0.y, 0.004f) }
                                when {
                                    pageLines == null -> onMessage("Reading the page's text…")
                                    page.turns != 0 -> onMessage("Save first to edit text on a turned page.")
                                    line == null -> onMessage(if (pageLines.isEmpty()) "This page has no editable text (it may be a scan)." else "Tap right on a line of text.")
                                    else -> onLine(lineEdit(line, page, bitmaps[page.id]))
                                }
                            }
                        }
                        Tool.PEN -> {
                            down.consume()
                            val pts = mutableListOf(p0)
                            livePoints = pts.toList()
                            val done = drag(down) { o -> pts += n(o); livePoints = pts.toList() }
                            livePoints = emptyList()
                            if (done) onAdd(currentPen.copy(id = newId(), points = pts.toList()))
                        }
                        Tool.MARK -> {
                            down.consume()
                            val (color, kind) = currentMark
                            val pageLines = lines[page.id].orEmpty()
                            val from = charAt(pageLines, p0)
                            if (from != null) {
                                var to: Pair<Int, Int> = from
                                liveItem = MarkItem(0, selectionRects(pageLines, from, to), color, kind)
                                val done = drag(down) { o ->
                                    charAt(pageLines, n(o), reach = 0.06f)?.let { to = it }
                                    liveItem = MarkItem(0, selectionRects(pageLines, from, to), color, kind)
                                }
                                val rects = selectionRects(pageLines, from, to)
                                liveItem = null
                                if (done && rects.isNotEmpty()) onAdd(MarkItem(newId(), rects, color, kind))
                            } else if (kind == MarkKind.HIGHLIGHT) {
                                // No text here (e.g. a scan): a see-through marker instead.
                                val pts = mutableListOf(p0)
                                val marker = InkItem(0, emptyList(), color, 0.02f, 0.4f)
                                liveItem = marker.copy(points = pts.toList())
                                val done = drag(down) { o -> pts += n(o); liveItem = marker.copy(points = pts.toList()) }
                                liveItem = null
                                if (done && pts.size > 1) onAdd(marker.copy(id = newId(), points = pts.toList()))
                            } else {
                                onMessage("Start on a word to underline or strike it out.")
                            }
                        }
                        Tool.SHAPE -> {
                            down.consume()
                            val (kind, color) = currentShape
                            var end = p0
                            val done = drag(down) { o -> end = n(o); liveItem = ShapeItem(0, p0, end, kind, color, 0.004f) }
                            liveItem = null
                            if (done && (abs(end.x - p0.x) > 0.01f || abs(end.y - p0.y) > 0.01f)) onAdd(ShapeItem(newId(), p0, end, kind, color, 0.004f))
                        }
                        Tool.ERASER -> {
                            down.consume()
                            val gone = mutableSetOf<Long>()
                            fun rub(p: NPoint) {
                                list.forEach { if (it !is TextEditItem && hits(it, p)) gone += it.id }
                                erasing = gone.toSet()
                            }
                            rub(p0)
                            val done = drag(down) { o -> rub(n(o)) }
                            erasing = emptySet()
                            if (done) onErase(gone) else Unit
                        }
                    }
                }
            },
    ) {
        bitmaps[page.id]?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize()) }
            ?: CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = Accent, strokeWidth = 2.dp)
        Canvas(Modifier.fillMaxSize()) {
            if (tool == Tool.EDIT_TEXT) lines[page.id]?.forEach { line ->
                drawRect(
                    Color(0x552F6FDB), topLeft = Offset(line.box.l * size.width, line.box.t * size.height),
                    size = Size(line.box.w * size.width, line.box.h * size.height), style = Stroke(1.dp.toPx()),
                )
            }
            val preview = liveItem
            items.forEach { item ->
                if (item.id in erasing) return@forEach
                if (preview != null && item.id == preview.id) return@forEach
                drawEditItem(item, measurer, pictures)
            }
            if (preview != null) drawEditItem(preview, measurer, pictures)
            if (livePoints.isNotEmpty()) drawEditItem(pen.copy(points = livePoints), measurer, pictures)
            val sel = (preview?.takeIf { it.id == selectedId }) ?: items.firstOrNull { it.id == selectedId }
            if (sel != null) {
                val b = sel.bounds
                val pad = 4.dp.toPx()
                drawRect(
                    Accent, topLeft = Offset(b.l * size.width - pad, b.t * size.height - pad),
                    size = Size(b.w * size.width + 2 * pad, b.h * size.height + 2 * pad),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
                if (sel !is TextEditItem) {
                    drawCircle(Color.White, 11.dp.toPx(), Offset(b.r * size.width, b.b * size.height))
                    drawCircle(Accent, 8.dp.toPx(), Offset(b.r * size.width, b.b * size.height))
                }
            }
        }
    }
}

/** A new text edit for [line], with the page colour behind it and the text colour taken from the page picture. */
private fun lineEdit(line: TextLine, page: PageRef, bmp: Bitmap?): TextEditItem {
    var bg = android.graphics.Color.WHITE
    var fg = android.graphics.Color.BLACK
    if (bmp != null) {
        fun px(x: Float, y: Float) = bmp.getPixel((x * bmp.width).toInt().coerceIn(0, bmp.width - 1), (y * bmp.height).toInt().coerceIn(0, bmp.height - 1))
        val b = line.box
        val dx = 2f / bmp.width; val dy = 2f / bmp.height
        val around = listOf(px(b.l - dx, b.t - dy), px(b.r + dx, b.t - dy), px(b.l - dx, b.b + dy), px(b.r + dx, b.b + dy))
        bg = around.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key
        fun dist(c: Int) = abs(android.graphics.Color.red(c) - android.graphics.Color.red(bg)) +
            abs(android.graphics.Color.green(c) - android.graphics.Color.green(bg)) + abs(android.graphics.Color.blue(c) - android.graphics.Color.blue(bg))
        val inside = (0 until 16).flatMap { i -> (0 until 5).map { j -> px(b.l + b.w * (i + 0.5f) / 16, b.t + b.h * (j + 0.5f) / 5) } }
        fg = inside.maxByOrNull { dist(it) } ?: fg
        if (dist(fg) < 60) fg = android.graphics.Color.BLACK
    }
    val box = line.box
    return TextEditItem(
        id = newId(), box = box, original = line.text, text = line.text, color = fg or 0xFF000000.toInt(), background = bg or 0xFF000000.toInt(),
        size = line.sizePt / page.unitPt, baseline = ((line.baseline - box.t) / box.h).coerceIn(0.1f, 1f),
        bold = line.bold, family = line.family,
    )
}

private fun hits(item: EditItem, p: NPoint): Boolean = when (item) {
    is InkItem -> distanceToPath(item.points, p) < max(item.width * 1.2f, 0.015f)
    is ShapeItem -> if (item.kind == ShapeKind.LINE || item.kind == ShapeKind.ARROW) distanceToPath(listOf(item.start, item.end), p) < 0.02f
    else item.bounds.contains(p.x, p.y, 0.012f)
    else -> item.bounds.contains(p.x, p.y, 0.01f)
}

/** Resizes from the bottom-right handle; pictures and text keep their shape. */
private fun resizeTo(item: EditItem, p: NPoint): EditItem {
    val b = item.bounds
    val w = max(0.02f, p.x - b.l)
    val h = max(0.01f, p.y - b.t)
    return when (item) {
        is PictureItem, is TextBoxItem -> {
            val s = max(w / max(b.w, 0.001f), 0.1f)
            item.resized(NRect(b.l, b.t, b.l + b.w * s, b.t + b.h * s))
        }
        else -> item.resized(NRect(b.l, b.t, b.l + w, b.t + h))
    }
}

/** Follows one finger; false if a second finger came down (zooming) or the gesture was taken over. */
private suspend fun AwaitPointerEventScope.drag(down: PointerInputChange, onMove: (Offset) -> Unit): Boolean {
    while (true) {
        val event = awaitPointerEvent()
        if (event.changes.count { it.pressed } > 1) return false
        val c = event.changes.firstOrNull { it.id == down.id } ?: return false
        if (!c.pressed) return true
        if (c.isConsumed) return false
        c.consume()
        onMove(c.position)
    }
}

/** True for a tap: the finger lifts without moving (a move lets the page scroll instead). */
private suspend fun AwaitPointerEventScope.awaitTap(down: PointerInputChange): Boolean {
    val slop = viewConfiguration.touchSlop
    while (true) {
        val event = awaitPointerEvent()
        if (event.changes.size > 1) return false
        val c = event.changes.firstOrNull { it.id == down.id } ?: return false
        if (c.isConsumed) return false
        if ((c.position - down.position).getDistance() > slop) return false
        if (!c.pressed) return true
    }
}

// -------------------------------------------------------------------------------------------- drawing

private fun DrawScope.drawEditItem(item: EditItem, measurer: TextMeasurer, pictures: Map<String, ImageBitmap>) {
    val w = size.width; val h = size.height
    val unit = (w + h) / 2
    fun o(p: NPoint) = Offset(p.x * w, p.y * h)
    when (item) {
        is InkItem -> {
            if (item.points.isEmpty()) return
            val path = Path().apply {
                moveTo(item.points[0].x * w, item.points[0].y * h)
                if (item.points.size == 1) lineTo(item.points[0].x * w + 0.5f, item.points[0].y * h)
                item.points.drop(1).forEach { lineTo(it.x * w, it.y * h) }
            }
            drawPath(path, Color(item.color).copy(alpha = item.alpha), style = Stroke(item.width * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        is MarkItem -> item.rects.forEach { r ->
            val c = Color(item.color)
            when (item.kind) {
                MarkKind.HIGHLIGHT -> drawRect(c.copy(alpha = 0.55f), Offset(r.l * w, r.t * h), Size(r.w * w, r.h * h), blendMode = BlendMode.Multiply)
                MarkKind.UNDERLINE -> drawLine(c, Offset(r.l * w, (r.b - r.h * 0.12f) * h), Offset(r.r * w, (r.b - r.h * 0.12f) * h), max(1.5f, r.h * h * 0.07f))
                MarkKind.STRIKE -> drawLine(c, Offset(r.l * w, (r.cy + r.h * 0.08f) * h), Offset(r.r * w, (r.cy + r.h * 0.08f) * h), max(1.5f, r.h * h * 0.07f))
            }
        }
        is ShapeItem -> {
            val c = Color(item.color)
            val stroke = Stroke(item.width * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val b = item.bounds
            when (item.kind) {
                ShapeKind.RECT -> drawRect(c, Offset(b.l * w, b.t * h), Size(b.w * w, b.h * h), style = stroke)
                ShapeKind.OVAL -> drawOval(c, Offset(b.l * w, b.t * h), Size(b.w * w, b.h * h), style = stroke)
                ShapeKind.LINE, ShapeKind.ARROW -> {
                    val s = o(item.start); val e = o(item.end)
                    drawLine(c, s, e, stroke.width, cap = StrokeCap.Round)
                    if (item.kind == ShapeKind.ARROW) {
                        val ang = atan2((e.y - s.y).toDouble(), (e.x - s.x).toDouble())
                        val len = max(8f, stroke.width * 5)
                        for (side in listOf(-1, 1)) {
                            val a = ang + Math.PI + side * Math.PI / 7
                            drawLine(c, e, Offset(e.x + (len * cos(a)).toFloat(), e.y + (len * sin(a)).toFloat()), stroke.width, cap = StrokeCap.Round)
                        }
                    }
                }
            }
        }
        is TextBoxItem -> turnedFrame(item.box, item.turns) { _, _ ->
            val fs = item.size * unit
            val style = TextStyle(color = Color(item.color), fontSize = fs.toSp(), fontWeight = if (item.bold) FontWeight.Bold else FontWeight.Normal)
            item.text.split('\n').forEachIndexed { i, line ->
                val m = measurer.measure(line.ifEmpty { " " }, style)
                // Same baseline as the saved PDF: 0.93 of the size down, then 1.2 per line.
                drawText(m, topLeft = Offset(fs * 0.15f, fs * (0.93f + 1.2f * i) - m.firstBaseline))
            }
        }
        is PictureItem -> pictures[item.file]?.let { img ->
            turnedFrame(item.box, item.turns) { cw, ch ->
                drawImage(img, srcOffset = androidx.compose.ui.unit.IntOffset.Zero, srcSize = IntSize(img.width, img.height),
                    dstOffset = androidx.compose.ui.unit.IntOffset.Zero, dstSize = IntSize(cw.toInt().coerceAtLeast(1), ch.toInt().coerceAtLeast(1)))
            }
        }
        is TextEditItem -> {
            val b = item.box
            drawRect(Color(item.background), Offset(b.l * w, b.t * h), Size(b.w * w, b.h * h))
            if (item.text.isNotBlank()) turnedFrame(b, item.turns) { _, ch ->
                val style = TextStyle(
                    color = Color(item.color), fontSize = (item.size * unit).toSp(),
                    fontWeight = if (item.bold) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = when (item.family) { FontFamilyKind.SERIF -> FontFamily.Serif; FontFamilyKind.MONO -> FontFamily.Monospace; else -> FontFamily.SansSerif },
                )
                val m = measurer.measure(item.text, style, softWrap = false)
                drawText(m, topLeft = Offset(0f, ch * item.baseline - m.firstBaseline))
            }
        }
    }
}

/** Draws content in a box that may be turned by quarter turns; [block] gets the content's width and height. */
private fun DrawScope.turnedFrame(box: NRect, turns: Int, block: DrawScope.(Float, Float) -> Unit) {
    val w = box.w * size.width; val h = box.h * size.height
    val (cw, ch) = if (turns % 2 == 0) w to h else h to w
    val cx = box.cx * size.width; val cy = box.cy * size.height
    withTransform({
        translate(cx, cy)
        rotate(90f * turns, Offset.Zero)
        translate(-cw / 2, -ch / 2)
    }) { block(cw, ch) }
}

// -------------------------------------------------------------------------------------------- pages

@Composable
private fun PagesSheet(state: EditState, session: PdfSession, onChange: (EditState) -> Unit, onClose: () -> Unit) {
    val thumbs = remember { mutableStateMapOf<Pair<Int, Int>, Bitmap>() }
    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        ScreenTopBar("Pages", onBack = onClose, subtitle = "${state.pages.size} pages")
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(state.pages, key = { _, p -> p.id }) { i, page ->
                val key = page.source to page.turns
                LaunchedEffect(key) {
                    if (key !in thumbs && page.source >= 0) runCatching { session.render(page.source, 360).turnedBy(page.turns) }.getOrNull()?.let { thumbs[key] = it }
                }
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(CardHi).border(1.dp, Line, RoundedCornerShape(16.dp)).padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(page.shownW / page.shownH).background(Color.White).border(1.dp, Line)) {
                        thumbs[key]?.let { Image(it.asImageBitmap(), contentDescription = "Page ${i + 1}", modifier = Modifier.fillMaxSize()) }
                    }
                    Text("${i + 1}", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        MiniButton("‹", enabled = i > 0) { onChange(state.copy(pages = state.pages.toMutableList().apply { add(i - 1, removeAt(i)) })) }
                        MiniButton("↻") {
                            val turned = page.copy(turns = (page.turns + 1) % 4)
                            onChange(EditState(state.pages.map { if (it.id == page.id) turned else it }, state.items + (page.id to state.itemsOn(page).map { it.turned() })))
                        }
                        MiniButton("🗑", enabled = state.pages.size > 1) { onChange(state.copy(pages = state.pages.filter { it.id != page.id })) }
                        MiniButton("›", enabled = i < state.pages.lastIndex) { onChange(state.copy(pages = state.pages.toMutableList().apply { add(i + 1, removeAt(i)) })) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("+ Blank page", Card, TextPrimary, Modifier.weight(1f)) {
                val last = state.pages.last()
                onChange(state.copy(pages = state.pages + PageRef(newId(), -1, 0, last.shownW, last.shownH)))
            }
            PillButton("Done", Charcoal, Color.White, Modifier.weight(1f)) { onClose() }
        }
    }
}

// -------------------------------------------------------------------------------------------- signature

@Composable
private fun SignatureDialog(onDismiss: () -> Unit, onDone: (String, Float) -> Unit) {
    val ctx = LocalContext.current
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var live by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var ink by remember { mutableIntStateOf(0xFF1E1E1E.toInt()) }
    var padSize by remember { mutableStateOf(IntSize.Zero) }
    val saved = remember { mutableStateListOf<File>().apply { addAll(PdfDoc.savedSignatures(ctx)) } }
    val thumbs = remember { mutableStateMapOf<String, ImageBitmap>() }
    LaunchedEffect(Unit) {
        saved.toList().forEach { f ->
            withContext(Dispatchers.IO) { BitmapFactory.decodeFile(f.path) }?.let { thumbs[f.path] = it.asImageBitmap() }
        }
    }
    var keep by remember { mutableStateOf(true) }
    var forget by remember { mutableStateOf<File?>(null) }

    fun finish(bmpFile: File) {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(bmpFile.path, opts)
        onDone(bmpFile.path, opts.outWidth.toFloat() / opts.outHeight.coerceAtLeast(1))
    }

    // A placed signature uses its own copy, so deleting a saved one later can't break an open edit.
    fun useSaved(f: File) {
        val copy = File(PdfDoc.workDir(ctx), "sig_${newId()}.png")
        if (runCatching { f.copyTo(copy, overwrite = true) }.isSuccess) finish(copy)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your signature") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (saved.isNotEmpty()) {
                    Text("Tap a saved signature to use it", color = TextSecondary, fontSize = 13.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        saved.forEach { f ->
                            Box(
                                Modifier.size(width = 112.dp, height = 60.dp).clip(RoundedCornerShape(10.dp)).background(Color.White)
                                    .border(1.dp, Line, RoundedCornerShape(10.dp)).clickable { useSaved(f) },
                            ) {
                                thumbs[f.path]?.let { Image(it, contentDescription = "Saved signature", modifier = Modifier.fillMaxSize().padding(6.dp)) }
                                Box(
                                    Modifier.align(Alignment.TopEnd).padding(2.dp).size(22.dp).clip(CircleShape).background(Color(0x22000000))
                                        .clickable { forget = f },
                                    contentAlignment = Alignment.Center,
                                ) { Text("✕", color = Color(0xFF444444), fontSize = 11.sp) }
                            }
                        }
                    }
                    Text("Or draw a new one", color = TextSecondary, fontSize = 13.sp)
                }
                Box(
                    Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(12.dp))
                        .onSizeChanged { padSize = it }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                val pts = mutableListOf(down.position)
                                live = pts.toList()
                                drag(down) { pts += it; live = pts.toList() }
                                strokes += pts.toList()
                                live = emptyList()
                            }
                        },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        (strokes + listOf(live)).filter { it.isNotEmpty() }.forEach { s ->
                            val path = Path().apply { moveTo(s[0].x, s[0].y); s.drop(1).forEach { lineTo(it.x, it.y) } }
                            drawPath(path, Color(ink), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        drawLine(Color(0x22000000), Offset(16f, size.height * 0.78f), Offset(size.width - 16f, size.height * 0.78f), 1.dp.toPx())
                    }
                    if (strokes.isEmpty() && live.isEmpty()) Text("Sign here", color = Color(0x55000000), modifier = Modifier.align(Alignment.Center))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Swatch(0xFF1E1E1E.toInt(), ink == 0xFF1E1E1E.toInt()) { ink = 0xFF1E1E1E.toInt() }
                    Swatch(0xFF1F4FB8.toInt(), ink == 0xFF1F4FB8.toInt()) { ink = 0xFF1F4FB8.toInt() }
                    Chip("Clear") { strokes.clear() }
                    Chip(if (keep) "✓ Keep for later" else "Keep for later", on = keep) { keep = !keep }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = strokes.isNotEmpty(), onClick = {
                val all = strokes.flatten()
                val pad = 12f
                val l = all.minOf { it.x } - pad; val t = all.minOf { it.y } - pad
                val r = all.maxOf { it.x } + pad; val b = all.maxOf { it.y } + pad
                val bmp = Bitmap.createBitmap((r - l).toInt().coerceAtLeast(1), (b - t).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = ink; style = android.graphics.Paint.Style.STROKE; strokeWidth = 3f * ctx.resources.displayMetrics.density
                    strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
                }
                strokes.forEach { s ->
                    val path = android.graphics.Path().apply { moveTo(s[0].x - l, s[0].y - t); s.drop(1).forEach { lineTo(it.x - l, it.y - t) } }
                    canvas.drawPath(path, paint)
                }
                val file = File(PdfDoc.workDir(ctx), "sig_${newId()}.png")
                FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                // Kept privately on this phone, only when the user wants it.
                if (keep) runCatching { PdfDoc.keepSignature(ctx, file) }
                finish(file)
            }) { Text("Place it") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    forget?.let { f ->
        AlertDialog(
            onDismissRequest = { forget = null },
            title = { Text("Delete this saved signature?") },
            text = { Text("It's removed from this phone. Signatures already placed in PDFs stay.") },
            confirmButton = { TextButton(onClick = { f.delete(); saved.remove(f); forget = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { forget = null }) { Text("Cancel") } },
        )
    }
}

// -------------------------------------------------------------------------------------------- stamp

private enum class StampColor(val argb: Int) { RED(0xFFC62828.toInt()), GREEN(0xFF2E7D32.toInt()), BLUE(0xFF1F4FB8.toInt()) }

private val StampPresets = listOf(
    "APPROVED" to StampColor.GREEN, "PAID" to StampColor.GREEN, "RECEIVED" to StampColor.GREEN,
    "DRAFT" to StampColor.BLUE, "COPY" to StampColor.BLUE,
    "CONFIDENTIAL" to StampColor.RED, "REJECTED" to StampColor.RED, "VOID" to StampColor.RED,
)

/** A rubber-stamp look: bold capitals in a rounded frame, with an optional date line, on a clear background. */
private fun renderStamp(text: String, color: Int, date: String?): Bitmap {
    val big = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; textSize = 96f; typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        letterSpacing = 0.06f
    }
    val small = android.graphics.Paint(big).apply { textSize = 44f; letterSpacing = 0.02f }
    val pad = 36f
    val stroke = 9f
    val bigW = big.measureText(text)
    val smallW = date?.let { small.measureText(it) } ?: 0f
    val w = maxOf(bigW, smallW) + pad * 2
    val h = pad * 2 + 72f + (if (date != null) 64f else 0f)
    val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val frame = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = android.graphics.Paint.Style.STROKE; strokeWidth = stroke
    }
    canvas.drawRoundRect(stroke, stroke, w - stroke, h - stroke, 22f, 22f, frame)
    canvas.drawText(text, (w - bigW) / 2, pad + 72f, big)
    date?.let { canvas.drawText(it, (w - smallW) / 2, pad + 72f + 56f, small) }
    return bmp
}

@Composable
private fun StampDialog(onDismiss: () -> Unit, onDone: (String, Float) -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf(StampPresets[0].first) }
    var color by remember { mutableStateOf(StampPresets[0].second) }
    var dated by remember { mutableStateOf(true) }
    val today = remember { java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date()) }
    val label = text.trim().uppercase()
    val preview = remember(label, color, dated) { label.takeIf { it.isNotEmpty() }?.let { renderStamp(it, color.argb, today.takeIf { dated }).asImageBitmap() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a stamp") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) { preview?.let { Image(it, contentDescription = "Stamp preview") } }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StampPresets.forEach { (word, c) -> Chip(word, on = label == word) { text = word; color = c } }
                }
                OutlinedTextField(text, { text = it.take(24) }, singleLine = true, label = { Text("Or type your own") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StampColor.entries.forEach { c -> Swatch(c.argb, c == color) { color = c } }
                    Chip(if (dated) "✓ Today's date" else "Today's date", on = dated) { dated = !dated }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = label.isNotEmpty(), onClick = {
                val bmp = renderStamp(label, color.argb, today.takeIf { dated })
                val file = File(PdfDoc.workDir(ctx), "stamp_${newId()}.png")
                FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                onDone(file.path, bmp.width.toFloat() / bmp.height)
            }) { Text("Place it") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// -------------------------------------------------------------------------------------------- small parts

@Composable
private fun BarButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Card).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (enabled) TextPrimary else TextSecondary.copy(alpha = 0.4f), fontSize = 18.sp) }
}

@Composable
private fun ToolButton(icon: String, label: String, on: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(66.dp).clip(RoundedCornerShape(14.dp)).background(if (on) Accent else Color.Transparent).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, color = if (on) Color.White else TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = if (on) Color.White else TextSecondary, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun Chip(label: String, on: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (on) Charcoal else Card).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(label, color = if (on) Color.White else if (danger) Accent else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun Swatch(color: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(Color(color))
            .border(if (selected) 3.dp else 1.dp, if (selected) Accent else Line, CircleShape).clickable(onClick = onClick),
    )
}

@Composable
private fun MiniButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (enabled) TextPrimary else TextSecondary.copy(alpha = 0.3f), fontSize = 16.sp) }
}

internal fun sharePdf(ctx: android.content.Context, uri: Uri) {
    val shareUri = if (uri.scheme == "file") {
        val file = File(uri.path!!)
        try {
            androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        } catch (e: IllegalArgumentException) {
            // Outside the shared folders: share a copy from the cache instead.
            runCatching {
                val copy = File(File(ctx.cacheDir, "shared").apply { mkdirs() }, file.name)
                file.copyTo(copy, overwrite = true)
                androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", copy)
            }.getOrElse {
                Toast.makeText(ctx, "Couldn't share this PDF.", Toast.LENGTH_LONG).show()
                return
            }
        }
    } else uri
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("application/pdf").putExtra(android.content.Intent.EXTRA_STREAM, shareUri)
        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = android.content.ClipData.newRawUri("", shareUri)
    runCatching { ctx.startActivity(android.content.Intent.createChooser(send, "Share PDF")) }
}
