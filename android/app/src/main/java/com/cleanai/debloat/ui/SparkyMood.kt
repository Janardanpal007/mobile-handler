package com.cleanai.debloat.ui

/**
 * What Sparky is feeling. The first four follow the phone's clock; the rest react to what the app is doing.
 * Based on the "Sparky Flow" starter ChatGPT made for this app (time states and reaction priority).
 */
enum class SparkyMood { MORNING, AFTERNOON, EVENING, NIGHT, THINKING, HAPPY, OOPS }

/** 05–11 morning, 12–16 afternoon, 17–20 evening, 21–04 night. */
fun timeMood(hour: Int): SparkyMood = when (hour) {
    in 5..11 -> SparkyMood.MORNING
    in 12..16 -> SparkyMood.AFTERNOON
    in 17..20 -> SparkyMood.EVENING
    else -> SparkyMood.NIGHT
}

fun greeting(hour: Int): String = when (timeMood(hour)) {
    SparkyMood.MORNING -> "Good morning"
    SparkyMood.AFTERNOON -> "Good afternoon"
    SparkyMood.EVENING -> "Good evening"
    else -> "Good night"
}

/**
 * The mood to show. A short reaction wins (an error over a success), then "thinking" while work runs,
 * then the time of day.
 */
fun sparkyMood(reaction: SparkyMood?, working: Boolean, hour: Int): SparkyMood =
    reaction ?: if (working) SparkyMood.THINKING else timeMood(hour)

/** Which reaction to keep when a new one arrives while another is still showing: an error is never hidden by a success. */
fun nextReaction(current: SparkyMood?, incoming: SparkyMood): SparkyMood =
    if (current == SparkyMood.OOPS && incoming == SparkyMood.HAPPY) current else incoming
