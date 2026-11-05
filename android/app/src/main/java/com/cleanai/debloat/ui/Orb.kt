package com.cleanai.debloat.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

/** The assistant's animated ring mark. [active] speeds it up while it's thinking. */
@Composable
fun Orb(modifier: Modifier, active: Boolean = false) {
    val t = rememberInfiniteTransition(label = "orb")
    val spin by t.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(if (active) 5000 else 14000, easing = LinearEasing)),
        label = "spin",
    )
    val pulse by t.animateFloat(
        0.92f, 1.06f,
        infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2f
        drawCircle(
            Brush.radialGradient(
                listOf(Accent.copy(alpha = 0.32f), Sage.copy(alpha = 0.10f), Color.Transparent),
                center = c, radius = r,
            ),
            radius = r, center = c,
        )
        repeat(7) { i ->
            val direction = if (i % 2 == 0) 1f else -0.7f
            rotate(spin * direction + i * 26f, pivot = c) {
                scale(scaleX = 1f, scaleY = 0.38f + 0.09f * i, pivot = c) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(
                                Color.Transparent, Accent.copy(alpha = 0.95f), Sage.copy(alpha = 0.25f),
                                Color.Transparent, Accent.copy(alpha = 0.6f), Color.Transparent,
                            ),
                            center = c,
                        ),
                        radius = r * (0.52f + i * 0.05f) * pulse,
                        center = c,
                        style = Stroke(width = (2.6f - i * 0.2f).dp.toPx()),
                    )
                }
            }
        }
        drawCircle(
            Brush.radialGradient(
                listOf(Accent.copy(alpha = 0.30f), Color.Transparent),
                center = c, radius = r * 0.32f,
            ),
            radius = r * 0.32f, center = c,
        )
    }
}

/** The app's mark, same as the launcher icon: the orb on a dark disc. */
@Composable
fun AppMark(modifier: Modifier) {
    Box(modifier.clip(CircleShape).background(Color(0xFF141B18)), contentAlignment = Alignment.Center) {
        Orb(Modifier.fillMaxSize(0.92f))
    }
}

/** Flat warm-grey background used by every screen. */
@Composable
fun GlowBackground() {
    Canvas(Modifier.fillMaxSize()) { drawRect(Bg) }
}
