package com.cleanai.debloat.ui

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanai.debloat.data.MyFiles
import com.cleanai.debloat.data.PdfEdit
import com.cleanai.debloat.data.PdfMark
import com.cleanai.debloat.data.PdfPages
import kotlinx.coroutines.launch

private enum class PdfTool(val title: String, val blurb: String) {
    MERGE("Merge PDFs", "Join several PDFs into one"),
    EXTRACT("Extract pages", "Split out the pages you need"),
    REMOVE("Remove pages", "Delete pages you don't want"),
    COMPRESS("Compress", "Make a PDF smaller to share"),
}

/** PDF Reader & Editor: read with pinch-zoom, draw / highlight / add text, merge, split, remove pages and compress. */
@Composable
fun PdfScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf<Uri?>(null) }
    var tool by remember { mutableStateOf<PdfTool?>(null) }
    var source by remember { mutableStateOf<Uri?>(null) }
    val merging = remember { mutableStateListOf<Uri>() }
    var asking by remember { mutableStateOf(false) }
    var pagesText by remember { mutableStateOf("") }
    var quality by remember { mutableStateOf(0.6f) }
    var busy by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<Pair<String, Uri>?>(null) }
    // Runs once the user has picked where to save the result.
    var job by remember { mutableStateOf<(suspend (Uri) -> String)?>(null) }

    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { out ->
        val work = job ?: return@rememberLauncherForActivityResult
        job = null
        if (out == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = "Working on your PDF…"
            val message = runCatching { work(out) }.getOrElse { "⚠️ Couldn't finish: ${it.message ?: it.javaClass.simpleName}" }
            busy = null
            done = message to out
        }
    }
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (tool == null) open = uri else { source = uri; pagesText = ""; asking = true }
    }
    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) { merging.addAll(uris); tool = PdfTool.MERGE }
    }
    // Scans and photo PDFs already in My Files can be used straight away, without the file browser.
    val mine = remember { MyFiles.list(ctx).filter { it.hasPdf } }
    var choosing by remember { mutableStateOf(false) }

    fun choose(t: PdfTool?) {
        tool = t
        when {
            mine.isNotEmpty() -> choosing = true
            t == PdfTool.MERGE -> pickMany.launch(arrayOf("application/pdf"))
            else -> pickOne.launch(arrayOf("application/pdf"))
        }
    }

    fun chosen(uri: Uri) {
        choosing = false
        when (tool) {
            null -> open = uri
            PdfTool.MERGE -> merging += uri
            else -> { source = uri; pagesText = ""; asking = true }
        }
    }

    open?.let { uri ->
        PdfReader(uri, onClose = { open = null }, onSaved = { open = it })
        return
    }

    BackHandler(enabled = tool != null) { tool = null; merging.clear() }
    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        ScreenTopBar("PDF Reader & Editor", onBack = { if (tool != null) { tool = null; merging.clear() } else onBack() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (tool == PdfTool.MERGE) {
                Text("Merge in this order", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                merging.forEachIndexed { i, uri ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardHi).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${i + 1}.  ${PdfEdit.displayName(ctx, uri)}", color = TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        SmallButton("↑", enabled = i > 0) { merging.add(i - 1, merging.removeAt(i)) }
                        SmallButton("✕") { merging.removeAt(i) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Add more", Card, TextPrimary, Modifier.weight(1f)) { choose(PdfTool.MERGE) }
                    PillButton("Merge ${merging.size}", Charcoal, Color.White, Modifier.weight(1f), enabled = merging.size >= 2) {
                        val inputs = merging.toList()
                        job = { out -> PdfEdit.merge(ctx, inputs, out); "Merged ${inputs.size} PDFs." }
                        saveAs.launch("Merged.pdf")
                    }
                }
            } else {
                BigAction("Open a PDF", "Read, zoom, draw, highlight or add text") { tool = null; pickOne.launch(arrayOf("application/pdf")) }
                Text("Tools", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                PdfTool.entries.forEach { t ->
                    ToolRow(t.title, t.blurb) {
                        if (t == PdfTool.MERGE) merging.clear()
                        choose(t)
                    }
                }
                if (mine.isNotEmpty()) {
                    Text("Your PDFs in My Files", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                    mine.take(20).forEach { doc -> ToolRow(doc.title, doc.sizeText) { open = Uri.fromFile(doc.pdf) } }
                }
                Text(
                    "Everything happens on your phone — your PDFs are never uploaded.",
                    color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }

    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false; if (tool != PdfTool.MERGE || merging.isEmpty()) tool = null },
            title = { Text(if (tool == PdfTool.MERGE) "Add a PDF" else "Choose a PDF") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolRow("Browse your phone…", "Downloads, Drive and other folders") {
                        choosing = false
                        if (tool == PdfTool.MERGE) pickMany.launch(arrayOf("application/pdf")) else pickOne.launch(arrayOf("application/pdf"))
                    }
                    Text("From My Files", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    mine.take(30).forEach { doc -> ToolRow(doc.title, doc.sizeText) { chosen(Uri.fromFile(doc.pdf)) } }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosing = false; if (tool != PdfTool.MERGE || merging.isEmpty()) tool = null }) { Text("Cancel") } },
        )
    }

    // Asks which pages, or how much to compress, for the PDF just picked.
    val src = source
    if (asking && src != null && tool != null) {
        val t = tool!!
        val count = remember(src) { PdfEdit.pageCount(ctx, src) }
        val name = remember(src) { PdfEdit.displayName(ctx, src).removeSuffix(".pdf") }
        AlertDialog(
            onDismissRequest = { asking = false; tool = null },
            title = { Text(t.title) },
            text = {
                Column {
                    Text("$name · $count page${if (count == 1) "" else "s"}", color = TextSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    if (t == PdfTool.COMPRESS) {
                        Text(
                            when {
                                quality < 0.45f -> "Smallest file (pictures look rougher)"
                                quality < 0.7f -> "Balanced"
                                else -> "Best quality (smaller saving)"
                            },
                            fontSize = 15.sp,
                        )
                        Slider(quality, { quality = it }, valueRange = 0.3f..0.85f)
                    } else {
                        OutlinedTextField(
                            pagesText, { pagesText = it },
                            label = { Text(if (t == PdfTool.EXTRACT) "Pages to keep, e.g. 1-3, 5" else "Pages to remove, e.g. 2, 4-6") },
                            singleLine = true,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when (t) {
                        PdfTool.COMPRESS -> {
                            val q = quality
                            job = { out ->
                                val (before, after) = PdfEdit.compress(ctx, src, out, q, if (q < 0.45f) 1200 else 1800)
                                if (after in 1 until before) "Compressed: ${kb(before)} → ${kb(after)} (${100 - after * 100 / before}% smaller)."
                                else "Saved. This PDF had no pictures that could be made smaller (${kb(after)})."
                            }
                            saveAs.launch("$name (compressed).pdf")
                        }
                        else -> {
                            val chosen = PdfEdit.parsePages(pagesText, count)
                            val keep = when {
                                chosen == null -> null
                                t == PdfTool.EXTRACT -> chosen
                                else -> (0 until count).filter { it !in chosen }
                            }
                            if (keep.isNullOrEmpty()) {
                                Toast.makeText(ctx, "Type page numbers between 1 and $count.", Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }
                            job = { out -> PdfEdit.keepPages(ctx, src, keep, out); "Saved ${keep.size} page${if (keep.size == 1) "" else "s"}." }
                            saveAs.launch(if (t == PdfTool.EXTRACT) "$name (pages).pdf" else "$name (edited).pdf")
                        }
                    }
                    asking = false
                    tool = null
                }) { Text("Choose where to save") }
            },
            dismissButton = { TextButton(onClick = { asking = false; tool = null }) { Text("Cancel") } },
        )
    }

    busy?.let { BusyDialog(it) }
    done?.let { (message, out) ->
        AlertDialog(
            onDismissRequest = { done = null },
            title = { Text(if (message.startsWith("⚠️")) "Something went wrong" else "Done") },
            text = { Text(message) },
            confirmButton = {
                if (!message.startsWith("⚠️")) TextButton(onClick = { done = null; tool = null; merging.clear(); open = out }) { Text("Open it") }
            },
            dismissButton = { TextButton(onClick = { done = null; tool = null; merging.clear() }) { Text("Close") } },
        )
    }
}

private fun kb(bytes: Long) = if (bytes >= 1_000_000) String.format("%.1f MB", bytes / 1e6) else "${bytes / 1000} KB"

// ---------------------------------------------------------------- reader

private val InkColors = listOf(Color(0xFF1E1E1E), Color(0xFFD64535), Color(0xFF2F6FDB), Color(0xFF2E9E5B))
private val HighlightColor = Color(0xFFFFE14D)

private enum class Pen { PEN, HIGHLIGHT, TEXT }

@Composable
private fun PdfReader(uri: Uri, onClose: () -> Unit, onSaved: (Uri) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pages = remember(uri) { runCatching { PdfPages(ctx, uri) } }
    DisposableEffect(uri) { onDispose { pages.getOrNull()?.close() } }
    val name = remember(uri) { PdfEdit.displayName(ctx, uri) }
    val marks = remember(uri) { mutableStateMapOf<Int, SnapshotStateList<PdfMark>>() }
    var editing by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var leaving by remember { mutableStateOf(false) }
    val hasMarks = marks.values.any { it.isNotEmpty() }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { out ->
        if (out == null) return@rememberLauncherForActivityResult
        val snapshot = marks.mapValues { it.value.toList() }
        scope.launch {
            busy = "Saving your annotations…"
            val ok = runCatching { PdfEdit.annotate(ctx, uri, out, snapshot) }
            busy = null
            Toast.makeText(ctx, if (ok.isSuccess) "Saved a copy with your annotations." else "⚠️ Couldn't save: ${ok.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            // Show the saved copy, so the marks stay on screen and further notes build on it.
            if (ok.isSuccess) onSaved(out)
        }
    }

    editing?.let { index ->
        val doc = pages.getOrNull() ?: return
        PageEditor(doc, index, marks.getOrPut(index) { mutableStateListOf() }, onDone = { editing = null })
        return
    }

    BackHandler { if (hasMarks) leaving = true else onClose() }
    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        ScreenTopBar(name, onBack = { if (hasMarks) leaving = true else onClose() }, subtitle = pages.getOrNull()?.let { "${it.count} pages" })
        val doc = pages.getOrElse { e ->
            Text(
                if (e is SecurityException) "This PDF is password-protected. Open it in an app that can unlock it first."
                else "Couldn't open this PDF: ${e.message}",
                color = TextPrimary, fontSize = 16.sp, modifier = Modifier.padding(24.dp),
            )
            return@Column
        }
        var scale by remember { mutableStateOf(1f) }
        var panX by remember { mutableStateOf(0f) }
        val list = rememberLazyListState()
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(0.dp))
                // Two fingers zoom and pan sideways; one finger scrolls the pages as usual.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.size >= 2) {
                                scale = (scale * event.calculateZoom()).coerceIn(1f, 4f)
                                val limit = size.width * (scale - 1) / 2
                                panX = (panX + event.calculatePan().x).coerceIn(-limit, limit)
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                        if (scale <= 1.01f) { scale = 1f; panX = 0f }
                    }
                },
        ) {
            val widthPx = with(LocalDensity.current) { (maxWidth - 24.dp).toPx() }.toInt()
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = panX },
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            ) {
                itemsIndexed(doc.ratios) { i, ratio ->
                    PageView(doc, i, ratio, (widthPx * 1.5f).toInt(), marks[i].orEmpty())
                }
            }
            Text(
                "${list.firstVisibleItemIndex + 1} / ${doc.count}",
                color = Color.White, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().background(CardHi).navigationBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PillButton("✏  Annotate page", Charcoal, Color.White, Modifier.weight(1.3f)) { editing = list.firstVisibleItemIndex }
            if (hasMarks) PillButton("Save copy", Coral, Color.White, Modifier.weight(1f)) { save.launch(name.removeSuffix(".pdf") + " (annotated).pdf") }
            else PillButton("Share", Card, TextPrimary, Modifier.weight(1f)) { share(ctx, uri) }
        }
    }
    busy?.let { BusyDialog(it) }
    if (leaving) {
        AlertDialog(
            onDismissRequest = { leaving = false },
            title = { Text("Leave without saving?") },
            text = { Text("Your drawings and notes are not saved yet. Tap \"Save copy\" to keep them.") },
            confirmButton = { TextButton(onClick = { leaving = false; onClose() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { leaving = false }) { Text("Keep editing") } },
        )
    }
}

