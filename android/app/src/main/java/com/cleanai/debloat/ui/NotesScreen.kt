package com.cleanai.debloat.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanai.debloat.data.CheckItem
import com.cleanai.debloat.data.Note
import com.cleanai.debloat.data.Notes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Note colours; 0 is the plain card colour of the current theme. */
private val NoteColors = listOf(
    Color.Unspecified, Color(0xFFF6E7A1), Color(0xFFCFE8C2), Color(0xFFC6E2F2), Color(0xFFF6C9BF), Color(0xFFDCD0F2), Color(0xFFE6DFD2),
)

/**
 * A note's colour is either a small number (0 = plain, 1..6 = the quick pastels above) or a full ARGB colour
 * picked from the chart; ARGB colours are opaque, so they never clash with the small numbers.
 */
private fun isCustom(c: Int) = c !in NoteColors.indices
private fun noteBg(c: Int) = when {
    c == 0 -> CardHi
    isCustom(c) -> Color(c)
    else -> NoteColors[c]
}
/** Dark text on light colours, white text on dark ones. */
private fun darkBg(c: Int) = isCustom(c) && Color(c).luminance() < 0.45f
private fun noteFg(c: Int) = when { c == 0 -> TextPrimary; darkBg(c) -> Color.White; else -> Ink }
private fun noteFg2(c: Int) = when { c == 0 -> TextSecondary; darkBg(c) -> Color.White.copy(alpha = 0.7f); else -> Ink.copy(alpha = 0.62f) }

/** The colour chart: 12 hues across, from pale to deep down the rows, then a row of greys. */
private val ChartHues = (0 until 12).map { it * 30f }
private val ChartTones = listOf(0.12f to 1f, 0.25f to 1f, 0.45f to 1f, 0.7f to 0.95f, 0.85f to 0.78f, 0.85f to 0.52f)
private val ChartRows: List<List<Int>> =
    ChartTones.map { (s, v) -> ChartHues.map { h -> android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)) } } +
        listOf((0 until 12).map { i -> android.graphics.Color.HSVToColor(floatArrayOf(0f, 0f, 1f - i / 11f)) })

private const val TRASH = "\u0000trash"
private const val LISTS = "\u0000lists"

/**
 * Notes: text notes and checklists with colours, pins and tags, search, a trash you can restore from,
 * notes locked behind the phone's fingerprint or screen lock, and export to Markdown.
 * Everything is stored privately on the phone.
 */
