package com.cleanai.debloat.ui

import com.cleanai.debloat.data.FileNames
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanai.debloat.data.Docx
import com.cleanai.debloat.data.PdfEdit
import com.cleanai.debloat.data.RecentDocs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

/** One paragraph being edited. Its runs keep their mixed formatting until the text is changed. */
private class Block(val id: Long, para: Docx.Para) {
    var para by mutableStateOf(para)
    var value by mutableStateOf(TextFieldValue(styled(para)))
}

private var nextId = 0L

private fun styled(p: Docx.Para): AnnotatedString = buildAnnotatedString {
    p.runs.forEach { r ->
        val start = length
        append(r.text)
        addStyle(
            SpanStyle(
                fontWeight = if (r.bold) FontWeight.Bold else null,
                fontStyle = if (r.italic) FontStyle.Italic else null,
                textDecoration = if (r.underline) TextDecoration.Underline else null,
            ),
            start, length,
        )
    }
}

/** Word documents: create, open, edit and save .docx files, all on the phone. */
@Composable
fun WordScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val blocks = remember { mutableStateListOf<Block>() }
    var editing by remember { mutableStateOf(false) }
    var uri by remember { mutableStateOf<Uri?>(null) }
    var name by remember { mutableStateOf("Document.docx") }
    var dirty by remember { mutableStateOf(false) }
    // The opened file, so saving keeps its tables, pictures and everything else that wasn't edited.
    var source by remember { mutableStateOf<Docx.Source?>(null) }
    var focused by remember { mutableStateOf(0) }
    var leaving by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf<NameAsk?>(null) }
    val focus = remember { mutableMapOf<Long, FocusRequester>() }
    var focusNext by remember { mutableStateOf<Long?>(null) }

    // Empty paragraphs left at the end of the editor aren't worth saving.
    fun current() = Docx.Document(
        blocks.map { it.para }.dropLastWhile { it.text.isBlank() && it.origin == null && it.locked == null }.ifEmpty { listOf(blocks.first().para) },
        source,
    )

    fun load(paras: List<Docx.Para>) {
        blocks.clear()
        paras.forEach { blocks += Block(nextId++, it) }
        focused = 0
        dirty = false
        editing = true
    }

    // The whole file is made first; only then is the old one replaced, so a failed save never leaves it half-written.
    fun write(target: Uri): Boolean = runCatching {
        val bytes = ByteArrayOutputStream().also { Docx.write(current(), it) }.toByteArray()
        (ctx.contentResolver.openOutputStream(target, "wt") ?: error("no stream")).use { it.write(bytes) }
    }.isSuccess

    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(DOCX)) { out ->
        if (out == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) { write(out) }
            if (ok) { uri = out; name = PdfEdit.displayName(ctx, out); dirty = false; RecentDocs.add(ctx, RecentDocs.Kind.WORD, out, name) }
            Toast.makeText(ctx, if (ok) "Saved $name" else "⚠️ Couldn't save there. Try another folder.", Toast.LENGTH_SHORT).show()
        }
    }

    /** Renames the open file where it is; a file not saved yet just takes the new name. */
    fun renameTo(file: String) {
        val u = uri
        if (u == null) { name = file; return }
        scope.launch {
            val renamed = withContext(Dispatchers.IO) { FileNames.renameDocument(ctx, u, file) }
            if (renamed == null) {
                Toast.makeText(ctx, "This folder doesn't allow renaming. Use Save as a new file instead.", Toast.LENGTH_LONG).show()
                return@launch
            }
            RecentDocs.remove(ctx, RecentDocs.Kind.WORD, u)
            uri = renamed; name = PdfEdit.displayName(ctx, renamed)
            RecentDocs.add(ctx, RecentDocs.Kind.WORD, renamed, name)
            Toast.makeText(ctx, "Renamed to $name", Toast.LENGTH_SHORT).show()
        }
    }

    fun save() {
        val target = uri
        if (target == null) { naming = NameAsk(NameFor.NEW_FILE, ".docx", name); return }
        scope.launch {
            val ok = withContext(Dispatchers.IO) { write(target) }
            if (ok) { dirty = false; Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show() }
            // Some apps only let us read the file; save a copy instead.
            else saveAs.launch(name)
        }
    }

    fun openUri(picked: Uri) {
        scope.launch {
            val doc = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(picked)!!.use { Docx.read(it) } }
            }
            doc.onSuccess {
                uri = picked
                name = PdfEdit.displayName(ctx, picked)
                source = it.source
                RecentDocs.add(ctx, RecentDocs.Kind.WORD, picked, name)
                load(it.paras)
            }.onFailure {
                // A file that has moved or been deleted drops off the recent list.
                RecentDocs.remove(ctx, RecentDocs.Kind.WORD, picked)
                Toast.makeText(ctx, "Couldn't open this file. Only Word .docx files are supported (not old .doc).", Toast.LENGTH_LONG).show()
            }
        }
    }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked -> picked?.let(::openUri) }
    // A .docx opened from another app. If this one has unsaved changes, it asks first.
    DocLaunch.Receiver(DocLaunch.Kind.WORD, dirty, name, ::openUri)

    fun newDocument() {
        uri = null; name = "Document.docx"; source = null
        load(listOf(Docx.Para(listOf(Docx.Run("")), Docx.Style.TITLE), Docx.Para(listOf(Docx.Run("")))))
    }
    fun openDocument() = open.launch(arrayOf(DOCX, "application/octet-stream"))

    val recents = remember(editing) { RecentDocs.list(ctx, RecentDocs.Kind.WORD) }
    if (!editing) {
        Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
            ScreenTopBar("Word Editor", onBack)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BigAction("New document", "Start writing a Word file") { newDocument() }
                ToolRow("Open a Word file", "Edit a .docx from your phone, Drive or Downloads") { openDocument() }
                RecentDocsList("Recent documents", recents) { openUri(it.uri) }
                Text(
                    "Edit headings, lists, alignment and bold / italic / underline. Saved files open in Microsoft Word, Google Docs and LibreOffice. " +
                        "In a file you open, tables, pictures, links, headers and footers are kept exactly as they are; they're shown in grey and can't be changed here.",
                    color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        return
    }

    BackHandler { if (dirty) leaving = true else editing = false }
    val block = blocks.getOrNull(focused)?.takeIf { it.para.locked == null }
    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        ScreenTopBar(
            name.removeSuffix(".docx"),
            onBack = { if (dirty) leaving = true else editing = false },
            subtitle = (if (dirty) "Not saved · " else "") + "${words(blocks)} words",
            action = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PillButton("Save", Accent, OnAccent) { save() }
                    Box {
                        Text("⋮", color = TextPrimary, fontSize = 22.sp, modifier = Modifier.clickable { menu = true }.padding(horizontal = 10.dp, vertical = 4.dp))
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; naming = NameAsk(NameFor.RENAME, ".docx", name) })
                            DropdownMenuItem(text = { Text("Save as a new file") }, onClick = { menu = false; naming = NameAsk(NameFor.COPY, ".docx", name) })
                            uri?.let { u ->
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    onClick = { menu = false; if (dirty) Toast.makeText(ctx, "Save first to share your latest changes.", Toast.LENGTH_SHORT).show() else shareFile(ctx, u, DOCX) },
                                )
                            }
                        }
                    }
                }
            },
        )
        if (blocks.any { !it.para.locked.isNullOrEmpty() }) {
            Text(
                "Tables, pictures and links in this file are kept exactly as they are. They're shown in grey and can't be changed here.",
                color = TextPrimary, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Butter.copy(alpha = 0.5f)).padding(10.dp),
            )
        }
        val list = rememberLazyListState()
        LazyColumn(
            state = list,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp, vertical = 24.dp),
        ) {
            itemsIndexed(blocks, key = { _, b -> b.id }) { i, b ->
                b.para.locked?.let { label -> KeptBlock(label, b.para.text); return@itemsIndexed }
                val requester = focus.getOrPut(b.id) { FocusRequester() }
                LaunchedEffect(focusNext) {
                    if (focusNext == b.id) { runCatching { requester.requestFocus() }; focusNext = null }
                }
                ParagraphEditor(
                    b,
                    number = if (b.para.style == Docx.Style.NUMBERED) blocks.subList(0, i + 1).takeLastWhile { it.para.style == Docx.Style.NUMBERED }.size else 0,
                    modifier = Modifier.focusRequester(requester).onFocusChanged { if (it.isFocused) focused = i },
                    onChange = { v ->
                        val nl = v.text.indexOf('\n')
                        if (nl >= 0) {
                            // Enter: split the paragraph; lists carry on, headings go back to normal text.
                            val before = v.text.substring(0, nl)
                            val after = v.text.substring(nl + 1).replace("\n", " ")
                            b.para = b.para.withText(before)
                            b.value = TextFieldValue(styled(b.para), TextRange(before.length))
                            val nextStyle = if (b.para.style == Docx.Style.BULLET || b.para.style == Docx.Style.NUMBERED) b.para.style else Docx.Style.BODY
                            // An empty list item + Enter ends the list.
                            if (before.isEmpty() && nextStyle != Docx.Style.BODY) {
                                b.para = b.para.copy(style = Docx.Style.BODY)
                                dirty = true
                                return@ParagraphEditor
                            }
                            // Enter on the line above the empty last paragraph (e.g. the new-document title) just moves into it.
                            val next = blocks.getOrNull(i + 1)
                            if (after.isEmpty() && next != null && i + 1 == blocks.lastIndex && next.para.text.isEmpty()) {
                                focusNext = next.id
                                return@ParagraphEditor
                            }
                            val nb = Block(nextId++, Docx.Para(listOf(Docx.Run(after, b.para.bold, b.para.italic, b.para.underline)), nextStyle, b.para.align))
                            nb.value = TextFieldValue(styled(nb.para), TextRange(0))
                            blocks.add(i + 1, nb)
                            focusNext = nb.id
                        } else {
                            b.value = v
                            b.para = b.para.withText(v.text)
                        }
                        dirty = true
                    },
                    onBackspaceAtStart = {
                        if (i > 0 && blocks[i - 1].para.locked == null) {
                            // Backspace at the start joins this paragraph onto the one before.
                            val prev = blocks[i - 1]
                            val join = prev.para.text.length
                            prev.para = prev.para.withText(prev.para.text + b.para.text)
                            prev.value = TextFieldValue(styled(prev.para), TextRange(join))
                            blocks.removeAt(i)
                            focusNext = prev.id
                            dirty = true
                        } else if (b.para.style != Docx.Style.BODY) {
                            b.para = b.para.copy(style = Docx.Style.BODY)
                            dirty = true
                        }
                    },
                )
            }
        }
        // Formatting bar for the paragraph you're typing in; sits above the keyboard.
        if (block != null) {
            Column(Modifier.fillMaxWidth().background(CardHi).imePadding().navigationBarsPadding().padding(vertical = 8.dp)) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Docx.Style.entries.forEach { s ->
                        FormatChip(s.label, block.para.style == s) {
                            block.para = block.para.copy(style = s); dirty = true; focusNext = block.id
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FormatChip("B", block.para.bold, bold = true) {
                        block.para = block.para.withBold(!block.para.bold); block.value = block.value.copy(annotatedString = styled(block.para)); dirty = true; focusNext = block.id
                    }
                    FormatChip("I", block.para.italic, italic = true) {
                        block.para = block.para.withItalic(!block.para.italic); block.value = block.value.copy(annotatedString = styled(block.para)); dirty = true; focusNext = block.id
                    }
                    FormatChip("U", block.para.underline, underline = true) {
                        block.para = block.para.withUnderline(!block.para.underline); block.value = block.value.copy(annotatedString = styled(block.para)); dirty = true; focusNext = block.id
                    }
                    Spacer(Modifier.width(8.dp))
                    listOf(Docx.Align.LEFT to "Left", Docx.Align.CENTER to "Center", Docx.Align.RIGHT to "Right", Docx.Align.JUSTIFY to "Justify").forEach { (a, label) ->
                        FormatChip(label, block.para.align == a) { block.para = block.para.copy(align = a); dirty = true; focusNext = block.id }
                    }
                }
            }
        }
    }

    naming?.let { ask ->
        NameDialog(ask.why.title, ask.initial, ask.ext, confirm = ask.why.confirm, onDismiss = { naming = null }, onDone = { n ->
            naming = null
            if (ask.why == NameFor.RENAME) renameTo(n + ask.ext) else saveAs.launch(n + ask.ext)
        })
    }
    if (leaving) {
        AlertDialog(
            onDismissRequest = { leaving = false },
            title = { Text("Save changes?") },
            text = { Text("Your changes to $name aren't saved yet.") },
            confirmButton = { TextButton(onClick = { leaving = false; save() }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { leaving = false; dirty = false; editing = false }) { Text("Discard") } },
        )
    }
}

