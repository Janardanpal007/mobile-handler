package com.cleanai.debloat.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.composed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.delay

/** The dark colour the app opens and closes on (same as the launcher icon). */
val Night = Color(0xFF141B18)

// Critically damped: smooth, and never springs past full size.
private val Soft = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)

/**
 * CRED-style tap: the whole element sinks a little while pressed and springs back on release,
 * instead of a ripple. Put it first in the modifier chain so the background and border scale too.
 */
fun Modifier.bouncy(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    // No overshoot on release: the element settles back without popping out bigger.
    val s by animateFloatAsState(
        if (pressed && enabled) 0.95f else 1f,
        spring(dampingRatio = 1f, stiffness = Spring.StiffnessHigh),
        label = "press",
    )
    graphicsLayer { scaleX = s; scaleY = s }
        .clickable(source, indication = null, enabled = enabled, onClick = onClick)
}

/** Places that already played their rise-in this session, so coming back to a screen doesn't replay it. */
private val entered = mutableSetOf<Int>()

/**
 * Rises and fades in the first time it appears, [index] steps after the elements before it,
 * so lists build up one by one.
 */
fun Modifier.enter(index: Int = 0): Modifier = composed {
    val key = currentCompositeKeyHash
    val p = remember { Animatable(if (key in entered) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (p.value >= 1f) return@LaunchedEffect
        entered += key
        delay(40L * index.coerceAtMost(12))
        p.animateTo(1f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow))
    }
    graphicsLayer {
        alpha = p.value.coerceIn(0f, 1f)
        translationY = (1f - p.value) * 36.dp.toPx()
        val s = 0.94f + 0.06f * p.value
        scaleX = s; scaleY = s
    }
}

/**
 * Screen changes with depth, CRED-style: the screen you're on sinks back and fades while the new one
 * settles in from just behind it. Nothing grows past full size, so there's no pop.
 */
fun AnimatedContentTransitionScope<String>.depthTransition(home: String): ContentTransform {
    val back = targetState == home
    return if (!back) {
        (scaleIn(Soft, initialScale = 0.92f) + fadeIn(tween(220))) togetherWith
            (scaleOut(Soft, targetScale = 0.94f) + fadeOut(tween(160)))
    } else {
        (scaleIn(Soft, initialScale = 0.94f) + fadeIn(tween(220))) togetherWith
            (scaleOut(Soft, targetScale = 0.92f) + fadeOut(tween(160)))
    }.apply { targetContentZIndex = if (back) 0f else 1f }
}

/**
 * Opening animation: the orb lights up on a dark screen, the name slides in, then everything
 * zooms through to the app. Calls [onDone] when finished.
 */
@Composable
fun IntroOverlay(onDone: () -> Unit) {
    val orbScale = remember { Animatable(0.4f) }
    val orbAlpha = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    val name = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        val a = async { orbScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }
        orbAlpha.animateTo(1f, tween(280))
        glow.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        name.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        a.await()
        delay(260)
        exit.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        onDone()
    }

    Box(
        Modifier.fillMaxSize().alpha(1f - exit.value).background(Night),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            // On the way out the whole mark rushes towards you, like flying into the app.
            modifier = Modifier.graphicsLayer {
                val s = 1f + exit.value * 2.2f
                scaleX = s; scaleY = s
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(220.dp).scale(0.6f + glow.value * 0.4f).alpha(glow.value * 0.85f)
                        .background(Brush.radialGradient(listOf(Coral.copy(alpha = 0.45f), Color.Transparent)), CircleShape),
                )
                Orb(
                    Modifier.size(120.dp).scale(orbScale.value).alpha(orbAlpha.value).clip(CircleShape),
                    active = true,
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "Mobile Handler",
                color = Color(0xFFEDEBE6),
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (8f - name.value * 7f).sp,
                modifier = Modifier.alpha(name.value).graphicsLayer { translationY = (1f - name.value) * 24.dp.toPx() },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "handles your phone",
                color = Color(0xFF8E9690),
                fontSize = 14.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.alpha(name.value * 0.9f),
            )
        }
    }
}

/** Closing animation: the app sinks back into the dark and the orb fades out. Calls [onDone] at the end. */
@Composable
fun OutroOverlay(onDone: () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        onDone()
    }
    Box(Modifier.fillMaxSize().alpha(p.value).background(Night), contentAlignment = Alignment.Center) {
        Orb(Modifier.size(90.dp).scale(1.2f - p.value * 0.4f).alpha(1f - p.value * 0.3f).clip(CircleShape))
    }
}
