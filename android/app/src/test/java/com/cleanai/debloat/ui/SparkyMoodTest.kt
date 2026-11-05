package com.cleanai.debloat.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SparkyMoodTest {

    @Test
    fun `clock hours map to the right time of day at every boundary`() {
        val expected = mapOf(
            4 to SparkyMood.NIGHT, 5 to SparkyMood.MORNING, 11 to SparkyMood.MORNING,
            12 to SparkyMood.AFTERNOON, 16 to SparkyMood.AFTERNOON, 17 to SparkyMood.EVENING,
            20 to SparkyMood.EVENING, 21 to SparkyMood.NIGHT, 23 to SparkyMood.NIGHT, 0 to SparkyMood.NIGHT,
        )
        expected.forEach { (hour, mood) -> assertEquals("hour $hour", mood, timeMood(hour)) }
    }

    @Test
    fun `greeting follows the time of day`() {
        assertEquals("Good morning", greeting(5))
        assertEquals("Good afternoon", greeting(12))
        assertEquals("Good evening", greeting(20))
        assertEquals("Good night", greeting(21))
        assertEquals("Good night", greeting(4))
    }

    @Test
    fun `reaction beats thinking and thinking beats the clock`() {
        assertEquals(SparkyMood.OOPS, sparkyMood(SparkyMood.OOPS, working = true, hour = 9))
        assertEquals(SparkyMood.HAPPY, sparkyMood(SparkyMood.HAPPY, working = true, hour = 9))
        assertEquals(SparkyMood.THINKING, sparkyMood(null, working = true, hour = 23))
        assertEquals(SparkyMood.NIGHT, sparkyMood(null, working = false, hour = 23))
    }

    @Test
    fun `a success never hides an error that is still showing`() {
        assertEquals(SparkyMood.OOPS, nextReaction(SparkyMood.OOPS, SparkyMood.HAPPY))
        assertEquals(SparkyMood.OOPS, nextReaction(SparkyMood.HAPPY, SparkyMood.OOPS))
        assertEquals(SparkyMood.HAPPY, nextReaction(null, SparkyMood.HAPPY))
    }
}
