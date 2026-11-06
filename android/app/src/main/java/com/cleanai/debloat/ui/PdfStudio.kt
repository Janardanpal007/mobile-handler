package com.cleanai.debloat.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanai.debloat.R
import com.cleanai.debloat.data.MyFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** One round tool button: what it shows and what it does. */
internal class StudioTile(val label: String, @DrawableRes val icon: Int, val tint: Color, val onClick: () -> Unit)

/** Everything a studio button can ask the PDF screen to do. */
internal class StudioActions(
    val openPdf: (EditorStart) -> Unit,
    val pdfTool: (PdfTool) -> Unit,
    val openTool: (String) -> Unit,
    val startTool: (String, ToolStart.Action) -> Unit,
    val openDoc: (MyFiles.Doc) -> Unit,
    val showAllTools: () -> Unit,
)

private enum class StudioTab(val title: String) { PDF("PDF"), WORD("Word"), EXCEL("Excel") }

/** The tab last used, so coming back from Word or Excel shows the same tab. */
private var lastTab = StudioTab.PDF

private const val GRID_COLUMNS = 4
private const val RECENT_SHOWN = 4
private const val THUMB_WIDTH_PX = 160

private fun pdfTiles(a: StudioActions) = listOf(
    StudioTile("Edit", R.drawable.ic_edit, Sky) { a.openPdf(EditorStart.READ) },
    StudioTile("Sign", R.drawable.ic_sign, Butter) { a.openPdf(EditorStart.SIGN) },
    StudioTile("Scan", R.drawable.ic_tool_doc, Mint) { a.openTool("docscan") },
    StudioTile("Photos", R.drawable.ic_tool_pdf, Sky) { a.openTool("pdf") },
    StudioTile("Merge", R.drawable.ic_merge, Mint) { a.pdfTool(PdfTool.MERGE) },
    StudioTile("Split", R.drawable.ic_split, Butter) { a.pdfTool(PdfTool.EXTRACT) },
    StudioTile("Compress", R.drawable.ic_compress, CoralTint) { a.pdfTool(PdfTool.COMPRESS) },
    StudioTile("All tools", R.drawable.ic_grid, Sky) { a.showAllTools() },
)

/** Every studio tool, grouped, for the All tools page. */
private fun allToolGroups(a: StudioActions) = listOf(
    "Edit & sign" to listOf(
        StudioTile("Edit", R.drawable.ic_edit, Sky) { a.openPdf(EditorStart.READ) },
        StudioTile("Sign", R.drawable.ic_sign, Butter) { a.openPdf(EditorStart.SIGN) },
        StudioTile("Stamp", R.drawable.ic_stamp, CoralTint) { a.openPdf(EditorStart.STAMP) },
    ),
    "Organize" to listOf(
        StudioTile("Merge", R.drawable.ic_merge, Mint) { a.pdfTool(PdfTool.MERGE) },
        StudioTile("Split", R.drawable.ic_split, Butter) { a.pdfTool(PdfTool.EXTRACT) },
        StudioTile("Remove pages", R.drawable.ic_pages, CoralTint) { a.pdfTool(PdfTool.REMOVE) },
    ),
    "Protect & share" to listOf(
        StudioTile("Compress", R.drawable.ic_compress, CoralTint) { a.pdfTool(PdfTool.COMPRESS) },
        StudioTile("Watermark", R.drawable.ic_watermark, Sky) { a.pdfTool(PdfTool.WATERMARK) },
        StudioTile("Password", R.drawable.ic_lock, Butter) { a.pdfTool(PdfTool.PASSWORD) },
    ),
    "Create" to listOf(
        StudioTile("Scan", R.drawable.ic_tool_doc, Mint) { a.openTool("docscan") },
        StudioTile("Photos to PDF", R.drawable.ic_tool_pdf, Sky) { a.openTool("pdf") },
        StudioTile("Word", R.drawable.ic_tool_word, Sky) { a.startTool("word", ToolStart.Action.NEW) },
        StudioTile("Excel", R.drawable.ic_tool_sheet, Mint) { a.startTool("excel", ToolStart.Action.NEW) },
    ),
)

/**
 * PDF Studio's start page: PDF / Word / Excel tabs, one "open" card, round tool buttons and recent files.
 * Every button leads to something that already works; nothing here is a placeholder.
 */
