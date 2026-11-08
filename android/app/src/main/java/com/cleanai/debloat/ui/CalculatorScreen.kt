package com.cleanai.debloat.ui

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import android.app.Activity
import android.content.res.Configuration
import com.cleanai.debloat.overlay.FloatingCalcService
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.cleanai.debloat.data.Currency
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlinx.coroutines.launch

// iPhone calculator colours.
internal val KeyDigit = Color(0xFF333333)
internal val KeyDigitDown = Color(0xFF737373)
internal val KeyFn = Color(0xFFA5A5A5)
internal val KeyFnDown = Color(0xFFD9D9D9)
internal val KeyOp = Color(0xFFFF9F0A)
internal val KeyOpDown = Color(0xFFFCC78D)
private val SegmentBg = Color(0xFF1C1C1E)
private val SegmentOn = Color(0xFF636366)

private const val MAX_DIGITS = 15

/** A very long sum keeps its newest part on screen. */
private const val SUM_SHOWN = 90

/** Swipes that start this close to the screen's sides are Android's back gesture, not "delete a digit". */
private val EDGE_ZONE = 40.dp

private val CalcKeys = listOf(
    listOf("AC", "%", "⌫", "÷"),
    listOf("7", "8", "9", "×"),
    listOf("4", "5", "6", "−"),
    listOf("1", "2", "3", "+"),
    listOf("00", "0", ".", "="),
)

private val MoneyKeys = listOf(
    listOf("7", "8", "9", "⌫"),
    listOf("4", "5", "6", "AC"),
    listOf("1", "2", "3", "⇅"),
    listOf("00", "0", ".", "↻"),
)

/** Calculator state lives here so a half-typed sum survives rotating the phone or leaving the screen. */
internal object CalcMemory {
    val basic = IosCalc()
    val sci = SciCalc()

    /** Set by the assistant ("scientific calculator", "loan EMI") to open a particular tab. */
    @Volatile var requestedTab: Int? = null

    /** The 🕘 button in the top row opens the history of the Basic or Scientific calculator. */
    val historyOpen = mutableStateOf(false)
}

/** Height a keypad may use, so landscape keys shrink instead of running off the screen. */
@Composable
internal fun keypadHeightLimit(): Dp = (LocalConfiguration.current.screenHeightDp * 0.62f).dp

/**
 * iPhone-style calculator. Tabs: Basic, Scientific (also shown when Basic is turned sideways, like the iPhone),
 * Finance (loan EMI, SIP, compound interest, GST), live Currency conversion and Units (length, weight, temperature and more).
 */
@Composable
fun CalculatorScreen(onBack: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(CalcMemory.requestedTab ?: 0) }
    LaunchedEffect(Unit) { CalcMemory.requestedTab = null }
    val ctx = LocalContext.current
    remember { CalcHistory.init(ctx) }
    var askOverlay by remember { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    LightSystemBars()
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding(),
    ) {
        // Everything above the display fits in one slim row of icons, leaving the room for the sum.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RoundIcon("Back", onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Segments(TAB_ICONS, TAB_NAMES, tab, Modifier.weight(1f)) { tab = it }
            // Only the Basic and Scientific calculators keep a history.
            if (tab <= 1) RoundIcon("History", { CalcMemory.historyOpen.value = true }) { Text("🕘", fontSize = 16.sp) }
            // Opens the small calculator that floats over other apps.
            RoundIcon("Floating calculator", {
                if (FloatingCalcService.canShow(ctx)) FloatingCalcService.start(ctx) else askOverlay = true
            }) { Text("⧉", color = KeyOp, fontSize = 20.sp) }
        }
        if (askOverlay) {
            AlertDialog(
                onDismissRequest = { askOverlay = false },
                title = { Text("Floating calculator") },
                text = {
                    Text(
                        "To keep a calculator on top of other apps, Android needs \"Display over other apps\" for Mobile Handler.\n\n" +
                            "On the next screen, switch Mobile Handler on and come back. The calculator opens by itself.",
                    )
                },
                confirmButton = { TextButton(onClick = { askOverlay = false; FloatingCalcService.start(ctx) }) { Text("Open settings") } },
                dismissButton = { TextButton(onClick = { askOverlay = false }) { Text("Not now") } },
            )
        }
        when (tab) {
            0 -> if (landscape) ScientificPad(CalcMemory.sci) else MathPad()
            1 -> ScientificPad(CalcMemory.sci)
            2 -> FinancePad()
            3 -> CurrencyPad()
            else -> UnitsPad()
        }
    }
}

