package com.cleanai.debloat.ui

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanai.debloat.R
import com.cleanai.debloat.data.MyFiles
import com.cleanai.debloat.features.Feature
import com.cleanai.debloat.features.Features
import com.cleanai.debloat.features.ToolGroup

private val DefaultPins = listOf("docscan", "calculator", "pdfreader", "qr")

/** Short names for the small round buttons on Home. */
private val ShortLabel = mapOf(
    "docscan" to "Scan", "calculator" to "Calculator", "pdfreader" to "Edit PDF", "qr" to "Scan QR", "notes" to "Notes",
    "pdf" to "Images → PDF", "photo" to "Photo", "weather" to "Weather", "adblock" to "Ad Block", "cleaner" to "Cleaner",
    "files" to "Files", "restore" to "Restore", "word" to "Word", "excel" to "Excel",
)

private fun shortLabel(f: Feature) = ShortLabel[f.id] ?: f.title

/** The three document editors, each its own tool. */
private val DocumentTools = listOf("pdfreader" to "PDF", "word" to "Word", "excel" to "Excel")

private const val MORE_TOOLS_SHOWN = 7

/** The four Home shortcuts, saved on the phone; the user can swap any of them. */
private object Pins {
    private const val KEY = "home_pins"

    fun load(ctx: Context): List<String> {
        val saved = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).getString(KEY, null)
            ?.split(',')?.filter { Features.byId(it) != null }
        return if (saved?.size == DefaultPins.size) saved else DefaultPins
    }

    fun save(ctx: Context, ids: List<String>) {
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString(KEY, ids.joinToString(",")).apply()
    }
}

/**
 * Home, task first: Sparky's greeting, one bar to ask or search, four quick actions, the PDF / Word / Excel editors,
 * more tools, and what you were working on.
 */
@Composable
fun HomeScreen(
    name: String,
    onFeature: (Feature) -> Unit,
    onAsk: (String?) -> Unit,
    onOpenDoc: (MyFiles.Doc) -> Unit,
    onGroup: (ToolGroup?) -> Unit,
    onSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val pins = remember { mutableStateListOf<String>().apply { addAll(Pins.load(ctx)) } }
    var editing by remember { mutableStateOf<Int?>(null) }
    val recent = remember { MyFiles.list(ctx).take(2) }
    val focus = LocalFocusManager.current

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Box(Modifier.enter(0)) { Header(name, onSparky = { onAsk(null) }, onSettings = onSettings) } }
        item {
            Box(Modifier.enter(1)) {
                SearchField(query, { query = it }, placeholder = "Ask Sparky or find a tool…", onGo = {
                    val hits = searchTools(query)
                    focus.clearFocus()
                    if (hits.size == 1) onFeature(hits[0]) else if (query.isNotBlank()) onAsk(query.trim())
                })
            }
        }
        if (query.isNotBlank()) {
            val hits = searchTools(query)
            hits.forEach { f -> item(key = "hit_${f.id}") { ToolRow(f) { focus.clearFocus(); onFeature(f) } } }
            item(key = "ask") {
                AskRow("Ask Sparky: “${query.trim()}”") { focus.clearFocus(); onAsk(query.trim()) }
            }
            if (hits.isEmpty()) item { Text("No tool by that name. Sparky can still help.", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp)) }
            return@LazyColumn
        }
        item { Box(Modifier.enter(2)) { SectionTitle("Quick actions", "Long-press to change") } }
        item {
            Row(Modifier.enter(3).fillMaxWidth()) {
                pins.forEachIndexed { slot, id ->
                    val f = Features.byId(id) ?: return@forEachIndexed
                    QuickTile(f, Modifier.weight(1f), onClick = { onFeature(f) }, onLongClick = { editing = slot })
                }
            }
        }
        item { Box(Modifier.enter(4)) { SectionTitle("Documents", null) } }
        item {
            Row(Modifier.enter(5), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DocumentTools.forEach { (id, label) ->
                    Features.byId(id)?.let { f -> DocumentCard(f, label, Modifier.weight(1f)) { onFeature(f) } }
                }
            }
        }
        item { Box(Modifier.enter(6)) { SectionTitle("More tools", "All tools", onAction = { onGroup(null) }) } }
        item {
            val more = Features.all.filter { f ->
                f.group != ToolGroup.APP && f.id !in pins && DocumentTools.none { it.first == f.id }
            }.take(MORE_TOOLS_SHOWN)
            val tiles = more.map { f -> StudioTile(shortLabel(f), f.icon, f.tint) { onFeature(f) } } +
                StudioTile("All tools", R.drawable.ic_grid, Sky) { onGroup(null) }
            Box(Modifier.enter(7)) { TileGrid(tiles) }
        }
        if (recent.isNotEmpty()) {
            item { Box(Modifier.enter(8)) { SectionTitle("Continue", null) } }
            recent.forEach { doc -> item(key = doc.dir.name) { Box(Modifier.enter(8)) { RecentRow(doc) { onOpenDoc(doc) } } } }
        }
        item {
            Text(BUILT_BY, color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }

    editing?.let { slot ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Choose a shortcut") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Features.all.filter { it.id !in pins || it.id == pins[slot] }.filter { it.group != ToolGroup.APP }.forEach { f ->
                        ToolRow(f, selected = f.id == pins[slot]) {
                            pins[slot] = f.id
                            Pins.save(ctx, pins.toList())
                            editing = null
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editing = null }) { Text("Close") } },
            dismissButton = {
                TextButton(onClick = { pins.clear(); pins.addAll(DefaultPins); Pins.save(ctx, DefaultPins); editing = null }) { Text("Reset all") }
            },
        )
    }
}