@Composable
fun NotesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val notes = remember { mutableStateListOf<Note>() }
    var loaded by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var openId by remember { mutableStateOf<Long?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var trashPick by remember { mutableStateOf<Note?>(null) }
    val unlock = rememberUnlock()

    LaunchedEffect(Unit) {
        notes.clear(); notes.addAll(Notes.load(ctx)); loaded = true
    }
    // Save shortly after each change, and once more on the way out.
    LaunchedEffect(tick) {
        if (tick > 0) { delay(400); Notes.saveLater(ctx, notes.toList()) }
    }
    DisposableEffect(Unit) {
        onDispose { if (loaded) Notes.saveLater(ctx, notes.toList()) }
    }

    fun put(n: Note, touch: Boolean = true) {
        val i = notes.indexOfFirst { it.id == n.id }
        val next = if (touch) n.copy(updated = System.currentTimeMillis()) else n
        if (i >= 0) notes[i] = next else notes.add(0, next)
        tick++
    }
    fun remove(id: Long) { notes.removeAll { it.id == id }; tick++ }

    fun open(n: Note) {
        if (n.locked) unlock("Open locked note") { openId = n.id } else openId = n.id
    }
    fun newNote(list: Boolean) {
        val n = Note(id = System.currentTimeMillis(), items = if (list) listOf(CheckItem("")) else null, tags = listOfNotNull(filter?.takeIf { !it.startsWith("\u0000") }))
        notes.add(0, n); openId = n.id; tick++
    }

    val exportAll = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val shown = notes.filter { it.trashed == 0L && !it.locked && !it.isEmpty }
        val skipped = notes.count { it.trashed == 0L && it.locked }
        scope.launch {
            val ok = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { w -> w.write(shown.joinToString("\n---\n\n") { it.toMarkdown() }) }
                }
            }
            Toast.makeText(
                ctx,
                if (ok.isFailure) "⚠️ Couldn't export: ${ok.exceptionOrNull()?.message}"
                else "Exported ${shown.size} notes." + if (skipped > 0) " Locked notes ($skipped) were left out." else "",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    // ---- one note open
    val opened = openId?.let { id -> notes.firstOrNull { it.id == id } }
    if (opened != null) {
        NoteEditor(
            note = opened,
            allTags = notes.flatMap { it.tags }.distinct().sorted(),
            onChange = { put(it) },
            onLockChange = { lock ->
                if (lock && !deviceSecure(ctx)) {
                    Toast.makeText(ctx, "Set a screen lock (PIN, pattern or fingerprint) in your phone's Settings first.", Toast.LENGTH_LONG).show()
                } else put(opened.copy(locked = lock), touch = false)
            },
            onTrash = {
                put(opened.copy(trashed = System.currentTimeMillis(), pinned = false), touch = false)
                openId = null
                Toast.makeText(ctx, "Moved to the trash.", Toast.LENGTH_SHORT).show()
            },
            onClose = {
                if (opened.isEmpty) remove(opened.id)
                openId = null
            },
        )
        return
    }

    BackHandler(enabled = filter != null || query.isNotEmpty()) { filter = null; query = "" }
    val live = notes.filter { it.trashed == 0L }
    val trash = notes.filter { it.trashed != 0L }.sortedByDescending { it.trashed }
    val tags = live.flatMap { it.tags }.distinct().sorted()
    if (filter == TRASH && trash.isEmpty()) filter = null
    if (filter != null && !filter!!.startsWith("\u0000") && filter !in tags) filter = null

    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        ScreenTopBar(
            if (filter == TRASH) "Trash" else "Notes",
            onBack = { if (filter != null) filter = null else onBack() },
            subtitle = if (!loaded) null else if (filter == TRASH) "${trash.size} in the trash" else if (live.size == 1) "1 note" else "${live.size} notes",
            action = {
                Box {
                    Box(
                        Modifier.bouncy { menu = true }.size(44.dp).clip(CircleShape).background(CardHi).border(1.dp, Line, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("⋮", color = TextPrimary, fontSize = 20.sp) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Export all as Markdown…") }, onClick = { menu = false; exportAll.launch("Notes.md") })
                        DropdownMenuItem(text = { Text("Trash (${trash.size})") }, enabled = trash.isNotEmpty(), onClick = { menu = false; filter = TRASH })
                    }
                }
            },
        )

        if (filter != TRASH) {
            // Search
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(50)).background(CardHi)
                    .border(1.dp, Line, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⌕", color = TextSecondary, fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search notes", color = TextSecondary, fontSize = 15.sp)
                    BasicTextField(
                        query, { query = it }, singleLine = true, cursorBrush = SolidColor(Accent),
                        textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp), modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) Text("✕", color = TextSecondary, modifier = Modifier.clickable { query = "" }.padding(start = 8.dp))
            }
            // Filters
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip("All", filter == null) { filter = null }
                if (live.any { it.isList }) FilterChip("☑ Checklists", filter == LISTS) { filter = LISTS }
                tags.forEach { t -> FilterChip("# $t", filter == t) { filter = if (filter == t) null else t } }
                if (trash.isNotEmpty()) FilterChip("🗑 Trash", false) { filter = TRASH }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (filter == TRASH) {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalItemSpacing = 10.dp,
                ) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Notes here are deleted for good after 30 days. Tap one to restore it.", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { notes.removeAll { it.trashed != 0L }; tick++ }) { Text("Empty trash", color = Accent) }
                        }
                    }
                    items(trash, key = { it.id }) { n -> NoteCard(n) { trashPick = n } }
                }
            } else {
                val shown = live
                    .filter { n -> when (filter) { null -> true; LISTS -> n.isList; else -> filter in n.tags } }
                    .filter { it.matches(query) }
                    .sortedByDescending { it.updated }
                val pinned = shown.filter { it.pinned }
                val others = shown.filter { !it.pinned }
                if (loaded && shown.isEmpty()) {
                    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (query.isNotEmpty()) "No notes match “$query”." else "No notes yet", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        if (query.isEmpty()) Text(
                            "Write a note or a checklist below. Pin the important ones, add tags, or lock private notes with your fingerprint.",
                            color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                } else LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalItemSpacing = 10.dp,
                ) {
                    if (pinned.isNotEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) { SectionLabel("📌 Pinned") }
                        items(pinned, key = { it.id }) { n -> NoteCard(n) { open(n) } }
                        if (others.isNotEmpty()) item(span = StaggeredGridItemSpan.FullLine) { SectionLabel("Others") }
                    }
                    items(others, key = { it.id }) { n -> NoteCard(n) { open(n) } }
                }
            }
        }

        if (filter != TRASH) Row(
            Modifier.fillMaxWidth().background(Bg).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PillButton("＋  Note", Charcoal, Color.White, Modifier.weight(1f), enabled = loaded) { newNote(list = false) }
            PillButton("☑  Checklist", Card, TextPrimary, Modifier.weight(1f), enabled = loaded) { newNote(list = true) }
        }
    }

    trashPick?.let { n ->
        AlertDialog(
            onDismissRequest = { trashPick = null },
            title = { Text(n.title.ifBlank { "Untitled note" }) },
            text = { Text("Restore this note, or delete it for good? Deleting can't be undone.") },
            confirmButton = { TextButton(onClick = { put(n.copy(trashed = 0), touch = false); trashPick = null }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { remove(n.id); trashPick = null }) { Text("Delete forever", color = Accent) } },
        )
    }
}