/** White status and navigation bar icons while the black calculator is showing. */
@Composable
private fun LightSystemBars() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(Unit) {
        val window = (view.context as Activity).window
        val bars = WindowCompat.getInsetsController(window, view)
        bars.isAppearanceLightStatusBars = false
        bars.isAppearanceLightNavigationBars = false
        onDispose {
            bars.isAppearanceLightStatusBars = !AppTheme.dark
            bars.isAppearanceLightNavigationBars = !AppTheme.dark
        }
    }
}

/** The calculator's modes as short symbols, with their names read out by TalkBack. */
private val TAB_ICONS = listOf("123", "√x", "EMI", "₹$", "cm")
private val TAB_NAMES = listOf("Basic", "Scientific", "Finance", "Currency", "Units")

/** A small round button in the calculator's top row. */
@Composable
private fun RoundIcon(name: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.bouncy(onClick = onClick).size(38.dp).clip(CircleShape).background(KeyDigit)
            .semantics { contentDescription = name; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** iOS segmented control. */
@Composable
private fun Segments(labels: List<String>, names: List<String>, selected: Int, modifier: Modifier, onSelect: (Int) -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SegmentBg)
            .padding(3.dp),
    ) {
        labels.forEachIndexed { i, label ->
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (i == selected) SegmentOn else Color.Transparent)
                    .clickable(onClickLabel = names[i]) { onSelect(i) }
                    .semantics { contentDescription = names[i] }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = Color.White, fontSize = 13.sp, fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, softWrap = false)
            }
        }
    }
}

/**
 * The calculator's brain, working like the iPhone's: × and ÷ before + and −,
 * the pressed operator stays lit until the next number, "=" again repeats the last step,
 * and AC turns into C once you start typing.
 */
internal class IosCalc {
    /** The number being typed, exactly as typed ("12.50"); null when showing a result. */
    var typed by mutableStateOf<String?>(null); private set
    /** The result on the display when nothing is being typed. */
    var current by mutableStateOf(BigDecimal.ZERO); private set
    var selectedOp by mutableStateOf<Char?>(null); private set
    var error by mutableStateOf(false); private set
    /** Grey line above the number: the sum being built, or the one just worked out. */
    var expression by mutableStateOf(""); private set

    private val nums = mutableListOf<BigDecimal>()
    private val ops = mutableListOf<Char>()
    private var repeatOp: Char? = null
    private var repeatArg: BigDecimal? = null

    val clearLabel get() = if (!error && typed != null && typed != "0" && typed != "-0") "C" else "AC"

    val display: String get() = when {
        error -> "Error"
        typed != null -> groupTyped(typed!!)
        else -> formatNumber(current)
    }

    private fun value(): BigDecimal = typed?.let { BigDecimal(it.removeSuffix(".").ifEmpty { "0" }) } ?: current

    /**
     * The answer so far while a sum is being typed: "12 + 3 × 4" shows 24 before "=" is pressed.
     * Null when there is nothing to work out yet (a single number, or right after an operator).
     */
    val preview: String? get() {
        if (typed == null || error || nums.isEmpty()) return null
        return runCatching { formatNumber(evaluate(nums + value(), ops)) }.getOrNull()
    }

    fun press(key: String) {
        when (key) {
            "AC" -> allClear()
            "C" -> clearEntry()
            "⌫" -> backspace()
            "%" -> percent()
            "=" -> equals()
            "+" -> op('+')
            "−" -> op('−')
            "×" -> op('×')
            "÷" -> op('÷')
            "00" -> { digit("0"); digit("0") }
            else -> digit(key)
        }
    }

    /** Swiping across the display removes the last digit, as on the iPhone. */
    fun backspace() {
        if (error) return allClear()
        val t = typed
        if (t != null) {
            typed = t.dropLast(1).takeUnless { it.isEmpty() || it == "-" } ?: "0"
        } else if (selectedOp != null && nums.isNotEmpty()) {
            // "12 + ⌫" goes back to typing 12.
            ops.removeAt(ops.lastIndex)
            typed = nums.removeAt(nums.lastIndex).stripTrailingZeros().toPlainString()
            selectedOp = null
            if (nums.isEmpty()) expression = ""
        } else return
        refreshExpression()
    }

