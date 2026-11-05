package com.cleanai.debloat.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import com.cleanai.debloat.R
import com.cleanai.debloat.agent.Act
import com.cleanai.debloat.agent.AgentMsg
import com.cleanai.debloat.agent.AgentViewModel
import com.cleanai.debloat.agent.Choice
import com.cleanai.debloat.agent.ChoicesMsg
import com.cleanai.debloat.agent.ProgressMsg
import com.cleanai.debloat.agent.ResultMsg
import com.cleanai.debloat.agent.ReviewMsg
import com.cleanai.debloat.agent.UserMsg
import com.cleanai.debloat.data.Decision
import com.cleanai.debloat.data.FoundApp
import com.cleanai.debloat.data.Safety
import com.cleanai.debloat.data.ShizukuShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val CardShape = RoundedCornerShape(22.dp)

/** The assistant: chat with the agent. Opened from the orb in the bottom bar or a feature tile. */
@Composable
fun ChatScreen(vm: AgentViewModel, onBack: () -> Unit, onSettings: () -> Unit) {
    val listState = rememberLazyListState()

    LaunchedEffect(vm.items.size, vm.typing) {
        val count = 1 + vm.items.size + if (vm.typing) 1 else 0
        listState.animateScrollToItem(count - 1)
    }

    // Sparky cheers when a task finishes cleanly and looks worried when part of it failed.
    val lastResult = vm.items.lastOrNull { it is ResultMsg } as? ResultMsg
    val firstResultId = remember { lastResult?.id }
    var reaction by remember { mutableStateOf<SparkyMood?>(null) }
    LaunchedEffect(lastResult?.id) {
        if (lastResult == null || lastResult.id == firstResultId) return@LaunchedEffect
        reaction = nextReaction(reaction, if (lastResult.lines.all { it.ok }) SparkyMood.HAPPY else SparkyMood.OOPS)
        delay(REACTION_MS)
        reaction = null
    }

    Box(Modifier.fillMaxSize()) {
        GlowBackground()
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding(),
        ) {
            ScreenTopBar(
                title = "Assistant",
                subtitle = shizukuLabel(vm.shizuku),
                onBack = onBack,
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoundIconButton(Icons.Filled.Delete, "Clear chat") { vm.clearChat() }
                        RoundIconButton(Icons.Filled.Settings, "Settings", onSettings)
                    }
                },
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "hero") { Hero(working = vm.typing || vm.busy, reaction = reaction) }
                items(vm.items, key = { it.id }) { item ->
                    // New messages fade in and the list glides to make room.
                    Box(Modifier.animateItem()) { when (item) {
                        is AgentMsg -> AgentBubble(item)
                        is UserMsg -> UserBubble(item.text)
                        is ChoicesMsg -> ChoiceList(item) { vm.onChoice(item.id, it) }
                        is ReviewMsg -> ReviewCard(item, vm.decisions, vm::setDecision)
                        is ProgressMsg -> ProgressCard(item)
                        is ResultMsg -> ResultCard(item)
                    } }
                }
                if (vm.typing) item(key = "typing") { TypingBubble() }
            }
            InputBar(enabled = !vm.busy, onSend = vm::onSend)
        }
    }
}

// ------------------------------------------------------------------ chrome

private const val REACTION_MS = 2_500L

@Composable
private fun Hero(working: Boolean, reaction: SparkyMood?) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SparkyPortrait(sparkyMood(reaction, working, rememberHour()), Modifier.size(120.dp), description = "Sparky")
        Text(
            "How can I help?",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Ask anything, or try \"clean my phone\", \"weather in Delhi\", \"12 x 8\"",
            color = TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun InputBar(enabled: Boolean, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val send = {
        if (text.isNotBlank()) {
            onSend(text)
            text = ""
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(CardHi)
            .border(1.dp, Line, RoundedCornerShape(30.dp))
            .padding(start = 4.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Message the assistant…", color = TextSecondary) },
            maxLines = 4,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = Coral,
            ),
        )
        val empty = text.isBlank()
        Box(
            Modifier.bouncy(enabled = enabled && !empty) { send() }
                .size(44.dp)
                .clip(CircleShape)
                .background(Charcoal)
                .alpha(if (enabled && !empty) 1f else 0.4f),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

// ------------------------------------------------------------------ bubbles

@Composable
private fun AgentBubble(msg: AgentMsg) {
    Row(verticalAlignment = Alignment.Top) {
        Orb(Modifier.size(26.dp))
        Spacer(Modifier.width(8.dp))
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 22.dp))
                .background(Card)
                .border(1.dp, Line, RoundedCornerShape(topStart = 6.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 22.dp))
                .padding(14.dp),
        ) {
            Text(msg.text, color = TextPrimary, fontSize = 15.sp, lineHeight = 21.sp)
            msg.steps.forEachIndexed { i, step ->
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Coral.copy(alpha = 0.14f))
                            .border(1.dp, Coral.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("${i + 1}", color = Coral, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(10.dp))
                    Text(step, color = TextPrimary.copy(alpha = 0.88f), fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 6.dp, bottomEnd = 22.dp, bottomStart = 22.dp))
                .background(Charcoal)
                .padding(horizontal = 16.dp, vertical = 11.dp),
        )
    }
}

@Composable
private fun TypingBubble() {
    val t = rememberInfiniteTransition(label = "typing")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Orb(Modifier.size(26.dp), active = true)
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(Card)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(3) { i ->
                val a by t.animateFloat(
                    0.25f, 1f,
                    infiniteRepeatable(tween(500, delayMillis = i * 150), RepeatMode.Reverse),
                    label = "dot$i",
                )
                Box(Modifier.size(7.dp).alpha(a).clip(CircleShape).background(Coral))
            }
        }
    }
}

