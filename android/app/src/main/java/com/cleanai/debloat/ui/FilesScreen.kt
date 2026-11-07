package com.cleanai.debloat.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.ui.text.style.TextOverflow
import com.cleanai.debloat.R
import com.cleanai.debloat.data.MyFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The app's own folder of scans and photo PDFs: open, share as PDF or as images, delete. */
@Composable
fun FilesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var version by remember { mutableStateOf(0) }
    val docs = remember(version) { MyFiles.list(ctx) }
    var deleting by remember { mutableStateOf<MyFiles.Doc?>(null) }
    var renaming by remember { mutableStateOf<MyFiles.Doc?>(null) }

    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        ScreenTopBar("My Files", onBack, subtitle = "${docs.size} document${if (docs.size == 1) "" else "s"} · on this phone")
        if (docs.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(Mint), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_folder), null, tint = Ink, modifier = Modifier.size(34.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text("No files yet", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Scan a document or turn photos into a PDF — they'll be saved here, ready to share.",
                    color = TextSecondary, fontSize = 15.sp, textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(docs, key = { _, d -> d.dir.name }) { i, doc ->
                    Box(Modifier.enter(i)) { DocCard(
                        doc,
                        onOpen = { MyFiles.open(ctx, doc) },
                        onSharePdf = { MyFiles.sharePdf(ctx, doc) },
                        onShareImages = { MyFiles.shareImages(ctx, doc) },
                        onDelete = { deleting = doc },
                        onRename = { renaming = doc },
                    ) }
                }
            }
        }
    }

    renaming?.let { doc ->
        NameDialog("Rename", doc.name ?: doc.kind, ".pdf", confirm = "Rename", onDismiss = { renaming = null }, onDone = { name ->
            if (!MyFiles.rename(doc, name)) Toast.makeText(ctx, "Couldn't rename this file.", Toast.LENGTH_SHORT).show()
            renaming = null; version++
        })
    }
    deleting?.let { doc ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = CardHi,
            title = { Text("Delete this file?", color = TextPrimary) },
            text = { Text("${doc.title} will be removed from this phone. This can't be undone.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { MyFiles.delete(doc); deleting = null; version++ }) { Text("Delete", color = Red) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel", color = TextPrimary) } },
        )
    }
}

@Composable
private fun DocCard(doc: MyFiles.Doc, onOpen: () -> Unit, onSharePdf: () -> Unit, onShareImages: () -> Unit, onDelete: () -> Unit, onRename: () -> Unit) {
    val pages = doc.images.size
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CardHi)
            .border(1.dp, Line, RoundedCornerShape(24.dp))
            .padding(14.dp),
    ) {
        Row(Modifier.bouncy(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
            Thumb(doc)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(doc.name ?: doc.kind, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (doc.name != null) "${doc.kind} · ${doc.dateText}" else doc.dateText, color = TextSecondary, fontSize = 14.sp)
                Text(
                    listOfNotNull(if (pages > 0) "$pages page${if (pages == 1) "" else "s"}" else null, doc.sizeText).joinToString(" · "),
                    color = TextSecondary, fontSize = 14.sp,
                )
            }
            Box(Modifier.size(40.dp).clip(CircleShape).bouncy(onClick = onRename), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = "Rename", tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Action("Open", Charcoal, Color.White, Modifier.weight(1f), onOpen)
            if (doc.hasPdf) Action("Share PDF", Card, TextPrimary, Modifier.weight(1.2f), onSharePdf)
            if (pages > 0) Action(if (doc.hasPdf) "Images" else "Share", Card, TextPrimary, Modifier.weight(1f), onShareImages)
            Action("Delete", Card, Red, Modifier.weight(1f), onDelete)
        }
    }
}

/** First page as a small preview, decoded off the main thread. */
@Composable
private fun Thumb(doc: MyFiles.Doc) {
    val bmp by produceState<ImageBitmap?>(null, doc.dir.name) {
        value = withContext(Dispatchers.IO) {
            doc.images.firstOrNull()?.let { f ->
                runCatching {
                    BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 8 })?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    Box(
        Modifier.size(width = 64.dp, height = 84.dp).clip(RoundedCornerShape(12.dp)).background(Sky),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) Image(b, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(painterResource(R.drawable.ic_tool_pdf), null, tint = Ink, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun Action(label: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.bouncy(onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}
