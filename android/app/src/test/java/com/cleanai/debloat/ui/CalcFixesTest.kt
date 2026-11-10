package com.cleanai.debloat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class CalcFixesTest {
    private fun basic(vararg keys: String) = IosCalc().apply { keys.forEach { press(it) } }
    private fun edited(text: String) = SumEditor(text).answer()?.let { formatNumber(it) }

    @Test
    fun `percent in edit mode matches the Basic keypad`() {
        listOf(
            "200+10%" to listOf("2", "0", "0", "+", "1", "0", "%", "="),
            "200−10%" to listOf("2", "0", "0", "−", "1", "0", "%", "="),
            "200×10%" to listOf("2", "0", "0", "×", "1", "0", "%", "="),
            "200÷10%" to listOf("2", "0", "0", "÷", "1", "0", "%", "="),
            "50%" to listOf("5", "0", "%"),
            "100+200+10%" to listOf("1", "0", "0", "+", "2", "0", "0", "+", "1", "0", "%", "="),
            "2×3+10%" to listOf("2", "×", "3", "+", "1", "0", "%", "="),
        ).forEach { (text, keys) -> assertEquals(text, basic(*keys.toTypedArray()).display, edited(text)) }
        assertEquals("220", edited("200+10%"))
        assertEquals("180", edited("200-10%"))
        assertEquals("20", edited("200×10%"))
        assertEquals("2,000", edited("200÷10%"))
        assertEquals("0.5", edited("50%"))
    }

    @Test
    fun `saved Basic sum keeps full precision`() {
        val calc = basic("1", "÷", "3", "=", "×", "3", "=")
        assertEquals("1", calc.display)
        assertEquals(0, CalcHistory.evaluate(calc.exact).compareTo(BigDecimal.ONE))
        assertEquals(0, SumEditor(calc.exact).answer()!!.compareTo(BigDecimal.ONE))
    }

    @Test
    fun `scientific history saves the Ans it used`() {
        assertEquals("5×2", CalcHistory.withAns("Ans×2", 5.0))
        assertEquals("2(5)", CalcHistory.withAns("2Ans", 5.0))
        assertEquals("(-3)^2", CalcHistory.withAns("Ans^2", -3.0))
        assertEquals("0.1+1", CalcHistory.withAns("Ans+1", 0.1))
        val big = 1.2345678901234567e300
        assertEquals(big, CalcHistory.evaluate(CalcHistory.withAns("Ans", big)).toDouble(), 0.0)
        val third = 1.0 / 3
        assertEquals(third * 3, CalcHistory.evaluate(CalcHistory.withAns("Ans×3", third)).toDouble(), 0.0)

        val sci = SciCalc().apply { listOf("5", "=", "×", "2", "=").forEach { press(it) } }
        assertEquals("10", sci.result)
        assertEquals("Ans×2", sci.lastExpr)
        assertEquals("5×2", CalcHistory.entries.first().expr)
    }

    @Test
    fun `loan tenure is capped instead of overflowing`() {
        assertEquals(60, tenureMonths(5.0))
        assertEquals(1, tenureMonths(0.01))
        assertEquals(1200, tenureMonths(100.0))
        assertNull(tenureMonths(999999999.0))
        assertNull(tenureMonths(0.0))
        assertNull(tenureMonths(-1.0))
        assertNull(tenureMonths(Double.NaN))
        assertNull(tenureMonths(null))
    }
}
