package com.cleanai.debloat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanai.debloat.R
import com.cleanai.debloat.data.MyFiles
import com.cleanai.debloat.features.Features

/** Tools in the "Create" row of the studio: feature id and a label short enough for a quarter-width tile. */
private val CreateTools = listOf("docscan" to "Scan", "pdf" to "Photos", "word" to "Word", "excel" to "Excel")
private const val RECENT_SHOWN = 6
private const val FILTER_FROM = 5

/**
 * Document Studio's start page: open a PDF, create a document, the PDF tools, and recent PDFs.
 * Every tile leads to something that already works; nothing here is a placeholder.
 */
@Composable
internal fun StudioHome(
    mine: List<MyFiles.Doc>,
    onOpenPdf: () -> Unit,
    onTool: (PdfTool) -> Unit,
    onCreate: (String) -> Unit,
    onDoc: (MyFiles.Doc) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Everything for your PDFs and documents.", color = TextSecondary, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp))
        OpenCard(onOpenPdf)

        StudioLabel("Create")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CreateTools.mapNotNull { (id, label) -> Features.byId(id)?.let { it to label } }.forEach { (f, label) ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(CardHi).border(1.dp, Line, RoundedCornerShape(18.dp))
                        .bouncy { onCreate(f.id) }.semantics(mergeDescendants = true) {}.padding(vertical = 12.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(f.tint), contentAlignment = Alignment.Center) {
                        Icon(painterResource(f.icon), null, tint = Ink, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.size(6.dp))
                    Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        StudioLabel("PDF tools")
        PdfTool.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { t -> ToolTile(t, Modifier.weight(1f)) { onTool(t) } }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        RecentPdfs(mine, onDoc)
        Text(
            "Everything happens on your phone — your PDFs are never uploaded.",
            color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun OpenCard(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Accent).bouncy(onClick = onClick)
            .semantics(mergeDescendants = true) {}.padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Open a PDF", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text("Read, find text, edit, sign, highlight and more", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
        }
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_tool_reader), null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun ToolTile(tool: PdfTool, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.heightIn(min = 92.dp).clip(RoundedCornerShape(18.dp)).background(CardHi).border(1.dp, Line, RoundedCornerShape(18.dp))
            .bouncy(onClick = onClick).semantics(mergeDescendants = true) {}.padding(14.dp),
    ) {
        Text(tool.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.size(2.dp))
        Text(tool.blurb, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** PDFs saved in My Files, newest first, with a filter once there are a few. */
@Composable
private fun RecentPdfs(mine: List<MyFiles.Doc>, onDoc: (MyFiles.Doc) -> Unit) {
    if (mine.isEmpty()) return
    var filter by rememberSaveable { mutableStateOf("") }
    var all by rememberSaveable { mutableStateOf(false) }
    val shown = remember(mine, filter) { if (filter.isBlank()) mine else mine.filter { it.title.contains(filter.trim(), ignoreCase = true) } }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        StudioLabel("Recent PDFs", Modifier.weight(1f))
        if (shown.size > RECENT_SHOWN) {
            Text(if (all) "Show fewer" else "View all", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).bouncy { all = !all }.padding(6.dp))
        }
    }
    if (mine.size >= FILTER_FROM) SearchField(filter, { filter = it }, placeholder = "Search your PDFs…", onGo = {})
    (if (all) shown else shown.take(RECENT_SHOWN)).forEach { doc -> ToolRow(doc.title, doc.sizeText) { onDoc(doc) } }
    if (shown.isEmpty()) Text("No PDF matches “${filter.trim()}”.", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
}

@Composable
private fun StudioLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = modifier.padding(start = 4.dp, top = 6.dp))
}
