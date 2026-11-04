package com.cleanai.debloat.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanai.debloat.data.Formula
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/** One finished calculation: what was worked out and the answer. */
internal data class CalcEntry(val expr: String, val result: String, val time: Long = System.currentTimeMillis())

/** The last 100 calculations from the Basic, Scientific and floating calculators, kept privately on the phone. */
internal object CalcHistory {
    private const val MAX = 100
    val entries = mutableStateListOf<CalcEntry>()
    private var prefs: android.content.SharedPreferences? = null

    fun init(ctx: Context) {
        if (prefs != null) return
        prefs = ctx.applicationContext.getSharedPreferences("calculator", Context.MODE_PRIVATE)
        val arr = runCatching { JSONArray(prefs!!.getString("history", "[]")) }.getOrNull() ?: return
        entries.clear()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            entries += CalcEntry(o.optString("e"), o.optString("r"), o.optLong("t"))
        }
    }

    fun add(expr: String, result: String) {
        if (expr.isBlank() || expr == result) return
        if (entries.firstOrNull()?.let { it.expr == expr && it.result == result } == true) return
        entries.add(0, CalcEntry(expr, result))
        while (entries.size > MAX) entries.removeAt(entries.lastIndex)
        save()
    }

    fun clear() { entries.clear(); save() }

    private fun save() {
        val arr = JSONArray()
        entries.forEach { arr.put(JSONObject().put("e", it.expr).put("r", it.result).put("t", it.time)) }
        prefs?.edit()?.putString("history", arr.toString())?.apply()
    }

    /** The calculator's own symbols, without spaces, commas or a dangling operator at the end. */
    fun tidy(text: String) = text.replace(",", "").replace(" ", "").replace('*', '×').replace('/', '÷').replace('x', '×').replace('X', '×')
        .replace('-', '−').trimEnd('+', '−', '×', '÷')

    /** Works out a typed or edited sum; accepts * / - x as well as × ÷ −, and ignores thousands commas. */
    fun evaluate(text: String, degrees: Boolean = true, ans: Double = 0.0): BigDecimal {
        val clean = tidy(text)
        require(clean.isNotEmpty())
        val open = clean.count { it == '(' } - clean.count { it == ')' }
        return BigDecimal(Formula.eval(clean + ")".repeat(open.coerceAtLeast(0)), degrees, vars = mapOf("Ans" to ans)))
    }
}

/** Change a calculation: move the cursor, fix a number or an operator, and see the new answer straight away. */
@Composable
internal fun EditCalcDialog(start: String, degrees: Boolean = true, onDismiss: () -> Unit, onDone: (String, BigDecimal) -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(start, TextRange(start.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val answer = runCatching { CalcHistory.evaluate(value.text, degrees) }
    fun insert(s: String) {
        val t = value.text
        val sel = value.selection
        val next = t.substring(0, sel.min) + s + t.substring(sel.max)
        value = TextFieldValue(next, TextRange(sel.min + s.length))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1E),
        titleContentColor = Color.White,
        title = { Text("Edit calculation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value, { value = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 22.sp, textAlign = TextAlign.End),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("+", "−", "×", "÷", "(", ")", ".", "%").forEach { k ->
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(if (k in "+−×÷") KeyOp else KeyDigit).clickable { insert(k) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) { Text(k, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium) }
                    }
                }
                Text(
                    answer.getOrNull()?.let { "= " + formatNumber(it) } ?: if (value.text.isBlank()) " " else "Can't work this out yet",
                    color = if (answer.isSuccess) Color.White else KeyFn, fontSize = 26.sp, textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = answer.isSuccess, onClick = { onDone(CalcHistory.tidy(value.text), answer.getOrThrow()) }) { Text("=  Use this", color = KeyOp) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = KeyFn) } },
    )
}

/** "🕘 History" and "✎ Edit" above a calculator's display. */
@Composable
internal fun CalcTools(editEnabled: Boolean, onHistory: () -> Unit, onEdit: () -> Unit, editLabel: String = "✎  Edit") {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        ToolPill("🕘  History", true, onHistory)
        ToolPill(editLabel, editEnabled, onEdit)
    }
}

@Composable
private fun ToolPill(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(KeyDigit).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(label, color = if (enabled) Color.White else Color.White.copy(alpha = 0.35f), fontSize = 14.sp) }
}

/** Past calculations, newest first; tap one to edit and reuse it. */
@Composable
internal fun HistoryDialog(onDismiss: () -> Unit, onPick: (CalcEntry) -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1E),
        titleContentColor = Color.White,
        title = { Text("History") },
        text = {
            if (CalcHistory.entries.isEmpty()) Text("Your calculations will show up here.", color = KeyFn)
            else LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(CalcHistory.entries, key = { it.time.toString() + it.expr }) { e ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(e) }.padding(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        Text(e.expr, color = KeyFn, fontSize = 15.sp, textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                        Text("= ${e.result}", color = Color.White, fontSize = 22.sp, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = KeyOp) } },
        dismissButton = {
            if (CalcHistory.entries.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Clear", color = KeyFn) }
        },
    )
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("All past calculations will be removed.") },
            confirmButton = { TextButton(onClick = { CalcHistory.clear(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}
