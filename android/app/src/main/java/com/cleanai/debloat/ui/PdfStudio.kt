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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
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
    val openDoc: (MyFiles.Doc) -> Unit,
    val showAllTools: () -> Unit,
    val deleteDoc: (MyFiles.Doc) -> Unit,
    val renameDoc: (MyFiles.Doc, String) -> Unit,
)

/** One tool on the All tools page: a round icon, its name and what it does. */
internal class ToolEntry(val label: String, val detail: String, @DrawableRes val icon: Int, val tint: Color, val onClick: () -> Unit)

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

/** Every studio tool, grouped by what you want to do, for the All tools page. */
private fun allToolGroups(a: StudioActions) = listOf(
    "Edit & sign" to listOf(
        ToolEntry("Edit", "Change text, draw, highlight, add pictures", R.drawable.ic_edit, Sky) { a.openPdf(EditorStart.READ) },
        ToolEntry("Sign", "Add your signature to a page", R.drawable.ic_sign, Butter) { a.openPdf(EditorStart.SIGN) },
        ToolEntry("Stamp", "Approved, Paid, Draft and more", R.drawable.ic_stamp, CoralTint) { a.openPdf(EditorStart.STAMP) },
    ),
    "Pages" to listOf(
        ToolEntry("Merge", PdfTool.MERGE.blurb, R.drawable.ic_merge, Mint) { a.pdfTool(PdfTool.MERGE) },
        ToolEntry("Split", PdfTool.EXTRACT.blurb, R.drawable.ic_split, Butter) { a.pdfTool(PdfTool.EXTRACT) },
        ToolEntry("Remove pages", PdfTool.REMOVE.blurb, R.drawable.ic_pages, CoralTint) { a.pdfTool(PdfTool.REMOVE) },
    ),
    "Protect & share" to listOf(
        ToolEntry("Compress", PdfTool.COMPRESS.blurb, R.drawable.ic_compress, CoralTint) { a.pdfTool(PdfTool.COMPRESS) },
        ToolEntry("Watermark", PdfTool.WATERMARK.blurb, R.drawable.ic_watermark, Sky) { a.pdfTool(PdfTool.WATERMARK) },
        ToolEntry("Password", PdfTool.PASSWORD.blurb, R.drawable.ic_lock, Butter) { a.pdfTool(PdfTool.PASSWORD) },
    ),
    "Create a PDF" to listOf(
        ToolEntry("Scan", "Use the camera to scan paper pages", R.drawable.ic_tool_doc, Mint) { a.openTool("docscan") },
        ToolEntry("Photos to PDF", "Turn pictures from your gallery into a PDF", R.drawable.ic_tool_pdf, Sky) { a.openTool("pdf") },
    ),
)

/**
 * The PDF Editor's start page: one "open" card, round tool buttons and recent PDFs.
 * Every button leads to something that already works; nothing here is a placeholder.
 */
@Composable
internal fun StudioHome(mine: List<MyFiles.Doc>, actions: StudioActions) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OpenCard("Open a PDF", "Read, find text, edit and sign", R.drawable.ic_tool_reader) { actions.openPdf(EditorStart.READ) }
        TileGrid(pdfTiles(actions))
        RecentFiles(mine, actions)
        Note("Everything happens on your phone — your documents are never uploaded.")
    }
}

/** The All tools page: every studio tool in a card for its group, each with a line on what it does. */
@Composable
internal fun StudioAllTools(actions: StudioActions) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)) {
        allToolGroups(actions).forEach { (title, entries) -> ToolGroupCard(title, entries) }
    }
}

@Composable
private fun ToolGroupCard(title: String, entries: List<ToolEntry>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardHi).border(1.dp, Line, RoundedCornerShape(20.dp))) {
            entries.forEachIndexed { i, e ->
                if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 72.dp).height(1.dp).background(Line))
                ToolEntryRow(e)
            }
        }
    }
}

@Composable
private fun ToolEntryRow(e: ToolEntry) {
    Row(
        Modifier.fillMaxWidth().bouncy(onClick = e.onClick).semantics(mergeDescendants = true) { role = Role.Button }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(e.tint), contentAlignment = Alignment.Center) {
            Icon(painterResource(e.icon), null, tint = Ink, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(e.label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(e.detail, color = TextSecondary, fontSize = 13.sp, maxLines = 2, lineHeight = 17.sp)
        }
        Text("›", color = TextSecondary, fontSize = 22.sp)
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
            Icon(painterResource(icon), null, tint = OnAccent, modifier = Modifier.size(26.dp))
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
internal fun TileGrid(tiles: List<StudioTile>) {
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
internal fun RoundTile(tile: StudioTile, modifier: Modifier) {
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
private fun RecentFiles(mine: List<MyFiles.Doc>, actions: StudioActions) {
    var filter by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<MyFiles.Doc?>(null) }
    var renaming by remember { mutableStateOf<MyFiles.Doc?>(null) }
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
    (if (all) shown else shown.take(RECENT_SHOWN)).forEach { doc ->
        RecentRow(doc, onClick = { actions.openDoc(doc) }, onRename = { renaming = doc }, onDelete = { deleting = doc })
    }
    renaming?.let { doc ->
        NameDialog("Rename", doc.name ?: doc.kind, ".pdf", confirm = "Rename", onDismiss = { renaming = null }, onDone = { actions.renameDoc(doc, it); renaming = null })
    }
    deleting?.let { doc ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this file?") },
            text = { Text("\u201C${doc.title}\u201D will be removed from your phone. This can't be undone.") },
            confirmButton = { TextButton(onClick = { actions.deleteDoc(doc); deleting = null }) { Text("Delete", color = Red) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
    if (shown.isEmpty()) Note("No file matches “${filter.trim()}”.")
}

@Composable
private fun RecentRow(doc: MyFiles.Doc, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
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
        Box(Modifier.size(40.dp).clip(CircleShape).bouncy(onClick = onRename), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_edit), contentDescription = "Rename ${doc.title}", tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
        Box(Modifier.size(40.dp).clip(CircleShape).bouncy(onClick = onDelete), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete ${doc.title}", tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
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
