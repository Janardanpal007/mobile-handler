package com.cleanai.debloat.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val FurLight = Color(0xFFFFC27E)
private val FurMid = Color(0xFFFF8838)
private val FurDark = Color(0xFFC94F17)
private val Cream = Color(0xFFFFF4E4)
private val CreamShade = Color(0xFFF1D9BC)
private val Paws = Color(0xFF5B3A2C)
private val Scarf = Color(0xFF5FCB8E)
private val ScarfDark = Color(0xFF2F9363)
private val Eye = Color(0xFF1E2521)
private val Blush = Color(0xFFFF9C8A)
private val FlameOuter = Color(0xFFFF4D1C)
private val FlameMid = Color(0xFFFFA22B)
private val FlameCore = Color(0xFFFFF2A8)

/**
 * Sparky, the app's own mascot: a little upright fire fox in a mint scarf, with a bushy tail whose tip
 * is a living flame. It breathes, blinks (sometimes a happy ^ ^ squint), tilts its head, shifts its
 * weight and swishes its tail; now and then the tail puffs out a few embers. When Home opens it waves
 * hello and chats, nodding along, and waves again every so often. [shock] (0..1, running once per tap)
 * makes it flare up: flames all around, ears ablaze, arms flung up and eyes squeezed shut.
 *
 * Everything is drawn on a 100 x 100 grid scaled to the canvas, so the numbers below read as percent.
 */
