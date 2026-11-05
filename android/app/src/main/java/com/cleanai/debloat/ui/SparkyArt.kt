package com.cleanai.debloat.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cleanai.debloat.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Sparky's 12 animation frames (ChatGPT's "Sparky Flow" starter, background cut out so they work on light and dark).
 * 0 stand · 1 blink · 2–3 wave · 4–5 think · 6 idea · 7 cheer · 8 yawn · 9 asleep · 10 surprised · 11 oops
 */
private val FRAMES = intArrayOf(
    R.drawable.sparky_frame_00, R.drawable.sparky_frame_01, R.drawable.sparky_frame_02, R.drawable.sparky_frame_03,
    R.drawable.sparky_frame_04, R.drawable.sparky_frame_05, R.drawable.sparky_frame_06, R.drawable.sparky_frame_07,
    R.drawable.sparky_frame_08, R.drawable.sparky_frame_09, R.drawable.sparky_frame_10, R.drawable.sparky_frame_11,
)

/**
 * How a mood moves: [rest] is the still frame, [intro] plays once when the mood appears or Sparky is tapped,
 * [idle] plays now and then afterwards. [loop] keeps cycling the intro (only while work is running).
 */
private class Moves(val rest: Int, val intro: IntArray, val idle: IntArray = intArrayOf(), val loop: Boolean = false, val step: Long = 240)

private fun movesFor(mood: SparkyMood) = when (mood) {
    SparkyMood.MORNING -> Moves(rest = 2, intro = intArrayOf(0, 2, 3, 2, 3, 2), idle = intArrayOf(3, 2))
    SparkyMood.AFTERNOON -> Moves(rest = 0, intro = intArrayOf(2, 3, 2, 0), idle = intArrayOf(1, 0))
    SparkyMood.EVENING -> Moves(rest = 0, intro = intArrayOf(1, 0), idle = intArrayOf(1, 0))
    SparkyMood.NIGHT -> Moves(rest = 9, intro = intArrayOf(8, 8, 9), step = 600)
    SparkyMood.THINKING -> Moves(rest = 4, intro = intArrayOf(4, 4, 5, 5, 6, 6), loop = true, step = 300)
    SparkyMood.HAPPY -> Moves(rest = 7, intro = intArrayOf(7, 6, 7))
    SparkyMood.OOPS -> Moves(rest = 11, intro = intArrayOf(10, 10, 11))
}

private const val IDLE_EVERY_MS = 7_000L
private const val CLOCK_CHECK_MS = 60_000L

/** True when the user turned animations off in Android's accessibility settings. */
@Composable
fun reduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/** True while the screen is in front of the user; false once the app goes to the background. */
@Composable
private fun rememberResumed(): Boolean {
    val owner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return resumed
}

/** The phone's current hour, refreshed when the app comes back and once a minute while it is on screen. */
@Composable
fun rememberHour(): Int {
    val resumed = rememberResumed()
    var hour by remember { mutableIntStateOf(LocalTime.now().hour) }
    LaunchedEffect(resumed) {
        while (resumed) {
            hour = LocalTime.now().hour
            delay(CLOCK_CHECK_MS)
        }
    }
    return hour
}

/**
 * Sparky as a picture that acts out [mood]. It moves only while on screen and in front of the user, rests on a still
 * frame between short idle moves, and stays still when the phone asks for reduced motion or [animate] is false.
 * A tap gives a springy hop and replays the mood.
 */
@Composable
fun SparkyPortrait(
    mood: SparkyMood,
    modifier: Modifier = Modifier,
    description: String? = null,
    animate: Boolean = true,
    onTap: (() -> Unit)? = null,
) {
    val moves = remember(mood) { movesFor(mood) }
    val moving = animate && !reduceMotion() && rememberResumed()
    var frame by remember(mood) { mutableIntStateOf(moves.rest) }
    var replay by remember { mutableIntStateOf(0) }
    LaunchedEffect(mood, moving, replay) {
        frame = moves.rest
        if (!moving) return@LaunchedEffect
        suspend fun play(seq: IntArray) {
            for (f in seq) { frame = f; delay(moves.step) }
            frame = moves.rest
        }
        do play(moves.intro) while (moves.loop)
        while (moves.idle.isNotEmpty()) {
            delay(IDLE_EVERY_MS)
            play(moves.idle)
        }
    }
    val hop = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val tap = onTap?.let { act ->
        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClickLabel = description) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (moving) {
                replay++
                scope.launch {
                    hop.snapTo(1f)
                    hop.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 400f))
                }
            }
            act()
        }
    } ?: Modifier
    Image(
        painterResource(FRAMES[frame]),
        contentDescription = description,
        modifier = modifier
            .then(tap)
            .graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 1f)
                scaleY = 1f - 0.12f * hop.value
                scaleX = 1f + 0.08f * hop.value
            },
    )
}