@Composable
private fun ChoiceList(msg: ChoicesMsg, onPick: (Choice) -> Unit) {
    Column(
        Modifier.padding(start = 34.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        msg.choices.forEach { c ->
            val picked = msg.picked == c.label
            val used = msg.picked != null && !c.action.isLink
            val border = when {
                picked -> Coral
                c.recommended && !used -> Coral.copy(alpha = 0.7f)
                else -> Line
            }
            Row(
                Modifier.bouncy(enabled = !used || c.action.isLink) { onPick(c) }
                    .fillMaxWidth()
                    .alpha(if (used && !picked) 0.4f else 1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (c.recommended && !used) Coral.copy(alpha = 0.10f) else Card)
                    .border(1.dp, border, RoundedCornerShape(18.dp))

                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(c.label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    c.hint?.let { Text(it, color = TextSecondary, fontSize = 12.sp) }
                }
                if (c.recommended) RecommendedTag()
                if (c.action.isLink) Text("↗", color = TextSecondary, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun RecommendedTag(small: Boolean = false) {
    Text(
        if (small) "★" else "★ Recommended",
        color = Color.White,
        fontSize = if (small) 10.sp else 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Coral)
            .padding(horizontal = if (small) 5.dp else 8.dp, vertical = 2.dp),
    )
}

// ------------------------------------------------------------------ app review

@Composable
private fun ReviewCard(msg: ReviewMsg, decisions: Map<String, Decision>, onDecide: (String, Decision) -> Unit) {
    val accent = when (msg.apps.firstOrNull()?.info?.safety) {
        Safety.RECOMMENDED -> Coral
        Safety.OPTIONAL -> Blue
        else -> Amber
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Card)
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.35f)), CardShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(msg.title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(msg.subtitle, color = TextSecondary, fontSize = 12.sp)
            }
            Text("${msg.apps.size}", color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        msg.apps.forEach { app -> AppRow(app, decisions[app.pkg] ?: Decision.KEEP) { onDecide(app.pkg, it) } }
    }
}

@Composable
private fun AppRow(app: FoundApp, decision: Decision, onDecide: (Decision) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardHi)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            AppIcon(app.pkg)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(app.pkg, color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                Text(app.info.why, color = TextPrimary.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Card)
                .padding(3.dp),
        ) {
            Decision.entries.forEach { d ->
                val allowed = d in app.allowed
                val selected = decision == d
                val color = when (d) {
                    Decision.REMOVE -> Red
                    Decision.DISABLE -> Amber
                    Decision.KEEP -> Ok
                }
                Row(
                    Modifier.bouncy(enabled = allowed) { onDecide(d) }
                        .weight(1f)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (selected) color.copy(alpha = 0.22f) else Color.Transparent)
                        .border(1.dp, if (selected) color else Color.Transparent, RoundedCornerShape(11.dp))
                        .alpha(if (allowed) 1f else 0.3f)

                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!allowed) {
                        Icon(Icons.Filled.Lock, null, tint = TextSecondary, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        d.name.lowercase().replaceFirstChar { it.uppercase() },
                        color = if (selected) color else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                    if (app.recommended == d) {
                        Spacer(Modifier.width(4.dp))
                        RecommendedTag(small = true)
                    }
                }
            }
        }
        if (app.recommended == null) {
            Text(
                "Your call — remove it if you don't use it.",
                color = Blue.copy(alpha = 0.9f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp),
            )
        }
    }
}

@Composable
private fun AppIcon(pkg: String) {
    val ctx = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) {
            runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardHi),
        contentAlignment = Alignment.Center,
    ) {
        icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(34.dp)) }
    }
}

// ------------------------------------------------------------------ progress / results

@Composable
private fun ProgressCard(msg: ProgressMsg) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 34.dp)
            .clip(CardShape)
            .background(Card)
            .border(1.dp, Line, CardShape)
            .padding(14.dp),
    ) {
        Row {
            Text(msg.label, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("${msg.done}/${msg.total}", color = Coral, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { if (msg.total == 0) 1f else msg.done / msg.total.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = Coral,
            trackColor = CardHi,
        )
    }
}

@Composable
private fun ResultCard(msg: ResultMsg) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 34.dp)
            .clip(CardShape)
            .background(Card)
            .border(1.dp, Line, CardShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(msg.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        msg.lines.forEach { line ->
            Row {
                Text(if (line.ok) "✓" else "✕", color = if (line.ok) Ok else Red, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(line.text, color = TextPrimary.copy(alpha = 0.88f), fontSize = 14.sp)
            }
        }
    }
}

// ------------------------------------------------------------------ settings

@Composable
fun SettingsDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var key by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardHi,
        title = { Text("Online AI help (optional)", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "You don't need a key: I already search the web for help for your phone. Optionally add your own Gemini or Claude API key " +
                        "and I'll also write answers to any question in my own words. " +
                        "Only your question, your phone model / Android version and the bloatware names are sent — never your personal data.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    singleLine = true,
                    label = { Text("Gemini or Claude API key") },
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(key) }) { Text("Save", color = Coral) } },
        dismissButton = {
            TextButton(onClick = { onSave("") }) { Text("Remove key", color = TextSecondary) }
        },
    )
}
