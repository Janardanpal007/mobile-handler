package com.cleanai.debloat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanai.debloat.data.Answers
import com.cleanai.debloat.data.Currency
import com.cleanai.debloat.data.Privacy
import kotlinx.coroutines.launch

private val CalcKeys = listOf(
    listOf("C", "( )", "%", "÷"),
    listOf("7", "8", "9", "×"),
    listOf("4", "5", "6", "−"),
    listOf("1", "2", "3", "+"),
    listOf("⌫", "0", ".", "="),
)

private val MoneyKeys = listOf(
    listOf("7", "8", "9", "⌫"),
    listOf("4", "5", "6", "C"),
    listOf("1", "2", "3", "⇅"),
    listOf("00", "0", ".", "↻"),
)

/** Calculator with a second tab for live currency conversion. */
@Composable
fun CalculatorScreen(onBack: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .systemBarsPadding(),
    ) {
        ScreenTopBar("Calculator", onBack)
        Tabs(listOf("Calculator", "Currency"), tab) { tab = it }
        Spacer(Modifier.height(12.dp))
        if (tab == 0) MathPad() else CurrencyPad()
    }
}

@Composable
private fun Tabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(Card)
            .padding(4.dp),
    ) {
        labels.forEachIndexed { i, label ->
            Box(
                Modifier.bouncy { onSelect(i) }
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (i == selected) Charcoal else Color.Transparent)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (i == selected) Color.White else TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ColumnScope.MathPad() {
    var expr by rememberSaveable { mutableStateOf("") }
    val history = remember { mutableStateListOf<String>() }
    // Display uses ×, ÷, −; the parser wants *, /, -.
    fun plain(s: String) = s.replace('×', '*').replace('÷', '/').replace('−', '-')
    val preview = Answers.evaluate(plain(expr))?.let { Answers.pretty(it) }

    fun press(key: String) {
        val last = expr.lastOrNull()
        val isOp = { c: Char? -> c != null && c in "+−×÷%" }
        expr = when (key) {
            "C" -> ""
            "⌫" -> expr.dropLast(1)
            "=" -> preview?.also { history.add(0, "$expr = $it"); if (history.size > 3) history.removeAt(3) } ?: expr
            "( )" -> if (expr.count { it == '(' } > expr.count { it == ')' } && (last?.isDigit() == true || last == ')')) "$expr)" else "$expr("
            "+", "−", "×", "÷", "%" -> when {
                expr.isEmpty() && key == "−" -> "−"
                expr.isEmpty() -> expr
                isOp(last) -> expr.dropLast(1) + key
                else -> expr + key
            }
            else -> expr + key
        }
    }

    DisplayCard {
        history.reversed().forEach {
            Text(it, color = Color.White.copy(alpha = 0.5f), fontSize = 20.sp, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            expr.ifEmpty { "0" },
            color = Color.White,
            fontSize = when {
                expr.length > 16 -> 36.sp
                expr.length > 10 -> 48.sp
                else -> 64.sp
            },
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.End,
            lineHeight = 70.sp,
        )
        Text(
            if (preview != null && preview != expr) "= $preview" else " ",
            color = Coral,
            fontSize = 32.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
        )
    }
    Keypad(CalcKeys, ::press)
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
            ?: if (Privacy.blocked()) "🔒 Private mode is on. Live rates need the internet — turn it off in Settings."
            else "No internet — couldn't get rates"
    }
    LaunchedEffect(Unit) { load(false) }

    val rate = rates?.rate(from, to)
    val value = amount.toDoubleOrNull()
    val result = if (rate != null && value != null) Currency.money(value * rate) else "–"

    fun press(key: String) {
        when (key) {
            "C" -> amount = "0"
            "⌫" -> amount = amount.dropLast(1).ifEmpty { "0" }
            "⇅" -> { val f = from; from = to; to = f }
            "↻" -> load(true)
            "." -> if ('.' !in amount) amount += "."
            else -> if (amount.length < 14) amount = if (amount == "0") key.trimStart('0').ifEmpty { "0" } else amount + key
        }
    }

    DisplayCard {
        MoneyRow(from, amount, Color.White, rates) { from = it }
        Spacer(Modifier.height(18.dp))
        MoneyRow(to, result, Coral, rates) { to = it }
        Spacer(Modifier.height(14.dp))
        Text(
            rate?.let { "1 $from = ${Currency.money(it)} $to" } ?: " ",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 17.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End,
        )
        Text(status, color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
    }
    Keypad(MoneyKeys, ::press)
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
                    .background(Color.White.copy(alpha = 0.14f))
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
        Text(
            amount,
            color = color,
            fontSize = when {
                amount.length > 12 -> 26.sp
                amount.length > 8 -> 32.sp
                else -> 44.sp
            },
            softWrap = false,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
    }
}

/** Dark display card from the HaHo kit. */
@Composable
private fun ColumnScope.DisplayCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .weight(1f)
            .clip(RoundedCornerShape(28.dp))
            .background(Charcoal)
            .padding(22.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.End,
        content = content,
    )
}

@Composable
private fun Keypad(rows: List<List<String>>, onPress: (String) -> Unit) {
    Spacer(Modifier.height(14.dp))
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key -> CalcKey(key, Modifier.weight(1f)) { onPress(key) } }
            }
        }
    }
}

@Composable
private fun CalcKey(key: String, modifier: Modifier, onClick: () -> Unit) {
    val (bg, fg) = when (key) {
        "=" -> Coral to Color.White
        "+", "−", "×", "÷", "⇅", "↻" -> Charcoal to Color.White
        "C", "( )", "%", "⌫" -> Card to Coral
        else -> CardHi to TextPrimary
    }
    Box(
        modifier.bouncy(onClick = onClick)
            .aspectRatio(1.25f)
            .clip(RoundedCornerShape(22.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(key, color = fg, fontSize = 32.sp, fontWeight = FontWeight.Medium)
    }
}