// ------------------------------------------------------------------------------------------- list parts

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
}

@Composable
private fun FilterChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (on) Charcoal else CardHi).border(1.dp, if (on) Charcoal else Line, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(label, color = if (on) Color.White else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
}

@Composable
private fun NoteCard(n: Note, onClick: () -> Unit) {
    val fg = noteFg(n.color)
    val fg2 = noteFg2(n.color)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(noteBg(n.color))
            .then(if (n.color == 0) Modifier.border(1.dp, Line, RoundedCornerShape(20.dp)) else Modifier)
            .bouncy(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (n.title.isNotBlank()) Text(n.title, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        when {
            n.locked -> Text("🔒 Locked", color = fg2, fontSize = 14.sp)
            n.items != null -> {
                n.items.take(6).forEach { item ->
                    Text(
                        (if (item.done) "☑ " else "☐ ") + item.text, color = if (item.done) fg2 else fg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    )
                }
                if (n.items.size > 6) Text("+ ${n.items.size - 6} more", color = fg2, fontSize = 13.sp)
            }
            n.body.isNotBlank() -> Text(n.body.trim(), color = fg, fontSize = 14.sp, maxLines = 9, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
            n.title.isBlank() -> Text("Empty note", color = fg2, fontSize = 14.sp)
        }
        if (n.tags.isNotEmpty()) Text(n.tags.joinToString("  ") { "#$it" }, color = fg2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ------------------------------------------------------------------------------------------- editor

@Composable
private fun NoteEditor(
    note: Note,
    allTags: List<String>,
    onChange: (Note) -> Unit,
    onLockChange: (Boolean) -> Unit,
    onTrash: () -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var colors by remember { mutableStateOf(false) }
    var tagging by remember { mutableStateOf(false) }
    var focusItem by remember { mutableStateOf<Int?>(null) }
    val requesters = remember { mutableMapOf<Int, FocusRequester>() }
    val fg = noteFg(note.color)
    val fg2 = noteFg2(note.color)
    BackHandler(onBack = onClose)

    // Keep locked notes out of screenshots and the recent-apps preview while they are open.
    val activity = ctx as? Activity
    DisposableEffect(note.locked) {
        if (note.locked) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching { ctx.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(note.toMarkdown()) } }
        Toast.makeText(ctx, if (ok.isSuccess) "Saved." else "⚠️ Couldn't save: ${ok.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.fillMaxSize().background(noteBg(note.color).takeIf { note.color != 0 } ?: Bg).systemBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            EditorButton("←", fg) { onClose() }
            Spacer(Modifier.weight(1f))
            EditorButton(if (note.pinned) "📌" else "📍", fg, dim = !note.pinned) { onChange(note.copy(pinned = !note.pinned)) }
            EditorButton(if (note.locked) "🔒" else "🔓", fg, dim = !note.locked) { onLockChange(!note.locked) }
            Box {
                EditorButton("⋮", fg) { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Share") }, onClick = {
                        menu = false
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, note.toMarkdown())
                            .putExtra(Intent.EXTRA_SUBJECT, note.title)
                        runCatching { ctx.startActivity(Intent.createChooser(send, "Share note")) }
                    })
                    DropdownMenuItem(text = { Text("Save as a file…") }, onClick = {
                        menu = false
                        saveFile.launch(note.title.ifBlank { "Note" }.replace(Regex("[\\\\/:*?\"<>|]"), " ").take(60) + ".md")
                    })
                    DropdownMenuItem(text = { Text("Move to trash", color = Accent) }, onClick = { menu = false; onTrash() })
                }
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            Box {
                if (note.title.isEmpty()) Text("Title", color = fg2, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                BasicTextField(
                    note.title, { onChange(note.copy(title = it.replace("\n", " "))) },
                    textStyle = TextStyle(color = fg, fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
                    cursorBrush = SolidColor(Accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(note.editedText(), color = fg2, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))

            val items = note.items
            if (items == null) {
                val bodyFocus = remember { FocusRequester() }
                LaunchedEffect(note.id) { if (note.isEmpty) runCatching { bodyFocus.requestFocus() } }
                Box(Modifier.fillMaxWidth()) {
                    if (note.body.isEmpty()) Text("Write something…", color = fg2, fontSize = 17.sp)
                    BasicTextField(
                        note.body, { onChange(note.copy(body = it)) },
                        textStyle = TextStyle(color = fg, fontSize = 17.sp, lineHeight = 25.sp),
                        cursorBrush = SolidColor(Accent),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 200.dp).focusRequester(bodyFocus),
                    )
                }
            } else {
                fun setItems(list: List<CheckItem>) = onChange(note.copy(items = list))
                items.forEachIndexed { i, item ->
                    val fr = requesters.getOrPut(i) { FocusRequester() }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Box(
                            Modifier.size(26.dp).clip(RoundedCornerShape(7.dp))
                                .background(if (item.done) Accent else Color.Transparent)
                                .border(2.dp, if (item.done) Accent else fg2, RoundedCornerShape(7.dp))
                                .clickable {
                                    // Ticked items sink to the bottom; unticked ones rise back above them.
                                    val rest = items.toMutableList().apply { removeAt(i) }
                                    val toggled = item.copy(done = !item.done)
                                    val at = if (toggled.done) rest.size else rest.indexOfFirst { it.done }.let { if (it < 0) rest.size else it }
                                    setItems(rest.apply { add(at, toggled) })
                                },
                            contentAlignment = Alignment.Center,
                        ) { if (item.done) Text("✓", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            if (item.text.isEmpty()) Text("List item", color = fg2, fontSize = 17.sp)
                            BasicTextField(
                                item.text,
                                { v ->
                                    if ('\n' in v) {
                                        // Enter starts a new item below.
                                        val cut = v.indexOf('\n')
                                        val list = items.toMutableList()
                                        list[i] = item.copy(text = v.substring(0, cut))
                                        list.add(i + 1, CheckItem(v.substring(cut + 1).replace("\n", "")))
                                        setItems(list)
                                        focusItem = i + 1
                                    } else setItems(items.toMutableList().apply { this[i] = item.copy(text = v) })
                                },
                                textStyle = TextStyle(
                                    color = if (item.done) fg2 else fg, fontSize = 17.sp,
                                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                                ),
                                cursorBrush = SolidColor(Accent),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                modifier = Modifier.fillMaxWidth().focusRequester(fr),
                            )
                        }
                        Text(
                            "✕", color = fg2, fontSize = 16.sp,
                            modifier = Modifier.clip(CircleShape).clickable {
                                setItems(items.toMutableList().apply { removeAt(i) })
                                if (i > 0) focusItem = i - 1
                            }.padding(10.dp),
                        )
                    }
                }
                Text(
                    "＋  Add item", color = fg2, fontSize = 16.sp,
                    modifier = Modifier.padding(vertical = 10.dp).clip(RoundedCornerShape(50)).clickable {
                        val firstDone = items.indexOfFirst { it.done }.let { if (it < 0) items.size else it }
                        setItems(items.toMutableList().apply { add(firstDone, CheckItem("")) })
                        focusItem = firstDone
                    }.padding(horizontal = 6.dp, vertical = 6.dp),
                )
                LaunchedEffect(focusItem, items.size) {
                    focusItem?.let { idx -> runCatching { requesters[idx]?.requestFocus() }; focusItem = null }
                }
                LaunchedEffect(note.id) { if (note.isEmpty) runCatching { requesters[0]?.requestFocus() } }
                if (items.any { it.done }) Text(
                    "Clear ticked items", color = Accent, fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 200.dp).clip(RoundedCornerShape(50)).clickable { setItems(items.filter { !it.done }) }.padding(6.dp),
                ) else Spacer(Modifier.height(200.dp))
            }
        }

        // Bottom bar: colour, tags, text <-> checklist
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            BarChip("🎨 Colour", fg) { colors = true }
            BarChip(if (note.tags.isEmpty()) "# Tags" else note.tags.joinToString(" ") { "#$it" }, fg) { tagging = true }
            if (note.items == null) BarChip("☑ Make checklist", fg) {
                val lines = note.body.lines().map { it.trim() }.filter { it.isNotEmpty() }
                onChange(note.copy(items = lines.map { l ->
                    val done = l.startsWith("✓ ") || l.startsWith("- [x] ", true)
                    CheckItem(l.removePrefix("✓ ").removePrefix("- [x] ").removePrefix("- [X] ").removePrefix("- [ ] ").removePrefix("- ").removePrefix("• "), done)
                }.ifEmpty { listOf(CheckItem("")) }, body = ""))
            } else BarChip("¶ Make text", fg) {
                onChange(note.copy(body = note.items.filter { it.text.isNotBlank() }.joinToString("\n") { (if (it.done) "✓ " else "") + it.text }, items = null))
            }
        }
    }

    if (colors) {
        AlertDialog(
            onDismissRequest = { colors = false },
            title = { Text("Note colour") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Preview of the note in the chosen colour.
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(noteBg(note.color))
                            .border(1.dp, Line, RoundedCornerShape(14.dp)).padding(12.dp),
                    ) { Text(note.title.ifBlank { "Your note" }, color = noteFg(note.color), fontWeight = FontWeight.SemiBold, maxLines = 1) }
                    Text("Quick colours", color = TextSecondary, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NoteColors.indices.forEach { i ->
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).background(noteBg(i))
                                    .border(if (i == note.color) 3.dp else 1.dp, if (i == note.color) Accent else Line, CircleShape)
                                    .clickable { onChange(note.copy(color = i)) },
                            )
                        }
                    }
                    Text("Any colour", color = TextSecondary, fontSize = 12.sp)
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val cell = (maxWidth - 2.dp * 11) / 12
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            ChartRows.forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    row.forEach { c ->
                                        val on = note.color == c
                                        Box(
                                            Modifier.size(cell).clip(RoundedCornerShape(4.dp)).background(Color(c))
                                                .border(if (on) 2.5.dp else 0.5.dp, if (on) Accent else Line, RoundedCornerShape(4.dp))
                                                .clickable { onChange(note.copy(color = c)) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { colors = false }) { Text("Done") } },
        )
    }

    if (tagging) {
        var fresh by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { tagging = false },
            title = { Text("Tags") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val choices = (allTags + note.tags).distinct().sorted()
                    if (choices.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        choices.forEach { t ->
                            FilterChip("# $t", t in note.tags) { onChange(note.copy(tags = if (t in note.tags) note.tags - t else note.tags + t)) }
                        }
                    }
                    OutlinedTextField(fresh, { fresh = it.replace("\n", "") }, singleLine = true, label = { Text("New tag") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = fresh.trim().removePrefix("#").trim()
                    if (t.isNotEmpty() && t !in note.tags) onChange(note.copy(tags = note.tags + t))
                    tagging = false
                }) { Text(if (fresh.isBlank()) "Done" else "Add") }
            },
        )
    }
}

@Composable
private fun EditorButton(label: String, fg: Color, dim: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (dim) fg.copy(alpha = 0.55f) else fg, fontSize = 20.sp) }
}

