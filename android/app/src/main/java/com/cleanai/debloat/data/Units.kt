package com.cleanai.debloat.data

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/** Unit conversions for the calculator's Units tab. Every unit converts through its category's base unit. */
object Units {
    class Measure(val name: String, val symbol: String, val toBase: (Double) -> Double, val fromBase: (Double) -> Double)

    class Category(val name: String, val units: List<Measure>, val allowsNegative: Boolean = false)

    private fun linear(name: String, symbol: String, factor: Double) = Measure(name, symbol, { it * factor }, { it / factor })

    private const val ABSOLUTE_ZERO_C = 273.15

    val categories = listOf(
        Category(
            "Length",
            listOf(
                linear("Millimetre", "mm", 0.001), linear("Centimetre", "cm", 0.01), linear("Metre", "m", 1.0),
                linear("Kilometre", "km", 1000.0), linear("Inch", "in", 0.0254), linear("Foot", "ft", 0.3048),
                linear("Yard", "yd", 0.9144), linear("Mile", "mi", 1609.344),
            ),
        ),
        Category(
            "Weight",
            listOf(
                linear("Milligram", "mg", 1e-6), linear("Gram", "g", 0.001), linear("Kilogram", "kg", 1.0),
                linear("Tonne", "t", 1000.0), linear("Ounce", "oz", 0.028349523125), linear("Pound", "lb", 0.45359237),
            ),
        ),
        Category(
            "Temperature",
            listOf(
                Measure("Celsius", "°C", { it }, { it }),
                Measure("Fahrenheit", "°F", { (it - 32) * 5 / 9 }, { it * 9 / 5 + 32 }),
                Measure("Kelvin", "K", { it - ABSOLUTE_ZERO_C }, { it + ABSOLUTE_ZERO_C }),
            ),
            allowsNegative = true,
        ),
        Category(
            "Area",
            listOf(
                linear("Square centimetre", "cm²", 1e-4), linear("Square metre", "m²", 1.0), linear("Square kilometre", "km²", 1e6),
                linear("Square foot", "ft²", 0.09290304), linear("Square yard (gaj)", "yd²", 0.83612736),
                linear("Acre", "ac", 4046.8564224), linear("Hectare", "ha", 10_000.0),
            ),
        ),
        Category(
            "Volume",
            listOf(
                linear("Millilitre", "ml", 0.001), linear("Litre", "l", 1.0), linear("Cubic metre", "m³", 1000.0),
                linear("Teaspoon (US)", "tsp", 0.00492892159375), linear("Cup (US)", "cup", 0.2365882365),
                linear("Gallon (US)", "gal", 3.785411784), linear("Gallon (UK)", "gal UK", 4.54609),
            ),
        ),
        Category(
            "Speed",
            listOf(
                linear("Metres per second", "m/s", 1.0), linear("Kilometres per hour", "km/h", 1 / 3.6),
                linear("Miles per hour", "mph", 0.44704), linear("Knot", "kn", 1852.0 / 3600),
            ),
        ),
        Category(
            "Time",
            listOf(
                linear("Second", "s", 1.0), linear("Minute", "min", 60.0), linear("Hour", "h", 3600.0),
                linear("Day", "d", 86_400.0), linear("Week", "wk", 604_800.0), linear("Year (365 days)", "yr", 31_536_000.0),
            ),
        ),
        Category(
            "Data",
            listOf(
                linear("Bit", "bit", 0.125), linear("Byte", "B", 1.0), linear("Kilobyte", "KB", 1e3),
                linear("Megabyte", "MB", 1e6), linear("Gigabyte", "GB", 1e9), linear("Terabyte", "TB", 1e12),
            ),
        ),
    )

    fun convert(value: Double, from: Measure, to: Measure): Double = to.fromBase(from.toBase(value))

    private const val LARGE = 1e12
    private const val SMALL = 1e-6

    /** Up to 6 decimals with thousands separators; very large or tiny numbers in short scientific form. */
    fun format(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "—"
        val symbols = DecimalFormatSymbols(Locale.US)
        val a = abs(v)
        if (a >= LARGE || (a != 0.0 && a < SMALL)) return DecimalFormat("0.####E0", symbols).format(v)
        val s = DecimalFormat("#,##0.######", symbols).format(v)
        return if (s == "-0") "0" else s
    }
}
