package com.cleanai.debloat.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanai.debloat.data.Formula
import java.math.BigDecimal
import java.text.NumberFormat
import kotlin.math.pow

private val KeySci = Color(0xFF1F1F1F)
private val KeySciDown = Color(0xFF505050)
private val Panel = Color(0xFF1C1C1E)

// ---------------------------------------------------------------- scientific

private val SciKeys = listOf(
    listOf("Deg", "sin", "cos", "tan", "π"),
    listOf("ln", "log", "√", "x²", "xʸ"),
    listOf("(", ")", "x!", "1/x", "e"),
    listOf("7", "8", "9", "÷", "AC"),
    listOf("4", "5", "6", "×", "⌫"),
    listOf("1", "2", "3", "−", "Ans"),
    listOf("0", ".", "%", "+", "="),
)

/**
 * Scientific mode: you type the whole sum ("2 × sin(30) + √(16)") and see the answer as you go.
 * "=" keeps the answer as Ans, so the next operator or function carries on from it.
 */
internal class SciCalc {
    var expr by mutableStateOf(""); private set
    var degrees by mutableStateOf(true); private set
    /** The answer shown after "=", or null while typing. */
    var result by mutableStateOf<String?>(null); private set
    /** The sum that produced [result], shown above it. */
    var lastExpr by mutableStateOf(""); private set
    var error by mutableStateOf<String?>(null); private set
    private var ans = 0.0

    private fun eval(e: String): Double {
        // Close any brackets left open: "sin(30" works like "sin(30)".
        val open = e.count { it == '(' } - e.count { it == ')' }
        return Formula.eval(e + ")".repeat(open.coerceAtLeast(0)), degrees, vars = mapOf("Ans" to ans))
    }

    /** Live answer while typing, or null if the sum isn't finished yet. */
    val preview: String? get() {
        if (expr.isBlank() || result != null) return null
        return runCatching { formatNumber(BigDecimal(eval(expr))) }.getOrNull()?.takeIf { it != expr }
    }

    fun press(key: String) {
        error = null
        val done = result != null
        when (key) {
            "AC" -> { expr = ""; result = null; lastExpr = "" }
            "⌫" -> if (done) { result = null; expr = "" } else expr = dropToken(expr)
            "Deg", "Rad" -> degrees = !degrees
            "=" -> equals()
            "+", "−", "×", "÷", "xʸ" -> {
                val op = if (key == "xʸ") "^" else key
                val base = if (done) "Ans" else expr
                expr = if (base.isNotEmpty() && base.last() in "+−×÷^" && op != "−") base.dropLast(1) + op else base + op
                result = null
            }
            "sin", "cos", "tan", "ln", "log" -> apply(done, "$key(", ")")
            "√" -> apply(done, "√(", ")")
            "x²" -> carry(done, "^2")
            "1/x" -> if (done) { expr = "1÷Ans"; result = null } else expr = "1÷($expr)"
            "x!" -> carry(done, "!")
            "%" -> carry(done, "%")
            else -> {
                // Digits, point, π, e, brackets and Ans start a new sum after "=".
                expr = if (done) key else expr + key
                result = null
            }
        }
    }

    /** sin, √ … wrap the last answer after "=", otherwise open a bracket. */
    private fun apply(done: Boolean, open: String, close: String) {
        expr = if (done) "${open}Ans$close" else expr + open
        result = null
    }

    /** x², x!, % act on what's there (or on the last answer). */
    private fun carry(done: Boolean, suffix: String) {
        expr = (if (done) "Ans" else expr) + suffix
        result = null
    }

    private fun equals() {
        if (expr.isBlank()) return
        try {
            val v = eval(expr)
            ans = v
            lastExpr = expr
            result = formatNumber(BigDecimal(v))
            CalcHistory.add(expr, result!!)
        } catch (e: Exception) {
            error = (e as? Formula.Error)?.code?.let { if (it == "#DIV/0!") "Can't divide by zero" else "Error" } ?: "Error"
        }
    }

    /** Replaces the sum with an edited or past one and works it out. */
    fun load(text: String) {
        error = null
        expr = text
        result = null
        equals()
    }

    /** Removes the last thing typed: a digit, or a whole "sin(" / "Ans". */
    private fun dropToken(s: String): String {
        if (s.endsWith("Ans")) return s.dropLast(3)
        if (s.endsWith("(")) {
            val name = s.dropLast(1).takeLastWhile { it.isLetter() }
            if (name.isNotEmpty()) return s.dropLast(name.length + 1)
        }
        return s.dropLast(1)
    }
}

