package com.cleanai.debloat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalcPreviewTest {
    private fun typed(vararg keys: String) = IosCalc().apply { keys.forEach { press(it) } }

    @Test
    fun `shows the answer while a sum is typed`() {
        assertEquals("15", typed("1", "2", "+", "3").preview)
        assertEquals("24", typed("1", "2", "+", "3", "×", "4").preview)
        assertEquals("2.5", typed("5", "÷", "2").preview)
    }

    @Test
    fun `shows nothing when there is nothing to work out`() {
        assertNull(typed("7").preview)
        assertNull(typed("7", "+").preview)
        assertNull(typed("7", "÷", "0").preview)
    }
}