    private fun digit(d: String) {
        if (error) allClear()
        val t = typed
        if (t == null) {
            typed = if (d == ".") "0." else d
            if (nums.isEmpty()) expression = ""
        } else if (d == ".") {
            if ('.' !in t) typed = "$t."
        } else if (t.count { it.isDigit() } < MAX_DIGITS) {
            typed = when (t) { "0" -> d; "-0" -> "-$d"; else -> t + d }
        }
        selectedOp = null
        refreshExpression()
    }

    private fun op(o: Char) {
        if (error) return
        if (selectedOp != null && typed == null && ops.isNotEmpty()) {
            ops[ops.lastIndex] = o
        } else {
            nums += value()
            ops += o
        }
        typed = null
        selectedOp = o
        // Shows the part already worked out: "2 + 3 ×" shows 3, "2 × 3 +" shows 6.
        safely {
            val before = ops.dropLast(1)
            current = if (o == '+' || o == '−') evaluate(nums, before)
            else {
                val start = before.indexOfLast { it == '+' || it == '−' } + 1
                evaluate(nums.subList(start, nums.size), before.subList(start, before.size))
            }
        }
        refreshExpression()
    }

    private fun equals() {
        if (error) return
        if (nums.isNotEmpty()) {
            val arg = value()
            val all = nums + arg
            repeatOp = ops.last()
            repeatArg = arg
            val text = describe(all, ops)
            safely { current = evaluate(all, ops) }
            expression = text
            nums.clear()
            ops.clear()
        } else {
            val o = repeatOp ?: return
            val arg = repeatArg ?: return
            val start = value()
            safely { current = evaluate(listOf(start, arg), listOf(o)) }
            expression = describe(listOf(start, arg), listOf(o))
        }
        typed = null
        selectedOp = null
        if (!error) CalcHistory.add(expression, display)
    }

    /** Shows an edited or past calculation: [text] on the grey line and its answer, ready to carry on from. */
    fun load(text: String, answer: BigDecimal) {
        allClear()
        current = answer
        expression = text
        CalcHistory.add(text, display)
    }

    /** "200 + 10 %" means 10 % of 200, like the iPhone; otherwise it divides by 100. */
    private fun percent() {
        if (error) return
        val v = value()
        val base = if (ops.isNotEmpty() && (ops.last() == '+' || ops.last() == '−')) nums.last() else null
        current = if (base != null) base.multiply(v).divide(BigDecimal(100), MathContext.DECIMAL64)
        else v.divide(BigDecimal(100), MathContext.DECIMAL64)
        typed = null
        selectedOp = null
        refreshExpression()
    }

    private fun clearEntry() {
        typed = null
        current = BigDecimal.ZERO
        selectedOp = ops.lastOrNull()
        refreshExpression()
    }

    private fun allClear() {
        typed = null
        current = BigDecimal.ZERO
        selectedOp = null
        error = false
        expression = ""
        nums.clear()
        ops.clear()
        repeatOp = null
        repeatArg = null
    }

    private fun refreshExpression() {
        if (nums.isEmpty()) return
        expression = describe(nums, ops) + (typed?.let { groupTyped(it) } ?: "")
    }

    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (e: ArithmeticException) {
            error = true
            nums.clear()
            ops.clear()
            typed = null
            selectedOp = null
        }
    }

    private fun describe(values: List<BigDecimal>, operators: List<Char>) = buildString {
        values.forEachIndexed { i, v ->
            append(formatNumber(v))
            operators.getOrNull(i)?.let { append(it) }
        }
    }

    companion object {
        /** × and ÷ first, then + and − from left to right. */
        fun evaluate(values: List<BigDecimal>, operators: List<Char>): BigDecimal {
            val terms = mutableListOf(values[0])
            val adds = mutableListOf<Char>()
            for (i in operators.indices) {
                val next = values.getOrNull(i + 1) ?: break
                when (val o = operators[i]) {
                    '×' -> terms[terms.lastIndex] = terms.last().multiply(next, MathContext.DECIMAL64)
                    '÷' -> {
                        if (next.signum() == 0) throw ArithmeticException("Division by zero")
                        terms[terms.lastIndex] = terms.last().divide(next, MathContext.DECIMAL64)
                    }
                    else -> { adds += o; terms += next }
                }
            }
            var total = terms[0]
            adds.forEachIndexed { i, o -> total = if (o == '+') total.add(terms[i + 1]) else total.subtract(terms[i + 1]) }
            return total
        }
    }
}