@Composable
fun Sparky(modifier: Modifier = Modifier, shock: Float = 0f, onTap: () -> Unit = {}) {
    val loop = rememberInfiniteTransition(label = "fox")
    val breathe by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    val sway by loop.animateFloat(-1f, 1f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sway")
    val lean by loop.animateFloat(-1f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "lean")
    // Flames never sit still: a fast flicker drives every flame's wobble.
    val flick by loop.animateFloat(0f, 2f * PI.toFloat(), infiniteRepeatable(tween(700, easing = LinearEasing)), label = "flick")
    val blink = remember { Animatable(0f) }
    val happy = remember { Animatable(0f) }
    val puff = remember { Animatable(0f) }
    val wave = remember { Animatable(0f) }
    val talk = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2200, 4200))
            // About one blink in three is a happy squint that it holds for a moment.
            if (Random.nextInt(3) == 0) {
                happy.animateTo(1f, tween(120)); delay(650); happy.animateTo(0f, tween(160))
            } else {
                blink.animateTo(1f, tween(70)); blink.animateTo(0f, tween(110))
            }
        }
    }
    // Left alone, the tail flame puffs out a few embers every few seconds.
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2800, 5200))
            puff.snapTo(0f)
            puff.animateTo(1f, tween(900, easing = LinearEasing))
            puff.snapTo(0f)
        }
    }
    // Hello: chat and wave when Home opens, then wave again every so often.
    LaunchedEffect(Unit) {
        // Wait for the app's opening animation to finish so the hello is seen.
        delay(2200)
        launch { talk.animateTo(1f, tween(1800, easing = LinearEasing)); talk.snapTo(0f) }
        while (true) {
            wave.snapTo(0f)
            wave.animateTo(1f, tween(1900, easing = LinearEasing))
            wave.snapTo(0f)
            delay(Random.nextLong(12000, 18000))
        }
    }

    Canvas(modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTap)) {
        val k = size.minDimension / 100f
        fun o(x: Float, y: Float) = Offset(x * k, y * k)
        fun sz(w: Float, h: Float) = Size(w * k, h * k)

        val zapping = shock > 0f && shock < 1f
        val heat = if (zapping) 1f - shock * 0.5f else 0f
        val jitter = if (zapping) (Random.nextFloat() - 0.5f) * 3f * (1f - shock) else 0f
        val chat = if (talk.value > 0f) abs(sin(talk.value * PI.toFloat() * 10f)) else 0f
        val cx = 44f + jitter
        val lift = breathe * 1.6f // the whole fox rises a little as it breathes in

        // Soft shadow on the "floor".
        drawOval(Color.Black.copy(alpha = 0.3f - breathe * 0.06f), topLeft = o(cx - 20f, 92f), size = sz(40f, 6f))

        var tailTip = Offset.Zero
        // Weight shifts from foot to foot: the fox leans a little around its feet.
        rotate(lean * 3f, pivot = o(cx, 92f)) {
            if (zapping) drawCircle(
                Brush.radialGradient(listOf(FlameMid.copy(alpha = 0.55f * heat), Color.Transparent), o(cx, 60f), 60f * k),
                radius = 60f * k, center = o(cx, 60f),
            )

            // Bushy tail behind the body, swishing; its tip is a flame.
            val swish = sway * 8f + if (zapping) sin(shock * 40f) * 12f else 0f
            rotate(swish, pivot = o(cx + 10f, 82f)) {
                val tail = Path().apply {
                    moveTo((cx + 8f) * k, 86f * k)
                    cubicTo((cx + 34f) * k, 88f * k, (cx + 42f) * k, 66f * k, (cx + 36f) * k, 50f * k)
                    cubicTo((cx + 30f) * k, 60f * k, (cx + 24f) * k, 72f * k, (cx + 8f) * k, 74f * k)
                    close()
                }
                drawPath(tail, Brush.linearGradient(listOf(FurDark, FurMid, FurLight), o(cx + 10f, 88f), o(cx + 36f, 52f)))
                tailTip = o(cx + 36f, 52f)
                flame(tailTip, (12f + 10f * heat) * k, 9f * k, flick, 0f)
            }

            // Legs and dark paws.
            for (s in listOf(-1f, 1f)) {
                drawLine(FurDark, o(cx + s * 7f, 78f - lift), o(cx + s * 8f, 90f), strokeWidth = 10f * k, cap = StrokeCap.Round)
                drawOval(Paws, topLeft = o(cx + s * 8f - 6.5f, 88f), size = sz(13f, 6.5f))
            }

            // Body.
            val bodyTop = 54f - lift
            drawOval(
                Brush.radialGradient(listOf(FurLight, FurMid, FurDark), o(cx - 6f, bodyTop + 6f), 30f * k),
                topLeft = o(cx - 15f, bodyTop), size = sz(30f, 32f),
            )
            // Cream tummy.
            drawOval(
                Brush.radialGradient(listOf(Cream, CreamShade), o(cx - 2f, bodyTop + 14f), 14f * k),
                topLeft = o(cx - 9f, bodyTop + 8f), size = sz(18f, 20f),
            )

            // Arms with dark paws. 0 degrees points straight down; the left arm waves.
            val p = wave.value
            val up = when { p <= 0f -> 0f; p < 0.18f -> p / 0.18f; p > 0.82f -> (1f - p) / 0.18f; else -> 1f }
            val wag = sin(p * PI.toFloat() * 9f) * 20f * up
            for (side in listOf(-1f, 1f)) {
                val deg = when {
                    zapping -> 150f + (Random.nextFloat() - 0.5f) * 40f
                    side < 0f -> 25f + 125f * up + wag
                    else -> 18f + lean * 4f
                }
                val rad = deg * PI.toFloat() / 180f
                val shoulder = o(cx + side * 12f, bodyTop + 7f)
                val hand = Offset(shoulder.x + side * sin(rad) * 15f * k, shoulder.y + cos(rad) * 15f * k)
                drawLine(FurDark, shoulder, hand, strokeWidth = 7.5f * k, cap = StrokeCap.Round)
                drawLine(FurMid, shoulder, hand, strokeWidth = 4f * k, cap = StrokeCap.Round)
                drawCircle(Paws, 4.2f * k, hand)
            }

            // Mint scarf, with its knot hanging down one side.
            drawRoundRect(
                Brush.verticalGradient(listOf(Scarf, ScarfDark), (bodyTop - 1f) * k, (bodyTop + 6f) * k),
                topLeft = o(cx - 15f, bodyTop - 1f), size = sz(30f, 7f), cornerRadius = CornerRadius(3.5f * k),
            )
            val knot = Path().apply {
                moveTo((cx + 5f) * k, (bodyTop + 4f) * k)
                lineTo((cx + 12f + sway * 1.5f) * k, (bodyTop + 15f) * k)
                lineTo((cx + 7f + sway * 1.5f) * k, (bodyTop + 16f) * k)
                lineTo((cx + 2f) * k, (bodyTop + 5f) * k)
                close()
            }
            drawPath(knot, ScarfDark)

            // Head: tilts gently, nods while talking.
            val tilt = lean * 3f + chat * 4f + if (zapping) (Random.nextFloat() - 0.5f) * 8f else 0f
            val headC = o(cx, 36f - lift * 1.2f)
            rotate(tilt, pivot = o(cx, bodyTop)) {
                // Pointed ears with cream insides; while flaring the tips catch fire.
                for (side in listOf(-1f, 1f)) {
                    val deg = side * 18f + if (side > 0f) sway * 4f else 0f
                    val base = Offset(headC.x + side * 12f * k, headC.y - 12f * k)
                    rotate(deg, pivot = base) {
                        val ear = Path().apply {
                            moveTo(base.x - 9f * k, base.y + 4f * k)
                            quadraticTo(base.x - 4f * k, base.y - 14f * k, base.x, base.y - 22f * k)
                            quadraticTo(base.x + 4f * k, base.y - 14f * k, base.x + 9f * k, base.y + 4f * k)
                            close()
                        }
                        drawPath(ear, Brush.verticalGradient(listOf(FurDark, FurMid), base.y - 22f * k, base.y))
                        val inner = Path().apply {
                            moveTo(base.x - 5f * k, base.y + 2f * k)
                            quadraticTo(base.x - 2f * k, base.y - 10f * k, base.x, base.y - 15f * k)
                            quadraticTo(base.x + 2f * k, base.y - 10f * k, base.x + 5f * k, base.y + 2f * k)
                            close()
                        }
                        drawPath(inner, Cream)
                        if (zapping) flame(Offset(base.x, base.y - 21f * k), 12f * heat * k, 6f * k, flick + side, 0f)
                    }
                }
                // Head shape: wide at the cheeks, lit from the top left.
                drawOval(
                    Brush.radialGradient(listOf(FurLight, FurMid, FurDark), Offset(headC.x - 8f * k, headC.y - 8f * k), 34f * k),
                    topLeft = Offset(headC.x - 23f * k, headC.y - 18f * k), size = sz(46f, 36f),
                )
                // Cream muzzle and cheek fluff.
                drawOval(
                    Brush.radialGradient(listOf(Cream, CreamShade), Offset(headC.x - 2f * k, headC.y + 6f * k), 18f * k),
                    topLeft = Offset(headC.x - 15f * k, headC.y - 1f * k), size = sz(30f, 17f),
                )
                // Shine.
                drawOval(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.6f), Color.Transparent), Offset(headC.x - 11f * k, headC.y - 11f * k), 8f * k),
                    topLeft = Offset(headC.x - 18f * k, headC.y - 16f * k), size = sz(14f, 10f),
                )

                // Eyes.
                val eyeY = headC.y - 2f * k
                for (s in listOf(-1f, 1f)) {
                    val e = Offset(headC.x + s * 9.5f * k, eyeY)
                    if (zapping) {
                        val d = 3.2f * k
                        val q = Path().apply {
                            moveTo(e.x - s * d, e.y - d); lineTo(e.x + s * d, e.y); lineTo(e.x - s * d, e.y + d)
                        }
                        drawPath(q, Eye, style = Stroke(width = 2.2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    } else if (happy.value > 0.5f || chat > 0.6f) {
                        // Happy squint: ^ ^ (also flashes while it chats, like a smile).
                        drawArc(
                            Eye, startAngle = 200f, sweepAngle = 140f, useCenter = false,
                            topLeft = Offset(e.x - 3.6f * k, e.y - 1.5f * k), size = sz(7.2f, 6f),
                            style = Stroke(width = 2f * k, cap = StrokeCap.Round),
                        )
                    } else {
                        val eh = 8f * k * (1f - blink.value * 0.9f)
                        drawOval(Eye, topLeft = Offset(e.x - 2.8f * k, e.y - eh / 2f), size = Size(5.6f * k, eh))
                        if (blink.value < 0.5f) drawCircle(Color.White, 1.3f * k, Offset(e.x - 1f * k, e.y - eh * 0.22f))
                    }
                }
                for (s in listOf(-1f, 1f)) drawOval(
                    Blush.copy(alpha = 0.55f),
                    topLeft = Offset(headC.x + s * 15f * k - 3f * k, eyeY + 5f * k), size = sz(6f, 3f),
                )
                // Nose and mouth.
                drawOval(Eye, topLeft = Offset(headC.x - 2.2f * k, headC.y + 3.5f * k), size = sz(4.4f, 3f))
                val m = Offset(headC.x, headC.y + 9f * k)
                if (zapping) drawOval(Eye, topLeft = Offset(m.x - 3f * k, m.y - 2f * k), size = sz(6f, 6f))
                else if (chat > 0.15f) {
                    val mh = (1.5f + 5f * chat) * k
                    drawOval(Eye, topLeft = Offset(m.x - 3.2f * k, m.y - 1f * k), size = Size(6.4f * k, mh))
                    drawOval(Blush, topLeft = Offset(m.x - 2f * k, m.y - 1f * k + mh * 0.55f), size = Size(4f * k, mh * 0.4f))
                } else drawArc(
                    Eye, startAngle = 20f, sweepAngle = 140f, useCenter = false,
                    topLeft = Offset(m.x - 4f * k, m.y - 4f * k), size = sz(8f, 6f),
                    style = Stroke(width = 1.8f * k, cap = StrokeCap.Round),
                )
            }
        }

        // Embers drifting up off the tail flame.
        if (puff.value > 0f) {
            for (i in 0 until 4) {
                val t = (puff.value * 1.3f - i * 0.12f).coerceIn(0f, 1f)
                if (t <= 0f || t >= 1f) continue
                val pos = Offset(tailTip.x + sin(t * 6f + i * 2f) * 5f * k, tailTip.y - 10f * k - t * 26f * k)
                drawCircle(Brush.radialGradient(listOf(FlameCore, FlameOuter.copy(alpha = 0f)), pos, 3f * k), 3f * k * (1f - t * 0.6f), pos, alpha = 1f - t)
            }
        }
        // Flare-up: tongues of fire burst out all around.
        if (zapping) {
            val centre = o(cx, 58f)
            for (i in 0 until 10) {
                val a = i * 36f + shock * 40f
                val rad = a * PI.toFloat() / 180f
                val from = Offset(centre.x + cos(rad) * 26f * k, centre.y + sin(rad) * 30f * k)
                val len = (12f + 10f * abs(sin(flick + i))) * heat * k
                flame(from, len, 8f * k, flick + i * 0.7f, a + 90f)
            }
        }
    }
}

