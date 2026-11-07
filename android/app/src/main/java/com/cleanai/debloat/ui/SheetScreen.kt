package com.cleanai.debloat.ui

import com.cleanai.debloat.data.FileNames
import android.net.Uri
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanai.debloat.data.CellRef
import com.cleanai.debloat.data.Formula
import com.cleanai.debloat.data.PdfEdit
import com.cleanai.debloat.data.RecentDocs
import com.cleanai.debloat.data.SheetData
import com.cleanai.debloat.data.Workbook
import com.cleanai.debloat.data.Sheets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private val CellW = 96.dp
private val CellH = 36.dp
private val HeadW = 44.dp

private class SheetState(name: String, cells: Map<CellRef, String>, val part: String? = null) {
    var name by mutableStateOf(name)
    val cells = mutableStateMapOf<CellRef, String>().apply { putAll(cells) }
    fun data() = SheetData(name, cells.toMap(), part)
}

/** Spreadsheets: open and edit Excel .xlsx and .csv files, with formulas like =SUM(A1:A9). */
@Composable
fun SheetScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheets = remember { mutableStateListOf<SheetState>() }
    var current by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var uri by remember { mutableStateOf<Uri?>(null) }
    var name by remember { mutableStateOf("Spreadsheet.xlsx") }
    var csv by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    // The opened .xlsx, so saving keeps its styles, merged cells, charts and other sheets.
    var source by remember { mutableStateOf<Sheets.Source?>(null) }
    // Set when saving can't keep everything in the opened file: the changes then go to a separate, simpler copy.
    var simpleCopy by remember { mutableStateOf(false) }
    var askCopy by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf<NameAsk?>(null) }
    var version by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf(CellRef(1, 1)) }
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    var barFocused by remember { mutableStateOf(false) }
    val bar = remember { FocusRequester() }

    fun load(data: List<SheetData>) {
        sheets.clear()
        data.forEach { sheets += SheetState(it.name, it.cells, it.part) }
        current = 0
        selected = CellRef(1, 1)
        draft = TextFieldValue(sheets[0].cells[selected].orEmpty())
        dirty = false
        version++
        editing = true
    }

    fun book() = Workbook(sheets.map { it.data() }, source)

    // The whole file is made first; only then is the old one replaced, so a failed save never leaves it half-written.
    fun write(target: Uri, asCsv: Boolean): Boolean = runCatching {
        val buf = ByteArrayOutputStream()
        when {
            asCsv -> Sheets.writeCsv(sheets[current].data(), buf)
            simpleCopy -> Sheets.writeXlsx(sheets.map { it.data() }, buf)
            // Saving into a different file than the one opened: if the original's extras can't all be kept,
            // that new file gets the simpler form; the opened file itself is never touched.
            target != uri && !Sheets.keepsEverything(book()) -> { simpleCopy = true; Sheets.writeXlsx(sheets.map { it.data() }, buf) }
            else -> Sheets.writeWorkbook(book(), buf)
        }
        val bytes = buf.toByteArray()
        (ctx.contentResolver.openOutputStream(target, "wt") ?: error("no stream")).use { it.write(bytes) }
    }.isSuccess

    fun saved(ok: Boolean, target: Uri, asCsv: Boolean) {
        if (ok) {
            uri = target; csv = asCsv; name = PdfEdit.displayName(ctx, target); dirty = false
            RecentDocs.add(ctx, RecentDocs.Kind.EXCEL, target, name)
            // A simple copy (or a CSV) has none of the original's extras left to keep.
            if (simpleCopy || asCsv) { source = null; simpleCopy = false }
            Toast.makeText(ctx, "Saved $name", Toast.LENGTH_SHORT).show()
        } else Toast.makeText(ctx, "⚠️ Couldn't save there. Try another folder.", Toast.LENGTH_SHORT).show()
    }

    val saveXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX)) { out ->
        if (out != null) scope.launch { saved(withContext(Dispatchers.IO) { write(out, false) }, out, false) }
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { out ->
        if (out != null) scope.launch { saved(withContext(Dispatchers.IO) { write(out, true) }, out, true) }
    }
    val base = name.substringBeforeLast('.')

    /** Renames the open file where it is; a file not saved yet just takes the new name. */
    fun renameTo(file: String) {
        val u = uri
        if (u == null) { name = file; return }
        scope.launch {
            val renamed = withContext(Dispatchers.IO) { FileNames.renameDocument(ctx, u, file) }
            if (renamed == null) {
                Toast.makeText(ctx, "This folder doesn't allow renaming. Use Save as Excel instead.", Toast.LENGTH_LONG).show()
                return@launch
            }
            RecentDocs.remove(ctx, RecentDocs.Kind.EXCEL, u)
            uri = renamed; name = PdfEdit.displayName(ctx, renamed)
            RecentDocs.add(ctx, RecentDocs.Kind.EXCEL, renamed, name)
            Toast.makeText(ctx, "Renamed to $name", Toast.LENGTH_SHORT).show()
        }
    }

    fun save() {
        val target = uri
        if (target == null) { naming = NameAsk(NameFor.NEW_FILE, ".xlsx", base); return }
        scope.launch {
            if (!csv && !withContext(Dispatchers.IO) { Sheets.keepsEverything(book()) }) { askCopy = true; return@launch }
            val ok = withContext(Dispatchers.IO) { write(target, csv) }
            if (ok) { dirty = false; Toast.makeText(ctx, if (csv) "Saved (CSV keeps values, not formulas)" else "Saved", Toast.LENGTH_SHORT).show() }
            else if (csv) saveCsv.launch("$base.csv") else saveXlsx.launch("$base.xlsx")
        }
    }

    fun openUri(picked: Uri) {
        val fileName = PdfEdit.displayName(ctx, picked)
        scope.launch {
            val isCsv = fileName.endsWith(".csv", true) || fileName.endsWith(".txt", true) || fileName.endsWith(".tsv", true)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openInputStream(picked)!!.use { if (isCsv) Workbook(listOf(Sheets.readCsv(it))) else Sheets.readWorkbook(it) }
                }
            }
            result.onSuccess {
                uri = picked; name = fileName; csv = isCsv; source = it.source; simpleCopy = false
                RecentDocs.add(ctx, RecentDocs.Kind.EXCEL, picked, fileName)
                load(it.sheets)
            }.onFailure {
                // A file that has moved or been deleted drops off the recent list.
                RecentDocs.remove(ctx, RecentDocs.Kind.EXCEL, picked)
                Toast.makeText(ctx, "Couldn't open this file. Excel .xlsx and .csv files are supported (not old .xls).", Toast.LENGTH_LONG).show()
            }
        }
    }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked -> picked?.let(::openUri) }
    // An .xlsx or .csv opened from another app. If this one has unsaved changes, it asks first.
    DocLaunch.Receiver(DocLaunch.Kind.EXCEL, dirty, name, ::openUri)

    fun newSheet() {
        uri = null; name = "Spreadsheet.xlsx"; csv = false; source = null; simpleCopy = false
        load(listOf(SheetData("Sheet1", emptyMap())))
    }
    fun openSheet() = open.launch(arrayOf(XLSX, "text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream"))

    val recents = remember(editing) { RecentDocs.list(ctx, RecentDocs.Kind.EXCEL) }
    if (!editing) {
        Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
            ScreenTopBar("Excel Editor", onBack)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BigAction("New spreadsheet", "Rows, columns and formulas") { newSheet() }
                ToolRow("Open a spreadsheet", "Excel .xlsx or .csv from your phone, Drive or Downloads") { openSheet() }
                RecentDocsList("Recent spreadsheets", recents) { openUri(it.uri) }
                Text(
                    "Formulas: + − * / ^ %, cell references like A1 and ranges like A1:B9, and SUM, AVERAGE, MIN, MAX, COUNT, PRODUCT, IF, AND, OR, ROUND, ABS, SQRT, POWER, MOD. " +
                        "Saved .xlsx files open in Excel, Google Sheets and LibreOffice. In a file you open, only the cells you change are rewritten: " +
                        "colours, number formats, merged cells, charts and other sheets stay as they are.",
                    color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        return
    }

    val sheet = sheets[current.coerceIn(0, sheets.lastIndex)]
    val eval = remember(version, current) { Sheets.Evaluator { sheet.cells[it] } }

    fun commit() {
        val text = draft.text.trim()
        val old = sheet.cells[selected].orEmpty()
        if (text == old) return
        if (text.isEmpty()) sheet.cells.remove(selected) else sheet.cells[selected] = text
        dirty = true
        version++
    }

    fun select(ref: CellRef) {
        // While typing a formula, tapping a cell adds its name ("=SUM(" + tap B2 -> "=SUM(B2").
        if (barFocused && draft.text.startsWith("=") && draft.text != sheet.cells[selected].orEmpty() && ref != selected) {
            val at = draft.selection.start
            val text = draft.text.substring(0, at) + ref.name + draft.text.substring(at)
            draft = TextFieldValue(text, TextRange(at + ref.name.length))
            return
        }
        commit()
        selected = ref
        draft = TextFieldValue(sheet.cells[ref].orEmpty(), TextRange(sheet.cells[ref].orEmpty().length))
    }

    fun insert(fn: String) {
        val t = if (draft.text.isEmpty()) "=$fn(" else draft.text + "$fn("
        draft = TextFieldValue(t, TextRange(t.length))
        runCatching { bar.requestFocus() }
    }

    /** Σ: sums the numbers right above the cell (or to its left). */
    fun autoSum() {
        fun isNum(r: CellRef) = runCatching { eval.value(r) is Double }.getOrDefault(false)
        var top = selected.row - 1
        while (top >= 1 && isNum(CellRef(selected.col, top))) top--
        val formula = if (top < selected.row - 1) "=SUM(${CellRef(selected.col, top + 1).name}:${CellRef(selected.col, selected.row - 1).name})"
        else {
            var left = selected.col - 1
            while (left >= 1 && isNum(CellRef(left, selected.row))) left--
            if (left < selected.col - 1) "=SUM(${CellRef(left + 1, selected.row).name}:${CellRef(selected.col - 1, selected.row).name})" else "=SUM("
        }
        draft = TextFieldValue(formula, TextRange(formula.length))
        if (formula.endsWith(")")) commit() else runCatching { bar.requestFocus() }
    }

    BackHandler { if (dirty) leaving = true else editing = false }
    val cols = ((sheet.cells.keys.maxOfOrNull { it.col } ?: 0) + 3).coerceIn(8, 702)
    val rows = ((sheet.cells.keys.maxOfOrNull { it.row } ?: 0) + 40).coerceAtLeast(100)

    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding().imePadding()) {
        ScreenTopBar(
            base,
            onBack = { commit(); if (dirty) leaving = true else editing = false },
            subtitle = if (dirty) "Not saved" else if (csv) "CSV" else "Excel",
            action = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PillButton("Save", Accent, OnAccent) { commit(); save() }
                    Box {
                        Text("⋮", color = TextPrimary, fontSize = 22.sp, modifier = Modifier.clickable { menu = true }.padding(horizontal = 10.dp, vertical = 4.dp))
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; naming = NameAsk(NameFor.RENAME, if (csv) ".csv" else ".xlsx", base) })
                            DropdownMenuItem(text = { Text("Save as Excel (.xlsx)") }, onClick = { menu = false; commit(); naming = NameAsk(NameFor.COPY, ".xlsx", base) })
                            DropdownMenuItem(text = { Text("Save this sheet as CSV") }, onClick = { menu = false; commit(); naming = NameAsk(NameFor.COPY, ".csv", base) })
                            uri?.let { u ->
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    onClick = { menu = false; if (dirty) Toast.makeText(ctx, "Save first to share your latest changes.", Toast.LENGTH_SHORT).show() else shareFile(ctx, u, if (csv) "text/csv" else XLSX) },
                                )
                            }
                        }
                    }
                }
            },
        )
        // Formula bar: cell name, what's typed in it, and ✓.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(12.dp)).background(CardHi).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(selected.name, color = Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(52.dp))
            BasicTextField(
                draft,
                { draft = it },
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 17.sp),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = {
                    // Like Excel's Enter: keep it and move down.
                    commit()
                    selected = CellRef(selected.col, selected.row + 1)
                    draft = TextFieldValue(sheet.cells[selected].orEmpty())
                }),
                modifier = Modifier.weight(1f).focusRequester(bar).onFocusChanged { barFocused = it.isFocused }.padding(vertical = 6.dp),
            )
            Text("✓", color = TextPrimary, fontSize = 20.sp, modifier = Modifier.clickable { commit() }.padding(horizontal = 8.dp))
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ChipButton("Σ AutoSum") { autoSum() }
            listOf("SUM", "AVERAGE", "MIN", "MAX", "COUNT", "IF", "ROUND").forEach { fn -> ChipButton(fn) { insert(fn) } }
            ChipButton("Clear cell") {
                sheet.cells.remove(selected); draft = TextFieldValue(""); dirty = true; version++
            }
        }

        val hs = rememberScrollState()
        Column(Modifier.weight(1f).fillMaxWidth().background(Color.White)) {
            Row {
                HeaderCell("", HeadW)
                Row(Modifier.horizontalScroll(hs)) {
                    for (c in 1..cols) HeaderCell(Formula.colName(c), CellW, c == selected.col)
                }
            }
            LazyColumn(Modifier.weight(1f)) {
                items(rows) { i ->
                    val r = i + 1
                    Row {
                        HeaderCell(r.toString(), HeadW, r == selected.row)
                        Row(Modifier.horizontalScroll(hs)) {
                            for (c in 1..cols) {
                                val ref = CellRef(c, r)
                                GridCell(sheet.cells[ref], eval.display(ref), ref == selected) { select(ref) }
                            }
                        }
                    }
                }
            }
        }
        // Sheet tabs.
        Row(
            Modifier.fillMaxWidth().background(CardHi).navigationBarsPadding().horizontalScroll(rememberScrollState()).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            sheets.forEachIndexed { i, s ->
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(if (i == current) Charcoal else Card)
                        .clickable { commit(); current = i; selected = CellRef(1, 1); draft = TextFieldValue(sheets[i].cells[selected].orEmpty()) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text(s.name, color = if (i == current) Color.White else TextPrimary, fontSize = 14.sp) }
            }
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(Card)
                    .clickable { commit(); sheets += SheetState("Sheet${sheets.size + 1}", emptyMap()); current = sheets.lastIndex; dirty = true; version++ }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) { Text("+ Sheet", color = TextPrimary, fontSize = 14.sp) }
        }
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
    naming?.let { ask ->
        NameDialog(ask.why.title, ask.initial, ask.ext, confirm = ask.why.confirm, onDismiss = { naming = null }, onDone = { n ->
            naming = null
            when {
                ask.why == NameFor.RENAME -> renameTo(n + ask.ext)
                ask.ext == ".csv" -> saveCsv.launch(n + ask.ext)
                else -> saveXlsx.launch(n + ask.ext)
            }
        })
    }
    if (askCopy) {
        AlertDialog(
            onDismissRequest = { askCopy = false },
            title = { Text("Save as a new copy?") },
            text = {
                Text(
                    "This change can't be saved into $name without losing some of its formatting or formulas. " +
                        "Your changes will go into a new, simpler copy (values and formulas only), and $name stays exactly as it is.",
                )
            },
            confirmButton = { TextButton(onClick = { askCopy = false; simpleCopy = true; saveXlsx.launch("$base (edited).xlsx") }) { Text("Save copy") } },
            dismissButton = { TextButton(onClick = { askCopy = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HeaderCell(label: String, width: androidx.compose.ui.unit.Dp, lit: Boolean = false) {
    Box(
        Modifier.size(width, CellH).background(if (lit) Color(0xFFFFE3DE) else Color(0xFFF1F1EF)).border(0.5.dp, Color(0xFFD6D6D2)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color(0xFF55605A), fontSize = 12.sp, fontWeight = if (lit) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun GridCell(raw: String?, shown: String, selected: Boolean, onClick: () -> Unit) {
    val number = raw != null && (raw.startsWith("=") || Sheets.literal(raw) is Double)
    val error = shown.startsWith("#")
    Box(
        Modifier.size(CellW, CellH).background(Color.White)
            .border(if (selected) 2.dp else 0.5.dp, if (selected) Accent else Color(0xFFE0E0DC))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = if (number && !error) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Text(
            shown,
            color = if (error) Color(0xFFD64535) else Color(0xFF1B1B1B),
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = if (number) TextAlign.End else TextAlign.Start,
        )
    }
}

@Composable
private fun ChipButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(Card).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