/** Up to nine digits with thousands commas; very large or tiny numbers switch to 1.2345e15 style. */
internal fun formatNumber(v: BigDecimal): String {
    if (v.signum() == 0) return "0"
    val intDigits = v.abs().setScale(0, RoundingMode.DOWN).toPlainString().length.let { if (v.abs() < BigDecimal.ONE) 1 else it }
    if (intDigits <= MAX_DIGITS) {
        val r = v.setScale(MAX_DIGITS - intDigits, RoundingMode.HALF_UP).stripTrailingZeros()
        // Rounding can carry over ("999,999,999.6"), so check the length again.
        if (r.signum() != 0 && r.precision() - r.scale() <= MAX_DIGITS) return groupTyped(r.toPlainString())
    }
    val r = v.round(MathContext(6, RoundingMode.HALF_UP)).stripTrailingZeros()
    val exp = r.precision() - r.scale() - 1
    val mantissa = r.movePointLeft(exp).stripTrailingZeros().toPlainString()
    return "${mantissa}e$exp"
}

/** "-1234567.50" -> "-1,234,567.50", keeping what was typed after the point. */
internal fun groupTyped(s: String): String {
    val sign = if (s.startsWith("-")) "-" else ""
    val body = s.removePrefix("-")
    val int = body.substringBefore('.')
    val rest = if ('.' in body) "." + body.substringAfter('.') else ""
    return sign + int.reversed().chunked(3).joinToString(",").reversed() + rest
}