@Composable
internal fun StudioHome(mine: List<MyFiles.Doc>, actions: StudioActions) {
    var tab by rememberSaveable { mutableStateOf(lastTab) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Tabs(tab) { tab = it; lastTab = it }
        when (tab) {
            StudioTab.PDF -> {
                OpenCard("Open a PDF", "Read, find text, edit and sign", R.drawable.ic_tool_reader) { actions.openPdf(EditorStart.READ) }
                TileGrid(pdfTiles(actions))
                RecentFiles(mine, actions.openDoc)
            }
            StudioTab.WORD -> {
                OpenCard("Open a Word file", ".docx from your phone, Drive or Downloads", R.drawable.ic_tool_word) { actions.startTool("word", ToolStart.Action.OPEN) }
                TileGrid(listOf(StudioTile("New document", R.drawable.ic_plus, Sky) { actions.startTool("word", ToolStart.Action.NEW) }))
            }
            StudioTab.EXCEL -> {
                OpenCard("Open a spreadsheet", ".xlsx or .csv from your phone, Drive or Downloads", R.drawable.ic_tool_sheet) { actions.startTool("excel", ToolStart.Action.OPEN) }
                TileGrid(listOf(StudioTile("New sheet", R.drawable.ic_plus, Mint) { actions.startTool("excel", ToolStart.Action.NEW) }))
            }
        }
        Note("Everything happens on your phone — your documents are never uploaded.")
    }
}

/** The All tools page: every studio tool, grouped. */
@Composable
internal fun StudioAllTools(actions: StudioActions) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 24.dp)) {
        allToolGroups(actions).forEach { (title, tiles) ->
            Text(title, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
            TileGrid(tiles)
        }
    }
}

@Composable
private fun Tabs(selected: StudioTab, onSelect: (StudioTab) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Card).padding(4.dp)) {
        StudioTab.entries.forEach { t ->
            val on = t == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (on) CardHi else Color.Transparent)
                    .bouncy { onSelect(t) }.semantics { role = Role.Tab; this.selected = on }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(t.title, color = if (on) TextPrimary else TextSecondary, fontSize = 15.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun OpenCard(title: String, subtitle: String, @DrawableRes icon: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardHi).border(1.dp, Line, RoundedCornerShape(20.dp))
            .bouncy(onClick = onClick).semantics(mergeDescendants = true) { role = Role.Button }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Accent), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("›", color = TextSecondary, fontSize = 22.sp)
    }
}

/** Round icon buttons, four to a row. */
@Composable
private fun TileGrid(tiles: List<StudioTile>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(GRID_COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { t -> RoundTile(t, Modifier.weight(1f)) }
                repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun RoundTile(tile: StudioTile, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).bouncy(onClick = tile.onClick).semantics(mergeDescendants = true) { role = Role.Button }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(tile.tint), contentAlignment = Alignment.Center) {
            Icon(painterResource(tile.icon), null, tint = Ink, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(tile.label, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 15.sp)
    }
}

/** PDFs saved in My Files with a small picture of their first page; a filter once there are more than a few. */
@Composable
private fun RecentFiles(mine: List<MyFiles.Doc>, onDoc: (MyFiles.Doc) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("") }
    var all by rememberSaveable { mutableStateOf(false) }
    val shown = remember(mine, filter) { if (filter.isBlank()) mine else mine.filter { it.title.contains(filter.trim(), ignoreCase = true) } }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Text("Recent files", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 4.dp))
        if (shown.size > RECENT_SHOWN) {
            Text(if (all) "Show fewer" else "View all", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).bouncy { all = !all }.padding(6.dp))
        }
    }
    if (mine.isEmpty()) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardHi).border(1.dp, Line, RoundedCornerShape(18.dp)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("No documents yet", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("Scans and PDFs you make here show up in this list.", color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        return
    }
    if (mine.size > RECENT_SHOWN) SearchField(filter, { filter = it }, placeholder = "Search your files…", onGo = {})
    (if (all) shown else shown.take(RECENT_SHOWN)).forEach { doc -> RecentRow(doc) { onDoc(doc) } }
    if (shown.isEmpty()) Note("No file matches “${filter.trim()}”.")
}

@Composable
private fun RecentRow(doc: MyFiles.Doc, onClick: () -> Unit) {
    val thumb by produceState<Bitmap?>(null, doc.pdf) { value = firstPageThumb(doc.pdf) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardHi).border(1.dp, Line, RoundedCornerShape(16.dp))
            .bouncy(onClick = onClick).semantics(mergeDescendants = true) { role = Role.Button }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 44.dp, height = 56.dp).clip(RoundedCornerShape(6.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(6.dp))) {
            thumb?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(doc.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("PDF · ${doc.sizeText}", color = TextSecondary, fontSize = 12.sp)
        }
        Text("›", color = TextSecondary, fontSize = 22.sp)
    }
}

/** A small picture of a PDF's first page, or null if it can't be drawn (locked or damaged). */
private suspend fun firstPageThumb(pdf: File): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                r.openPage(0).use { p ->
                    val h = (THUMB_WIDTH_PX * p.height / p.width.coerceAtLeast(1)).coerceAtLeast(1)
                    Bitmap.createBitmap(THUMB_WIDTH_PX, h, Bitmap.Config.ARGB_8888).also {
                        it.eraseColor(android.graphics.Color.WHITE)
                        p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }
    }.getOrNull()
}

@Composable
private fun Note(text: String) {
    Text(text, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 4.dp))
}
