package com.cleanai.debloat.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {
    private fun unit(category: String, name: String) =
        Units.categories.first { it.name == category }.units.first { it.name == name }

    private fun convert(category: String, v: Double, from: String, to: String) =
        Units.convert(v, unit(category, from), unit(category, to))

    @Test
    fun `converts length and weight`() {
        assertEquals(3.28084, convert("Length", 1.0, "Metre", "Foot"), 1e-5)
        assertEquals(1.609344, convert("Length", 1.0, "Mile", "Kilometre"), 1e-9)
        assertEquals(2.20462, convert("Weight", 1.0, "Kilogram", "Pound"), 1e-5)
    }

    @Test
    fun `converts temperatures through Celsius`() {
        assertEquals(212.0, convert("Temperature", 100.0, "Celsius", "Fahrenheit"), 1e-9)
        assertEquals(-40.0, convert("Temperature", -40.0, "Fahrenheit", "Celsius"), 1e-9)
        assertEquals(0.0, convert("Temperature", 273.15, "Kelvin", "Celsius"), 1e-9)
    }

    @Test
    fun `converts area, speed and data`() {
        assertEquals(43560.0, convert("Area", 1.0, "Acre", "Square foot"), 1e-3)
        assertEquals(100.0, convert("Speed", 27.7777777778, "Metres per second", "Kilometres per hour"), 1e-6)
        assertEquals(8000.0, convert("Data", 1.0, "Kilobyte", "Bit"), 1e-9)
    }

    @Test
    fun `formats results for reading`() {
        assertEquals("1,234.5", Units.format(1234.5))
        assertEquals("0.333333", Units.format(1.0 / 3))
        assertEquals("0", Units.format(-0.0))
        assertEquals("1.5E12", Units.format(1.5e12))
        assertEquals("—", Units.format(Double.NaN))
    }
}