@Composable
private fun ColumnScope.MathPad() {
    val calc = CalcMemory.basic
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val swipe = with(density) { 36.dp.toPx() }
    val direction = LocalLayoutDirection.current
    val edges = WindowInsets.systemGestures
    val leftEdge = maxOf(edges.getLeft(density, direction).toFloat(), with(density) { EDGE_ZONE.toPx() })
    val rightEdge = maxOf(edges.getRight(density, direction).toFloat(), with(density) { EDGE_ZONE.toPx() })
    var history by CalcMemory.historyOpen
    // Samsung-style editing: the sum on the display with a cursor, changed with the calculator's own keys.
    var editing by remember { mutableStateOf<SumEditor?>(null) }
    // The sum being typed, or the one just worked out, or the number on show.
    fun editStart() = calc.expression.ifEmpty { calc.display.takeIf { it != "0" && it != "Error" }.orEmpty() }.replace(",", "")

    Column(
        Modifier
            .fillMaxWidth()
            .weight(1f)
            .pointerInput(leftEdge, rightEdge) {
                var dragged = 0f
                var fromEdge = false
                detectHorizontalDragGestures(
                    // A swipe in from the side is Android's back gesture: leave the number alone.
                    onDragStart = { at -> dragged = 0f; fromEdge = at.x < leftEdge || at.x > size.width - rightEdge },
                    onHorizontalDrag = { _, dx ->
                        if (fromEdge) return@detectHorizontalDragGestures
                        dragged += dx
                        if (kotlin.math.abs(dragged) > swipe) {
                            dragged = 0f
                            editing?.backspace() ?: calc.backspace()
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                )
            }
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.End,
    ) {
        val ed = editing
        Spacer(Modifier.weight(1f))
        if (ed != null) {
            SumField(ed)
            val answer = ed.answer()
            Text(
                answer?.let { "= " + formatNumber(it) } ?: " ",
                color = KeyFn, fontSize = 34.sp, maxLines = 1, textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp),
            )
        } else {
            // Tap the sum or the number to put a cursor in it and change it.
            // The whole sum, over up to three lines and smaller as it grows, so nothing is hidden.
            val sum = calc.expression.let { if (it.length > SUM_SHOWN) "…" + it.takeLast(SUM_SHOWN) else it }
            Text(
                // Lines break after + − × ÷, never in the middle of a number.
                sum.replace(Regex("([+−×÷])"), "$1​"),
                color = KeyFn,
                fontSize = when { sum.length <= 22 -> 26.sp; sum.length <= 44 -> 22.sp; else -> 18.sp },
                lineHeight = when { sum.length <= 22 -> 32.sp; sum.length <= 44 -> 28.sp; else -> 23.sp },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().clickable(enabled = calc.expression.isNotEmpty()) { editing = SumEditor(editStart()) },
            )
            // A longer sum leaves less room, so the big number gets a little smaller to keep the answer line in view.
            val numberSize = when { sum.length <= 22 -> 88f; sum.length <= 44 -> 76f; else -> 62f }
            Box(Modifier.clickable(enabled = editStart().isNotEmpty()) { editing = SumEditor(editStart()) }) { FitText(calc.display, max = numberSize) }
            // The answer as you type, before "=" (the line keeps its place so the number doesn't jump).
            Text(
                calc.preview?.let { "= $it" } ?: " ",
                color = KeyFn, fontSize = 26.sp, maxLines = 1, softWrap = false, textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
    }
    // Back leaves editing without changing anything.
    androidx.activity.compose.BackHandler(enabled = editing != null) { editing = null }
    if (history) HistoryDialog(onDismiss = { history = false }) { e -> history = false; editing = SumEditor(e.expr) }
    Keypad(
        CalcKeys,
        label = {
            when {
                // C empties the sum; once it is empty the key turns into AC.
                editing != null && it == "AC" -> if (editing!!.text.isEmpty()) "AC" else "C"
                it == "AC" -> calc.clearLabel
                else -> it
            }
        },
        selected = { editing == null && it.length == 1 && it[0] == calc.selectedOp },
    ) { key ->
        val ed = editing
        if (ed == null) calc.press(key)
        else when (key) {
            // The keypad reports the label it shows.
            "C" -> ed.clear()
            "AC" -> { editing = null; calc.press("AC") }
            "⌫" -> ed.backspace()
            "=" -> {
                val answer = ed.answer()
                if (answer != null) { calc.load(CalcHistory.tidy(ed.text), answer); editing = null }
                else haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            else -> ed.insert(key)
        }
    }
}

/** A sum being edited: its text and where the cursor is. */
internal class SumEditor(start: String) {
    var text by mutableStateOf(start); private set
    var cursor by mutableStateOf(start.length)

    fun insert(s: String) {
        text = text.substring(0, cursor) + s + text.substring(cursor)
        cursor += s.length
    }

    fun backspace() {
        if (cursor == 0) return
        text = text.removeRange(cursor - 1, cursor)
        cursor--
    }

    fun clear() { text = ""; cursor = 0 }

    fun answer(): BigDecimal? = if (text.isBlank()) null else runCatching { CalcHistory.evaluate(text) }.getOrNull()
}

/** The sum in large type with a blinking cursor; tap anywhere in it to move the cursor there. */
@Composable
private fun SumField(ed: SumEditor) {
    var layout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    val blink by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(530, easing = { t: Float -> if (t < 0.5f) 0f else 1f }), RepeatMode.Reverse),
        label = "blink",
    )
    val shown = ed.text
    Text(
        shown.ifEmpty { "0" },
        color = if (shown.isEmpty()) KeyFn else Color.White,
        fontSize = when { shown.length > 24 -> 30.sp; shown.length > 12 -> 40.sp; else -> 56.sp },
        lineHeight = when { shown.length > 24 -> 36.sp; shown.length > 12 -> 48.sp; else -> 64.sp },
        fontWeight = FontWeight.Light,
        textAlign = TextAlign.End,
        maxLines = 4,
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures { pos -> layout?.let { ed.cursor = it.getOffsetForPosition(pos).coerceIn(0, ed.text.length) } }
            }
            .drawWithContent {
                drawContent()
                val l = layout ?: return@drawWithContent
                val r = l.getCursorRect(ed.cursor.coerceIn(0, l.layoutInput.text.length))
                drawLine(KeyOp.copy(alpha = blink), Offset(r.left, r.top + 4f), Offset(r.left, r.bottom - 4f), 3.dp.toPx())
            },
    )
}

/** The big display number, shrinking to fit the width like the iPhone's. */
@Composable
internal fun FitText(text: String, color: Color = Color.White, max: Float = 88f) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomEnd) {
        val fontScale = LocalDensity.current.fontScale
        // Rough width of each character in ems: digits are wide, commas and points narrow.
        val ems = text.sumOf { if (it == ',' || it == '.') 0.28 else 0.58 }.toFloat().coerceAtLeast(1f)
        val size = minOf(max, maxWidth.value / ems / fontScale)
        Text(
            text,
            color = color,
            fontSize = size.sp,
            lineHeight = (size * 1.15f).sp,
            fontWeight = FontWeight.Light,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun ColumnScope.CurrencyPad() {
    val scope = rememberCoroutineScope()
    var from by rememberSaveable { mutableStateOf("USD") }
    var to by rememberSaveable { mutableStateOf("INR") }
    var amount by rememberSaveable { mutableStateOf("1") }
    var rates by remember { mutableStateOf<Currency.Rates?>(null) }
    var status by remember { mutableStateOf("Getting today's rates…") }

    fun load(force: Boolean) = scope.launch {
        status = "Getting today's rates…"
        rates = Currency.rates(force)
        status = rates?.let { "Updated ${it.updatedText()}" }
            ?: "No internet — couldn't get rates"
    }
    LaunchedEffect(Unit) { load(false) }

    val rate = rates?.rate(from, to)
    val value = amount.toDoubleOrNull()
    val result = if (rate != null && value != null) Currency.money(value * rate) else "–"

    fun press(key: String) {
        when (key) {
            "AC" -> amount = "0"
            "⌫" -> amount = amount.dropLast(1).ifEmpty { "0" }
            "⇅" -> { val f = from; from = to; to = f }
            "↻" -> load(true)
            "." -> if ('.' !in amount) amount += "."
            else -> if (amount.length < 14) amount = if (amount == "0") key.trimStart('0').ifEmpty { "0" } else amount + key
        }
    }

    Column(
        Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.End,
    ) {
        MoneyRow(from, groupTyped(amount), Color.White, rates) { from = it }
        Spacer(Modifier.height(14.dp))
        MoneyRow(to, result, KeyOp, rates) { to = it }
        Spacer(Modifier.height(12.dp))
        Text(
            rate?.let { "1 $from = ${Currency.money(it)} $to" } ?: " ",
            color = KeyFn,
            fontSize = 16.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End,
        )
        Text(status, color = KeyFn.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
    }
    Keypad(MoneyKeys, label = { it }, selected = { false }, onPress = ::press)
}

/** One currency line: a tappable code that opens the picker, and its amount. */
@Composable
private fun MoneyRow(code: String, amount: String, color: Color, rates: Currency.Rates?, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box {
            Row(
                Modifier.bouncy { open = true }
                    .clip(RoundedCornerShape(50))
                    .background(KeyDigit)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(code, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("  ▾", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
            }
            DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 420.dp)) {
                val known = Currency.COMMON.filter { rates == null || rates.usd.containsKey(it.first) }
                known.forEach { (c, name) ->
                    DropdownMenuItem(
                        text = { Text("$c  ·  $name", fontSize = 17.sp) },
                        onClick = { onPick(c); open = false },
                    )
                }
            }
        }
        Box(Modifier.weight(1f).padding(start = 12.dp)) { FitText(amount, color, max = 48f) }
    }
}

/** Round iPhone keys; a lone "0" key spans two columns. */
@Composable
private fun Keypad(
    rows: List<List<String>>,
    label: (String) -> String,
    selected: (String) -> Boolean,
    onPress: (String) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val heightLimit = keypadHeightLimit()
    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val gap = 14.dp
        val key = minOf((maxWidth - gap * 3) / 4, (heightLimit - gap * (rows.size - 1)) / rows.size)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { k ->
                        val wide = row.size == 3 && k == "0"
                        CalcKey(
                            label(k),
                            width = if (wide) key * 2 + gap else key,
                            height = key,
                            selected = selected(k),
                        ) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPress(label(k))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalcKey(key: String, width: Dp, height: Dp, selected: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val isOp = key in setOf("÷", "×", "−", "+", "=", "⇅", "↻")
    val isFn = key in setOf("AC", "C", "%", "⌫")
    val (up, down, fg) = when {
        isOp && selected -> Triple(Color.White, Color.White, KeyOp)
        isOp -> Triple(KeyOp, KeyOpDown, Color.White)
        isFn -> Triple(KeyFn, KeyFnDown, Color.Black)
        else -> Triple(KeyDigit, KeyDigitDown, Color.White)
    }
    // Brightens instantly on touch and fades back on release, like the iPhone keys.
    val bg by animateColorAsState(if (pressed) down else up, tween(if (pressed) 0 else 300), label = "key")
    Box(
        Modifier
            .size(width, height)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(source, indication = null, onClick = onClick),
        contentAlignment = if (width > height) Alignment.CenterStart else Alignment.Center,
    ) {
        Text(
            key,
            color = fg,
            fontSize = when {
                isOp -> 40.sp
                isFn -> 30.sp
                else -> 36.sp
            },
            fontWeight = FontWeight.Normal,
            modifier = if (width > height) Modifier.padding(start = height * 0.36f) else Modifier,
        )
    }
}