@Composable
internal fun ColumnScope.ScientificPad(calc: SciCalc) {
    var history by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<String?>(null) }
    if (history) HistoryDialog(onDismiss = { history = false }) { e -> history = false; edit = e.expr }
    edit?.let { start ->
        EditCalcDialog(start, calc.degrees, onDismiss = { edit = null }) { text, _ -> edit = null; calc.load(text) }
    }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (landscape) {
        // Sideways there isn't room above the keys, so the display sits beside them.
        Row(Modifier.weight(1f).fillMaxWidth()) {
            SciDisplay(calc, Modifier.weight(0.36f).fillMaxHeight(), onHistory = { history = true }, onEdit = { edit = it })
            SciKeypad(calc, Modifier.weight(0.64f).fillMaxHeight(), fillHeight = true)
        }
    } else {
        SciDisplay(calc, Modifier.fillMaxWidth().weight(1f), onHistory = { history = true }, onEdit = { edit = it })
        SciKeypad(calc, Modifier.fillMaxWidth(), fillHeight = false)
    }
}

@Composable
private fun SciDisplay(calc: SciCalc, modifier: Modifier, onHistory: () -> Unit, onEdit: (String) -> Unit) {
    Column(
        modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.End,
    ) {
        CalcTools(onHistory = onHistory)
        Spacer(Modifier.weight(1f))
        Text(
            if (calc.degrees) "DEG" else "RAD",
            color = KeyOp,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
        )
        val result = calc.result
        if (result != null) {
            Text(calc.lastExpr, color = KeyFn, fontSize = 22.sp, textAlign = TextAlign.End, maxLines = 2, modifier = Modifier.clickable { onEdit(calc.lastExpr) })
            FitText(result, max = 72f)
        } else {
            val shown = calc.expr.ifEmpty { "0" }
            Text(
                shown,
                color = Color.White,
                fontSize = when {
                    shown.length > 28 -> 26.sp
                    shown.length > 14 -> 34.sp
                    else -> 52.sp
                },
                lineHeight = if (shown.length > 14) 40.sp else 58.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.End,
                maxLines = 3,
                // Tap the sum to change it.
                modifier = Modifier.clickable(enabled = calc.expr.isNotEmpty()) { onEdit(calc.expr) },
            )
            Text(calc.error ?: calc.preview?.let { "= $it" } ?: " ", color = if (calc.error != null) KeyOp else KeyFn, fontSize = 24.sp)
        }
    }
}