/**
 * One flickering flame standing on [base], [height] tall and [width] wide, tilted by [angle] degrees.
 * [phase] wobbles the tip so neighbouring flames don't move together.
 */
private fun DrawScope.flame(base: Offset, height: Float, width: Float, phase: Float, angle: Float) {
    if (height <= 0f) return
    val wobble = sin(phase * 2f) * width * 0.35f
    val h = height * (0.9f + 0.1f * sin(phase * 3f))
    fun tongue(scale: Float): Path = Path().apply {
        val hw = width * scale / 2f
        val hh = h * scale
        moveTo(base.x - hw, base.y)
        cubicTo(base.x - hw * 1.1f, base.y - hh * 0.45f, base.x + wobble - hw * 0.3f, base.y - hh * 0.7f, base.x + wobble, base.y - hh)
        cubicTo(base.x + wobble + hw * 0.3f, base.y - hh * 0.7f, base.x + hw * 1.1f, base.y - hh * 0.45f, base.x + hw, base.y)
        quadraticTo(base.x, base.y + hw * 0.9f, base.x - hw, base.y)
        close()
    }
    rotate(angle, pivot = base) {
        drawCircle(Brush.radialGradient(listOf(FlameMid.copy(alpha = 0.45f), Color.Transparent), base, h), h, base)
        drawPath(tongue(1f), Brush.verticalGradient(listOf(FlameMid, FlameOuter), base.y - h, base.y))
        drawPath(tongue(0.55f), Brush.verticalGradient(listOf(FlameCore, FlameMid), base.y - h * 0.55f, base.y))
    }
}
