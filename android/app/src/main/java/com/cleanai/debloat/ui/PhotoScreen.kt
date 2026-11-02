package com.cleanai.debloat.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import com.cleanai.debloat.R
import com.cleanai.debloat.data.MyFiles
import com.cleanai.debloat.data.PhotoAi
import com.cleanai.debloat.data.PhotoAi.Mode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * PixelUp-style photo tools, all on the phone: Enhance to HD, Restore old photos, Colorize black & white.
 * Pick a photo, choose a tool, compare before/after, then save to My Files / Gallery or share.
 */
@Composable
fun PhotoScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var result by remember { mutableStateOf<Bitmap?>(null) }
    var mode by remember { mutableStateOf(Mode.ENHANCE) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var showBefore by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<MyFiles.Doc?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var colorizeReady by remember { mutableStateOf(PhotoAi.colorizeReady(ctx)) }
    var download by remember { mutableStateOf<Float?>(null) }

    val prefs = remember { ctx.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    fun watchDownload(id: Long) = scope.launch {
        download = 0f
        while (true) {
            val p = withContext(Dispatchers.IO) { PhotoAi.downloadProgress(ctx, id) } ?: break
            download = p
            delay(700)
        }
        download = 1f
        colorizeReady = PhotoAi.unpackColorize(ctx)
        download = null
        prefs.edit().remove("colorize_dl").apply()
        Toast.makeText(ctx, if (colorizeReady) "Colorize is ready" else "Download didn't finish — try again", Toast.LENGTH_LONG).show()
    }
    LaunchedEffect(Unit) {
        prefs.getLong("colorize_dl", -1).takeIf { it >= 0 && !colorizeReady }?.let { watchDownload(it) }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            original = withContext(Dispatchers.IO) { load(ctx, uri) }
            result = null; saved = null; showBefore = false
            if (original == null) Toast.makeText(ctx, "Couldn't open that photo", Toast.LENGTH_SHORT).show()
        }
    }

    fun run() {
        val src = original ?: return
        saved = null; result = null; progress = 0f
        job = scope.launch {
            val out = runCatching {
                if (mode == Mode.COLORIZE) PhotoAi.colorize(ctx, src) { progress = it }
                else PhotoAi.upscale(ctx, mode, src) { progress = it }
            }
            progress = null
            out.onSuccess { result = it }
                .onFailure { if (it !is kotlinx.coroutines.CancellationException) Toast.makeText(ctx, "Couldn't process: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    BackHandler { job?.cancel(); onBack() }

    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        ScreenTopBar("Photo Enhancer", { job?.cancel(); onBack() }, subtitle = "AI on this phone · photos never leave it")
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // The photo, or a big "pick a photo" box.
            val shown = if (showBefore || result == null) original else result
            Box(
                Modifier.bouncy(enabled = progress == null) {
                        pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    .fillMaxWidth()
                    .aspectRatio(shown?.let { it.width.toFloat() / it.height }?.coerceIn(0.6f, 1.6f) ?: 1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardHi)
                    .border(1.dp, Line, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (shown != null) {
                    Image(shown.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    if (result != null) {
                        Text(
                            if (showBefore) "Before" else "After",
                            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                                .clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(72.dp).clip(CircleShape).background(Coral), contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_tool_photo), null, tint = Ink, modifier = Modifier.size(34.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("Tap to choose a photo", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        Text("Old, blurry or black & white", color = TextSecondary, fontSize = 15.sp)
                    }
                }
            }

            // Tools.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Mode.entries.forEachIndexed { i, m ->
                    val on = mode == m
                    Row(
                        Modifier.enter(i + 1)
                            .bouncy(enabled = progress == null) { mode = m; result = null; saved = null }
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (on) Charcoal else CardHi)
                            .border(1.dp, if (on) Charcoal else Line, RoundedCornerShape(18.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(m.label, color = if (on) Color.White else TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (m == Mode.COLORIZE && !colorizeReady) "${m.hint} · one-time ${PhotoAi.COLORIZE_SIZE} download" else m.hint,
                                color = if (on) Color.White.copy(alpha = 0.75f) else TextSecondary, fontSize = 14.sp,
                            )
                        }
                        if (on) Text("✓", color = Coral, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Main action / progress.
            val p = progress
            val d = download
            when {
                p != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${if (mode == Mode.COLORIZE) "Colorizing" else "Enhancing"}… ${(p * 100).toInt()}%",
                        color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                    )
                    LinearProgressIndicator(
                        progress = { p }, color = Coral, trackColor = Line,
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                    )
                    Btn("Cancel", Card, Coral) { job?.cancel(); progress = null }
                }
                mode == Mode.COLORIZE && !colorizeReady && d != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (d >= 1f) "Preparing colorizer…" else "Downloading colorizer… ${(d * 100).toInt()}% of ${PhotoAi.COLORIZE_SIZE}",
                        color = TextPrimary, fontSize = 16.sp,
                    )
                    LinearProgressIndicator(
                        progress = { d }, color = Coral, trackColor = Line,
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                    )
                }
                mode == Mode.COLORIZE && !colorizeReady ->
                    Btn("Download colorizer (${PhotoAi.COLORIZE_SIZE})", Charcoal, Color.White) {
                        val id = PhotoAi.startColorizeDownload(ctx)
                        prefs.edit().putLong("colorize_dl", id).apply()
                        watchDownload(id)
                    }
                original == null -> Btn("Choose a photo", Charcoal, Color.White) {
                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                result == null -> Btn(mode.label, Coral, Color.White) { run() }
                else -> {
                    val r = result!!
                    Text(
                        "Done · ${r.width} × ${r.height} px",
                        color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Btn(if (showBefore) "Show after" else "Show before", Card, TextPrimary, Modifier.weight(1f)) { showBefore = !showBefore }
                        Btn("New photo", Card, TextPrimary, Modifier.weight(1f)) {
                            pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Btn(if (saved != null) "Saved ✓" else "Save", Charcoal, Color.White, Modifier.weight(1f)) {
                            if (saved == null) scope.launch {
                                val doc = withContext(Dispatchers.IO) { MyFiles.savePhoto(ctx, r) }
                                val inGallery = withContext(Dispatchers.IO) { MyFiles.saveToGallery(ctx, doc.images.first()) }
                                saved = doc
                                Toast.makeText(
                                    ctx, if (inGallery) "Saved to My Files and Gallery" else "Saved to My Files", Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                        Btn("Share", Coral, Color.White, Modifier.weight(1f)) {
                            scope.launch {
                                val doc = saved ?: withContext(Dispatchers.IO) { MyFiles.savePhoto(ctx, r) }.also { saved = it }
                                MyFiles.shareImages(ctx, doc)
                            }
                        }
                    }
                }
            }
            Text(
                "Runs on this phone with open-source AI (Real-ESRGAN, DDColor). Bigger photos take longer.",
                color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun Btn(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    Box(
        modifier.bouncy(onClick = onClick).clip(RoundedCornerShape(50)).background(bg).padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

/** Decodes the photo at a sensible size (longest side ≤ 2048 px) and turns it upright using its EXIF data. */
private fun load(ctx: Context, uri: Uri): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2048) sample *= 2
    val bmp = ctx.contentResolver.openInputStream(uri)!!.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    }!!
    val rotation = ctx.contentResolver.openInputStream(uri)!!.use { ExifInterface(it).rotationDegrees }
    if (rotation == 0) bmp
    else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
}.getOrNull()