@Composable
private fun SciKeypad(calc: SciCalc, modifier: Modifier, fillHeight: Boolean) {
    val haptics = LocalHapticFeedback.current
    val heightLimit = keypadHeightLimit()
    BoxWithConstraints(
        modifier.padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val gap = 10.dp
        val w = (maxWidth - gap * 4) / 5
        // Sideways, the keys share the height that is left; upright, they keep to the usual limit.
        val room = if (fillHeight) maxHeight else heightLimit
        val h = minOf(w * 0.74f, (room - gap * (SciKeys.size - 1)) / SciKeys.size)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            SciKeys.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { k ->
                        val label = if (k == "Deg") (if (calc.degrees) "Deg" else "Rad") else k
                        SciKey(label, w, h) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            calc.press(label)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SciKey(key: String, width: Dp, height: Dp, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val (up, down, fg) = when {
        key in setOf("÷", "×", "−", "+", "=") -> Triple(KeyOp, KeyOpDown, Color.White)
        key == "AC" || key == "⌫" -> Triple(KeyFn, KeyFnDown, Color.Black)
        key == "." || (key.length == 1 && key[0].isDigit()) -> Triple(KeyDigit, KeyDigitDown, Color.White)
        else -> Triple(KeySci, KeySciDown, Color.White)
    }
    val bg by animateColorAsState(if (pressed) down else up, tween(if (pressed) 0 else 300), label = "sciKey")
    Box(
        Modifier
            .size(width, height)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val digit = key.length == 1 && (key[0].isDigit() || key[0] in "÷×−+=.")
        // Smaller keys (sideways) get smaller labels so nothing is cut off.
        val cap = height.value * if (digit) 0.5f else 0.4f
        Text(key, color = fg, fontSize = minOf(if (digit) 26f else 18f, cap).sp, maxLines = 1)
    }
}

// ---------------------------------------------------------------- finance

private val money: NumberFormat = NumberFormat.getNumberInstance().apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}

private fun num(s: String) = s.replace(",", "").toDoubleOrNull()

/** Loan EMI, SIP, compound interest and GST, worked out as you type. */
@Composable
internal fun ColumnScope.FinancePad() {
    var tool by rememberSaveable { mutableStateOf(0) }
    Column(
        Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Chips(listOf("Loan EMI", "SIP", "Compound", "GST"), tool) { tool = it }
        when (tool) {
            0 -> LoanEmi()
            1 -> Sip()
            2 -> Compound()
            else -> Gst()
        }
    }
}

@Composable
private fun LoanEmi() {
    var principal by rememberSaveable { mutableStateOf("500000") }
    var rate by rememberSaveable { mutableStateOf("9") }
    var years by rememberSaveable { mutableStateOf("5") }
    Field("Loan amount", principal) { principal = it }
    Field("Interest rate (% per year)", rate) { rate = it }
    Field("Tenure (years)", years) { years = it }
    val p = num(principal); val r = num(rate); val y = num(years)
    if (p == null || r == null || y == null || y <= 0) return Hint()
    val n = Math.round(y * 12).toInt().coerceAtLeast(1)
    val i = r / 12 / 100
    val emi = if (i == 0.0) p / n else p * i * (1 + i).pow(n) / ((1 + i).pow(n) - 1)
    val total = emi * n
    Results(
        "Monthly EMI" to emi,
        listOf("Total interest" to total - p, "Total payment" to total, "Number of EMIs" to n.toDouble()),
    )
}

@Composable
private fun Sip() {
    var monthly by rememberSaveable { mutableStateOf("5000") }
    var rate by rememberSaveable { mutableStateOf("12") }
    var years by rememberSaveable { mutableStateOf("10") }
    Field("Monthly investment", monthly) { monthly = it }
    Field("Expected return (% per year)", rate) { rate = it }
    Field("Time period (years)", years) { years = it }
    val m = num(monthly); val r = num(rate); val y = num(years)
    if (m == null || r == null || y == null || y <= 0) return Hint()
    val n = Math.round(y * 12).toInt().coerceAtLeast(1)
    val i = r / 12 / 100
    // Paid at the start of each month, the usual way SIP calculators count it.
    val value = if (i == 0.0) m * n else m * ((1 + i).pow(n) - 1) / i * (1 + i)
    val invested = m * n
    Results("Maturity value" to value, listOf("Invested" to invested, "Estimated returns" to value - invested))
}

@Composable
private fun Compound() {
    var principal by rememberSaveable { mutableStateOf("100000") }
    var rate by rememberSaveable { mutableStateOf("7") }
    var years by rememberSaveable { mutableStateOf("5") }
    var freq by rememberSaveable { mutableStateOf(2) }
    Field("Amount invested", principal) { principal = it }
    Field("Interest rate (% per year)", rate) { rate = it }
    Field("Time period (years)", years) { years = it }
    Chips(listOf("Yearly", "Half-yearly", "Quarterly", "Monthly"), freq) { freq = it }
    val p = num(principal); val r = num(rate); val y = num(years)
    if (p == null || r == null || y == null || y < 0) return Hint()
    val k = intArrayOf(1, 2, 4, 12)[freq]
    val amount = p * (1 + r / 100 / k).pow(k * y)
    Results("Maturity amount" to amount, listOf("Interest earned" to amount - p, "Amount invested" to p))
}

@Composable
private fun Gst() {
    var amount by rememberSaveable { mutableStateOf("1000") }
    var rate by rememberSaveable { mutableStateOf("18") }
    var remove by rememberSaveable { mutableStateOf(0) }
    Field("Amount", amount) { amount = it }
    Field("GST rate (%)", rate) { rate = it }
    Chips(listOf("5%", "12%", "18%", "28%"), listOf("5", "12", "18", "28").indexOf(rate)) { rate = listOf("5", "12", "18", "28")[it] }
    Chips(listOf("Add GST", "Remove GST"), remove) { remove = it }
    val a = num(amount); val r = num(rate)
    if (a == null || r == null) return Hint()
    val (net, tax) = if (remove == 0) a to a * r / 100 else (a / (1 + r / 100)).let { it to a - it }
    Results(
        (if (remove == 0) "Total with GST" else "Amount before GST") to (if (remove == 0) net + tax else net),
        listOf("Net amount" to net, "GST" to tax, "CGST (half)" to tax / 2, "SGST (half)" to tax / 2),
    )
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, color = KeyFn, fontSize = 13.sp)
        BasicTextField(
            value,
            { v -> onChange(v.filter { it.isDigit() || it == '.' }.take(15)) },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 24.sp),
            cursorBrush = SolidColor(KeyOp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

@Composable
private fun Chips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, label ->
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (i == selected) KeyOp else Panel)
                    .clickable { onSelect(i) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = Color.White, fontSize = 13.sp, fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Hint() {
    Text("Fill in every box to see the result.", color = KeyFn, fontSize = 14.sp)
}

@Composable
private fun Results(main: Pair<String, Double>, rows: List<Pair<String, Double>>) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(18.dp),
    ) {
        Text(main.first, color = KeyFn, fontSize = 14.sp)
        Text(money.format(main.second), color = KeyOp, fontSize = 36.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(10.dp))
        rows.forEach { (label, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(label, color = KeyFn, fontSize = 15.sp, modifier = Modifier.weight(1f))
                val whole = label.startsWith("Number")
                Text(if (whole) v.toLong().toString() else money.format(v), color = Color.White, fontSize = 15.sp)
            }
        }
    }
}