@Composable
private fun BarChip(label: String, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).border(1.dp, fg.copy(alpha = 0.25f), RoundedCornerShape(50)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) { Text(label, color = fg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

// ------------------------------------------------------------------------------------------- lock

private fun deviceSecure(ctx: android.content.Context) =
    ctx.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

/**
 * Asks for the fingerprint, face or screen lock before [onOk] runs. Uses the phone's own prompt, so the app
 * never sees the fingerprint or PIN. Without a screen lock on the phone there is nothing to ask for.
 */
@Composable
private fun rememberUnlock(): (String, () -> Unit) -> Unit {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val keyguard = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) pending?.invoke()
        pending = null
    }
    return remember(ctx) {
        { title: String, onOk: () -> Unit ->
            val km = ctx.getSystemService(KeyguardManager::class.java)
            when {
                km == null || !km.isDeviceSecure -> {
                    Toast.makeText(ctx, "Your phone has no screen lock, so locked notes open without one.", Toast.LENGTH_LONG).show()
                    onOk()
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    val builder = BiometricPrompt.Builder(ctx).setTitle(title).setSubtitle("Mobile Handler · Notes")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    } else {
                        @Suppress("DEPRECATION")
                        builder.setDeviceCredentialAllowed(true)
                    }
                    builder.build().authenticate(
                        CancellationSignal(), ctx.mainExecutor,
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onOk()
                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                                if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED && errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED)
                                    Toast.makeText(ctx, errString ?: "Couldn't unlock.", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
                else -> {
                    @Suppress("DEPRECATION")
                    val intent = km.createConfirmDeviceCredentialIntent(title, "Mobile Handler · Notes")
                    if (intent == null) onOk() else { pending = onOk; keyguard.launch(intent) }
                }
            }
        }
    }
}