/** Tools whose name, description or group contains every word typed. */
internal fun searchTools(query: String): List<Feature> {
    val words = query.lowercase().split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()
    return Features.all.filter { f ->
        val hay = "${f.title} ${f.subtitle} ${f.group.title}".lowercase()
        words.all { it in hay }
    }
}

@Composable
private fun Header(name: String, onSparky: () -> Unit, onSettings: () -> Unit) {
    val hour = rememberHour()
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        SparkyPortrait(
            timeMood(hour),
            Modifier.size(60.dp),
            description = "Sparky. Opens the assistant",
            onTap = onSparky,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (name.isBlank()) greeting(hour) else "${greeting(hour)}, $name",
                color = TextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text("What shall we do?", color = TextPrimary, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
        }
        ThemeButton()
        Spacer(Modifier.width(8.dp))
        CircleButton(onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = TextPrimary, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun CircleButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(CardHi).border(1.dp, Line, CircleShape).bouncy(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Sun / moon button: switches between light and dark theme. */
@Composable
private fun ThemeButton() {
    val ctx = LocalContext.current
    CircleButton({ AppTheme.set(ctx, !AppTheme.dark) }) {
        Icon(
            painterResource(if (AppTheme.dark) R.drawable.ic_sun else R.drawable.ic_moon),
            contentDescription = if (AppTheme.dark) "Light theme" else "Dark theme",
            tint = TextPrimary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
internal fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String = "Find a tool or ask anything…", onGo: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).background(CardHi)
            .border(1.dp, Line, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
            if (value.isEmpty()) Text(placeholder, color = TextSecondary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value, onChange,
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onGo() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).bouncy { onChange("") },
                contentAlignment = Alignment.Center,
            ) { Text("✕", color = TextSecondary, fontSize = 14.sp) }
        }
    }
}

@Composable
internal fun SectionTitle(title: String, action: String?, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                color = if (onAction != null) Accent else TextSecondary,
                fontSize = 13.sp,
                fontWeight = if (onAction != null) FontWeight.SemiBold else FontWeight.Normal,
                modifier = if (onAction != null) Modifier.clip(RoundedCornerShape(8.dp)).bouncy(onClick = onAction).padding(horizontal = 6.dp, vertical = 8.dp) else Modifier,
            )
        }
    }
}

/** The pastel ball behind a tool's icon. */
@Composable
internal fun ToolIcon(f: Feature, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(f.tint)
            .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.4f), Color.Transparent), center = Offset(size.value * 0.7f, size.value * 0.6f), radius = size.value * 1.9f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(f.icon), contentDescription = null, tint = Ink, modifier = Modifier.size(size * 0.52f))
    }
}

/** A small round shortcut; long-press swaps it for another tool. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickTile(f: Feature, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp))
            .combinedClickable(role = Role.Button, onLongClickLabel = "Change shortcut", onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ToolIcon(f, 56.dp)
        Spacer(Modifier.size(6.dp))
        Text(shortLabel(f), color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One of the three document editors. */
@Composable
private fun DocumentCard(f: Feature, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(CardHi).border(1.dp, Line, RoundedCornerShape(20.dp))
            .bouncy(onClick = onClick).semantics(mergeDescendants = true) { role = Role.Button }.padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ToolIcon(f, 44.dp)
        Spacer(Modifier.size(8.dp))
        Text(label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun ToolRow(f: Feature, selected: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (selected) Card else CardHi)
            .border(1.dp, if (selected) Accent else Line, RoundedCornerShape(16.dp))
            .bouncy(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolIcon(f, 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(f.title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(f.subtitle, color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("›", color = TextSecondary, fontSize = 22.sp)
    }
}

@Composable
private fun AskRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).bouncy(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SparkyPortrait(SparkyMood.THINKING, Modifier.size(40.dp), animate = false)
        Spacer(Modifier.width(12.dp))
        Text(label, color = TextPrimary, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RecentRow(doc: MyFiles.Doc, onClick: () -> Unit) {
    val files = remember { Features.byId("files") }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardHi).border(1.dp, Line, RoundedCornerShape(16.dp))
            .bouncy(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val reader = Features.byId(if (doc.hasPdf) "pdfreader" else "photo") ?: files
        if (reader != null) ToolIcon(reader, 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(doc.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (doc.hasPdf) "PDF · ${doc.sizeText}" else "Photos · ${doc.sizeText}", color = TextSecondary, fontSize = 13.sp)
        }
        Text("Open", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
