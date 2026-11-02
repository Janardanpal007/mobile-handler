package com.cleanai.debloat.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.sin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanai.debloat.R
import com.cleanai.debloat.data.ShizukuShell
import com.cleanai.debloat.features.Feature
import com.cleanai.debloat.features.Features
import java.time.LocalTime

/** Home: a dashboard of every feature, built from [Features.all]. */
@Composable
fun HomeScreen(
    name: String,
    shizuku: ShizukuShell.State,
    onFeature: (Feature) -> Unit,
    onAssistant: () -> Unit,
    onSettings: () -> Unit,
    onFiles: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        GlowBackground()
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(2) }) { Box(Modifier.enter(0)) { Header(onSettings) } }
            item(span = { GridItemSpan(2) }) { Box(Modifier.enter(1)) { GreetingCard(name, shizuku) } }
            item(span = { GridItemSpan(2) }) {
                Row(Modifier.padding(top = 10.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Features", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${Features.all.size} tools", color = TextSecondary, fontSize = 13.sp)
                }
            }
            itemsIndexed(Features.all, key = { _, f -> f.id }) { i, f -> Box(Modifier.enter(i + 2)) { FeatureTile(f) { onFeature(f) } } }
            item(span = { GridItemSpan(2) }) {
                Text(
                    BUILT_BY,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
        BottomBar(
            modifier = Modifier
                .align(Alignment.BottomCenter),
            onFiles = onFiles,
            onAssistant = onAssistant,
            onSettings = onSettings,
        )
    }
}

@Composable
private fun Header(onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        AppMark(Modifier.size(40.dp))
        Spacer(Modifier.width(10.dp))
        Text("Mobile Handler", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        ThemeButton()
        Spacer(Modifier.width(8.dp))
        RoundIconButton(Icons.Filled.Settings, "Settings", onSettings)
    }
}

/** Sun / moon button: switches between light and dark theme. */
@Composable
private fun ThemeButton() {
    val ctx = LocalContext.current
    Box(
        Modifier.bouncy { AppTheme.set(ctx, !AppTheme.dark) }
            .size(44.dp)
            .clip(CircleShape)
            .background(CardHi)
            .border(1.dp, Line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(if (AppTheme.dark) R.drawable.ic_sun else R.drawable.ic_moon),
            contentDescription = if (AppTheme.dark) "Light theme" else "Dark theme",
            tint = TextPrimary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** The dark "card" from the HaHo kit, holding the greeting and the Shizuku status. */
@Composable
private fun GreetingCard(name: String, shizuku: ShizukuShell.State) {
    val hour = LocalTime.now().hour
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else -> "Good night"
    }
    val wave = if (hour in 5..20) "👋" else "🌙"
    // Tapping Sparky makes it flare up: the whole card shakes and glows while the flames run.
    val shock = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val zap = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            shock.snapTo(0f)
            shock.animateTo(1f, tween(850, easing = LinearEasing))
            shock.snapTo(0f)
        }
        Unit
    }
    val s = shock.value
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = if (s > 0f) sin(s * 48f) * (1f - s) * 7.dp.toPx() else 0f }
            .clip(RoundedCornerShape(28.dp))
            .background(Charcoal)
            .drawWithContent {
                drawContent()
                if (s > 0f) drawRect(Color(0xFFFFB066).copy(alpha = 0.3f * (1f - s) * if ((s * 14).toInt() % 2 == 0) 1f else 0.4f))
            }
            .padding(20.dp),
    ) {
        // Greeting on the left; Sparky on the right, centred top to bottom.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (name.isBlank()) "$greeting $wave" else "$greeting, $name $wave", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text("What should I handle for you?", color = Color.White, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
            }
            Sparky(Modifier.size(132.dp), shock = s, onTap = zap)
        }
        // Under the row, the full card width keeps the status on one line.
        Spacer(Modifier.height(10.dp))
        StatusChip(shizukuLabel(shizuku), shizukuDot(shizuku))
    }
}

@Composable
private fun FeatureTile(f: Feature, onClick: () -> Unit) {
    // Tapping a tool makes its icon pop up out of the card in 3D (lift, tilt, shadow), then opens it.
    val pop = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.bouncy {
            if (pop.isRunning) return@bouncy
            scope.launch {
                pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
                onClick()
                delay(400)
                pop.snapTo(0f)
            }
        }
            .fillMaxWidth()
            .aspectRatio(1.15f)
            .clip(RoundedCornerShape(26.dp))
            .background(CardHi)
            .border(1.dp, Line, RoundedCornerShape(26.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    val p = pop.value
                    scaleX = 1f + 0.32f * p; scaleY = 1f + 0.32f * p
                    rotationX = 16f * p; rotationY = -20f * p
                    translationY = -5.dp.toPx() * p
                    cameraDistance = 6f * density
                    shadowElevation = 14.dp.toPx() * p
                    shape = CircleShape
                    clip = true
                }
                .size(46.dp)
                .background(f.tint)
                // Glossy highlight so the icon always reads as a little 3D ball.
                .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent), center = Offset(30f, 26f), radius = 90f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(f.icon), contentDescription = null, tint = Ink, modifier = Modifier.size(24.dp))
        }
        Column {
            Text(f.title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(f.subtitle, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** HaHo floating pill bar: Home · dark centre button (Assistant) · Settings. */
@Composable
private fun BottomBar(modifier: Modifier, onFiles: () -> Unit, onAssistant: () -> Unit, onSettings: () -> Unit) {
    // Full-width bar: nothing from the grid shows beside it, just the three buttons.
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(CardHi)
            .border(1.dp, Line, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarItem(Modifier.weight(1f), "My Files", onClick = onFiles) {
                Icon(painterResource(R.drawable.ic_folder), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(26.dp))
            }
            // The assistant: the same revolving orb as in the chat, like Siri.
            Box(
                Modifier.bouncy(onClick = onAssistant)
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(Charcoal),
                    contentAlignment = Alignment.Center,
                ) { Orb(Modifier.size(60.dp), active = true) }
            }
            BarItem(Modifier.weight(1f), "Settings", onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(26.dp))
            }
        }
    }
}

@Composable
private fun BarItem(modifier: Modifier, label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Column(
        modifier.bouncy(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Text(
            label,
            color = TextSecondary,
            fontSize = 13.sp,
        )
    }
}