private fun words(blocks: List<Block>) = blocks.sumOf { b -> b.para.text.split(Regex("\\s+")).count { it.isNotBlank() } }

@Composable
private fun ParagraphEditor(
    b: Block,
    number: Int,
    modifier: Modifier,
    onChange: (TextFieldValue) -> Unit,
    onBackspaceAtStart: () -> Unit,
) {
    val p = b.para
    val (size, weight, color) = when (p.style) {
        Docx.Style.TITLE -> Triple(30.sp, FontWeight.Normal, Color(0xFF1B1B1B))
        Docx.Style.H1 -> Triple(23.sp, FontWeight.Bold, Color(0xFF2F5496))
        Docx.Style.H2 -> Triple(19.sp, FontWeight.Bold, Color(0xFF2F5496))
        else -> Triple(16.sp, FontWeight.Normal, Color(0xFF1B1B1B))
    }
    val style = TextStyle(
        fontSize = size,
        lineHeight = size * 1.4f,
        fontWeight = if (p.bold) FontWeight.Bold else weight,
        fontStyle = if (p.italic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (p.underline) TextDecoration.Underline else null,
        color = color,
        textAlign = when (p.align) {
            Docx.Align.CENTER -> TextAlign.Center
            Docx.Align.RIGHT -> TextAlign.End
            Docx.Align.JUSTIFY -> TextAlign.Justify
            Docx.Align.LEFT -> TextAlign.Start
        },
    )
    val top = when (p.style) {
        Docx.Style.H1 -> 14.dp
        Docx.Style.H2 -> 8.dp
        else -> 2.dp
    }
    Row(Modifier.fillMaxWidth().padding(top = top, bottom = 6.dp)) {
        when (p.style) {
            Docx.Style.BULLET -> Text("•", style = style, modifier = Modifier.widthIn(min = 22.dp))
            Docx.Style.NUMBERED -> Text("$number.", style = style, modifier = Modifier.widthIn(min = 28.dp))
            else -> {}
        }
        Box(Modifier.weight(1f)) {
            if (b.value.text.isEmpty()) {
                Text(
                    when (p.style) {
                        Docx.Style.TITLE -> "Title"
                        Docx.Style.H1, Docx.Style.H2 -> "Heading"
                        else -> "Start typing…"
                    },
                    style = style.copy(color = Color(0xFFB0B0B0)),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            BasicTextField(
                b.value,
                onChange,
                textStyle = style,
                cursorBrush = SolidColor(Accent),
                modifier = modifier.fillMaxWidth().onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.Backspace && b.value.selection == TextRange(0)) {
                        onBackspaceAtStart(); true
                    } else false
                },
            )
        }
    }
}

@Composable
private fun FormatChip(label: String, on: Boolean, bold: Boolean = false, italic: Boolean = false, underline: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) Charcoal else Card).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (on) Color.White else TextPrimary,
            fontSize = 15.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = if (underline) TextDecoration.Underline else null,
        )
    }
}

/** Shares a document the user saved, for the Office screens. */
internal fun shareFile(ctx: Context, uri: Uri, mime: String) {
    val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri("", uri)
    runCatching { ctx.startActivity(Intent.createChooser(send, "Share")) }
}

/** A table, picture or other part of an opened file that is kept as it is; "" marks are invisible. */
@Composable
private fun KeptBlock(label: String, text: String) {
    if (label.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF0F1F3)).padding(10.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text("$label · kept as is", color = Color(0xFF6B7078), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        if (text.isNotBlank()) Text(text.take(KEPT_PREVIEW_CHARS), color = Color(0xFF3A3F47), fontSize = 14.sp, lineHeight = 20.sp)
    }
}

private const val KEPT_PREVIEW_CHARS = 600
