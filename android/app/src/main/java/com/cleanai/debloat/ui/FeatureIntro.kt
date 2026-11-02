package com.cleanai.debloat.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanai.debloat.features.Feature
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

/** Two-part headline per tool: a plain lead-in and a big italic word, like the CRED ads. */
private val headlines = mapOf(
    "assistant" to ("Ask for" to "help"),
    "cleaner" to ("Clean out" to "bloatware"),
    "adblock" to ("Block" to "ads & trackers"),
    "calculator" to ("Do the" to "maths"),
    "qr" to ("Scan any" to "QR code"),
    "docscan" to ("Scan pages" to "into PDFs"),
    "pdf" to ("Turn photos" to "into a PDF"),
    "photo" to ("Enhance" to "old photos"),
    "files" to ("All your" to "scans & PDFs"),
    "weather" to ("Check the" to "weather"),
    "restore" to ("Bring apps" to "back"),
    "about" to ("Your" to "privacy"),
)

/**
 * Plays when a tool opens: the tool's colour floods the screen from the middle, its glossy icon
 * springs in with a ring around it and the headline rises line by line; then the colour slides up
 * and away to show the tool. [onCovered] runs once the colour fills the screen, so the tool opens
 * hidden behind it. Tapping skips straight to the slide away. Calls [onDone] at the end.
 */
@Composable
fun FeatureIntro(f: Feature, onCovered: () -> Unit, onDone: () -> Unit) {
    val flood = remember { Animatable(0f) }
    val icon = remember { Animatable(0f) }
    val line1 = remember { Animatable(0f) }
    val line2 = remember { Animatable(0f) }
    val leave = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val (lead, big) = headlines[f.id] ?: ("Open" to f.title)

    suspend fun exit() {
        if (leave.isRunning || leave.value > 0f) return
        leave.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        onDone()
    }

    LaunchedEffect(f.id) {
        launch { flood.animateTo(1f, tween(360, easing = FastOutSlowInEasing)); onCovered() }
        delay(120)
        launch { icon.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
        delay(90)
        launch { line1.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
        delay(90)
        line2.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
        delay(380)
        exit()
    }

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .graphicsLayer { translationY = -leave.value * size.height }
            .clickable(remember { MutableInteractionSource() }, indication = null) { scope.launch { exit() } },
    ) {
        val full = hypot(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()) / 2f
        // The colour grows as a circle from the middle of the screen.
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(f.tint, radius = full * flood.value, center = center)
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center) {
                // A thin ring that turns as the icon lands.
                Canvas(Modifier.size(132.dp).graphicsLayer { alpha = icon.value.coerceIn(0f, 1f); rotationZ = 90f * icon.value }) {
                    drawCircle(Ink.copy(alpha = 0.18f), style = Stroke(width = 2.dp.toPx()))
                    drawCircle(Ink.copy(alpha = 0.5f), radius = 5.dp.toPx(), center = Offset(size.width / 2f, 0f))
                }
                Box(
                    Modifier.graphicsLayer {
                        val s = icon.value
                        scaleX = s; scaleY = s
                        rotationY = (1f - s) * -40f
                        cameraDistance = 6f * density
                        shadowElevation = 18.dp.toPx() * s.coerceIn(0f, 1f)
                        shape = CircleShape; clip = true
                    }
                        .size(88.dp)
                        .background(Color.White.copy(alpha = 0.55f))
                        .background(Brush.radialGradient(listOf(Color.White, Color.Transparent), center = Offset(60f, 50f), radius = 170f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(f.icon), contentDescription = null, tint = Ink, modifier = Modifier.size(42.dp))
                }
            }
            Spacer(Modifier.height(40.dp))
            Text(
                lead.uppercase(),
                color = Ink.copy(alpha = 0.75f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (6f - 3.5f * line1.value).sp,
                modifier = Modifier.graphicsLayer {
                    alpha = line1.value
                    translationY = (1f - line1.value) * 28.dp.toPx()
                },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                big,
                color = Ink,
                fontSize = 48.sp,
                lineHeight = 52.sp,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer {
                    alpha = line2.value
                    translationY = (1f - line2.value) * 44.dp.toPx()
                    rotationZ = (1f - line2.value) * -4f
                },
            )
        }
    }
}