private fun share(ctx: android.content.Context, uri: Uri) {
    val shareUri = if (uri.scheme == "file") {
        androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", java.io.File(uri.path!!))
    } else uri
    val send = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, shareUri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri("", shareUri)
    runCatching { ctx.startActivity(Intent.createChooser(send, "Share PDF")) }
}

@Composable
private fun PageView(doc: PdfPages, index: Int, ratio: Float, widthPx: Int, marks: List<PdfMark>) {
    var bmp by remember(doc, index) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(doc, index, widthPx) { bmp = runCatching { doc.render(index, widthPx) }.getOrNull() }
    Box(
        Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(6.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(6.dp)),
    ) {
        bmp?.let { Image(it.asImageBitmap(), contentDescription = "Page ${index + 1}", modifier = Modifier.fillMaxSize()) }
            ?: CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = Coral, strokeWidth = 2.dp)
        if (marks.isNotEmpty()) Canvas(Modifier.fillMaxSize()) { drawMarks(marks) }
    }
}

private fun DrawScope.drawMarks(marks: List<PdfMark>, live: List<Offset> = emptyList(), livePen: PdfMark.Ink? = null) {
    fun ink(points: List<Pair<Float, Float>>, color: Int, width: Float, alpha: Float) {
        if (points.isEmpty()) return
        val path = Path().apply {
            moveTo(points[0].first * size.width, points[0].second * size.height)
            if (points.size == 1) lineTo(points[0].first * size.width + 0.5f, points[0].second * size.height)
            points.drop(1).forEach { lineTo(it.first * size.width, it.second * size.height) }
        }
        drawPath(path, Color(color).copy(alpha = alpha), style = Stroke(width * size.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    marks.forEach { m ->
        when (m) {
            is PdfMark.Ink -> ink(m.points, m.color, m.width, m.alpha)
            is PdfMark.Note -> drawContext.canvas.nativeCanvas.drawText(
                m.text, m.x * size.width, m.y * size.height + m.size * size.width * 0.8f,
                android.graphics.Paint().apply { color = m.color; textSize = m.size * size.width; isAntiAlias = true; typeface = android.graphics.Typeface.SANS_SERIF },
            )
        }
    }
    if (livePen != null && live.isNotEmpty()) ink(live.map { it.x / size.width to it.y / size.height }, livePen.color, livePen.width, livePen.alpha)
}

/** One page, full screen: draw with a pen, highlight, or tap to place text. */
@Composable
private fun PageEditor(doc: PdfPages, index: Int, marks: SnapshotStateList<PdfMark>, onDone: () -> Unit) {
    var pen by remember { mutableStateOf(Pen.PEN) }
    var color by remember { mutableStateOf(InkColors[1]) }
    val live = remember { mutableStateListOf<Offset>() }
    var noteAt by remember { mutableStateOf<Offset?>(null) }
    var noteText by remember { mutableStateOf("") }
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    BackHandler(onBack = onDone)

    Column(Modifier.fillMaxSize().background(Color(0xFF2A2F2C)).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Page ${index + 1}", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            PillButton("Undo", Color.White.copy(alpha = 0.15f), Color.White, enabled = marks.isNotEmpty()) { marks.removeAt(marks.lastIndex) }
            Spacer(Modifier.width(8.dp))
            PillButton("Done", Coral, Color.White) { onDone() }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
            val ratio = doc.ratios[index]
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }.toInt()
            LaunchedEffect(index) { bmp = runCatching { doc.render(index, (widthPx * 1.5f).toInt()) }.getOrNull() }
            val fitWidth = maxWidth / maxHeight < ratio
            Box(
                (if (fitWidth) Modifier.fillMaxWidth() else Modifier.height(maxHeight)).aspectRatio(ratio).background(Color.White),
            ) {
                bmp?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize()) }
                val ink = when (pen) {
                    Pen.HIGHLIGHT -> PdfMark.Ink(emptyList(), HighlightColor.toArgb(), 0.028f, 0.4f)
                    else -> PdfMark.Ink(emptyList(), color.toArgb(), 0.005f, 1f)
                }
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(pen) {
                            if (pen == Pen.TEXT) {
                                detectTapGestures { p -> noteAt = Offset(p.x / size.width, p.y / size.height); noteText = "" }
                            } else {
                                detectDragGestures(
                                    onDragStart = { live.clear(); live += it },
                                    onDrag = { change, _ -> live += change.position },
                                    onDragEnd = {
                                        marks += ink.copy(points = live.map { (it.x / size.width).coerceIn(0f, 1f) to (it.y / size.height).coerceIn(0f, 1f) })
                                        live.clear()
                                    },
                                    onDragCancel = { live.clear() },
                                )
                            }
                        },
                ) { drawMarks(marks, live, ink) }
            }
        }
        // Colours sit on their own row so all of them fit next to the three tools on narrow phones.
        Column(
            Modifier.fillMaxWidth().background(Color(0xFF1E2220)).navigationBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (pen != Pen.HIGHLIGHT) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                InkColors.forEach { c ->
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(c)
                            .border(if (c == color) 3.dp else 1.dp, if (c == color) Color.White else Color.White.copy(alpha = 0.3f), CircleShape)
                            .clickable { color = c },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Pen.PEN to "Pen", Pen.HIGHLIGHT to "Highlight", Pen.TEXT to "Text").forEach { (p, label) ->
                    PillButton(label, if (pen == p) Coral else Color.White.copy(alpha = 0.12f), Color.White, Modifier.weight(1f)) { pen = p }
                }
            }
        }
    }

    noteAt?.let { at ->
        AlertDialog(
            onDismissRequest = { noteAt = null },
            title = { Text("Add text") },
            text = { OutlinedTextField(noteText, { noteText = it }, singleLine = true, label = { Text("Text") }) },
            confirmButton = {
                TextButton(onClick = {
                    if (noteText.isNotBlank()) marks += PdfMark.Note(at.x, at.y, noteText.trim(), color.toArgb(), 0.03f)
                    noteAt = null
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { noteAt = null }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------- shared bits for the document tools

@Composable
internal fun BigAction(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier.bouncy(onClick = onClick).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Charcoal).padding(22.dp),
    ) {
        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
    }
}

@Composable
internal fun ToolRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.bouncy(onClick = onClick).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardHi).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = TextSecondary, fontSize = 13.sp)
        }
        Text("›", color = TextSecondary, fontSize = 26.sp)
    }
}

@Composable
internal fun PillButton(label: String, bg: Color, fg: Color, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.bouncy(enabled = enabled, onClick = onClick).clip(RoundedCornerShape(50))
            .background(if (enabled) bg else bg.copy(alpha = 0.4f)).padding(horizontal = 14.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) fg else fg.copy(alpha = 0.5f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun SmallButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.padding(start = 6.dp).size(34.dp).clip(CircleShape).background(Card).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) TextPrimary else TextSecondary.copy(alpha = 0.4f), fontSize = 15.sp)
    }
}

@Composable
internal fun BusyDialog(message: String) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(26.dp), color = Coral, strokeWidth = 3.dp)
                Spacer(Modifier.width(16.dp))
                Text(message)
            }
        },
    )
}
