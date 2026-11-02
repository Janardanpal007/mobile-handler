package com.cleanai.debloat.data

import kotlin.math.PI
import kotlin.math.E
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Maths shared by the scientific calculator and the spreadsheet:
 * + − × ÷ ^ %, factorial, brackets, implicit multiplication (2π, 3(4+1)),
 * functions (sin, ln, √, SUM, AVERAGE, IF …), comparisons and, for sheets, cell references like A1 and B2:C9.
 */
object Formula {
    /** Spreadsheet-style error, e.g. "#DIV/0!". */
    class Error(val code: String) : Exception(code)

    /** Looks up a cell: a Double, a String, or null when empty. May throw [Error] if that cell has one. */
    fun interface Cells {
        fun value(col: Int, row: Int): Any?
    }

    fun eval(
        text: String,
        degrees: Boolean = false,
        cells: Cells? = null,
        vars: Map<String, Double> = emptyMap(),
    ): Double {
        val s = text.replace('×', '*').replace('÷', '/').replace('−', '-').replace("√", "sqrt").replace("π", "pi")
        val v = Parser(s, degrees, cells, vars).parse()
        if (v.isNaN()) throw Error("#NUM!")
        if (v.isInfinite()) throw Error("#DIV/0!")
        return v
    }

    /** "AB" -> 27 (1-based). */
    fun colIndex(letters: String): Int = letters.uppercase().fold(0) { acc, c -> acc * 26 + (c - 'A' + 1) }

    /** 1 -> "A", 27 -> "AA". */
    fun colName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (n > 0) {
            val r = (n - 1) % 26
            sb.append('A' + r)
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    private val CELL = Regex("^([A-Za-z]{1,3})([0-9]{1,7})$")

    private class Parser(val s: String, val degrees: Boolean, val cells: Cells?, val vars: Map<String, Double>) {
        var i = 0

        fun parse(): Double {
            val v = comparison()
            skip()
            if (i != s.length) throw Error("#ERROR!")
            return v
        }

        private fun skip() {
            while (i < s.length && s[i] == ' ') i++
        }

        private fun peek(): Char? {
            skip()
            return s.getOrNull(i)
        }

        private fun comparison(): Double {
            val a = additive()
            skip()
            val op = listOf("<=", ">=", "<>", "<", ">", "=").firstOrNull { s.startsWith(it, i) } ?: return a
            i += op.length
            val b = additive()
            val r = when (op) {
                "<=" -> a <= b
                ">=" -> a >= b
                "<>" -> a != b
                "<" -> a < b
                ">" -> a > b
                else -> a == b
            }
            return if (r) 1.0 else 0.0
        }

        private fun additive(): Double {
            var v = term()
            while (true) {
                when (peek()) {
                    '+' -> { i++; v += term() }
                    '-' -> { i++; v -= term() }
                    else -> return v
                }
            }
        }

        private fun term(): Double {
            var v = unary()
            while (true) {
                val c = peek() ?: return v
                when {
                    c == '*' -> { i++; v *= unary() }
                    c == '/' -> {
                        i++
                        val d = unary()
                        if (d == 0.0) throw Error("#DIV/0!")
                        v /= d
                    }
                    // Implicit multiplication: 2π, 3(1+2), 2sin(30), (1+2)(3+4)
                    c == '(' || c.isLetter() || c == '.' || c.isDigit() -> v *= power()
                    else -> return v
                }
            }
        }

        private fun unary(): Double = when (peek()) {
            '-' -> { i++; -unary() }
            '+' -> { i++; unary() }
            else -> power()
        }

        private fun power(): Double {
            val base = postfix()
            if (peek() == '^') {
                i++
                return base.pow(unary())
            }
            return base
        }

        private fun postfix(): Double {
            var v = primary()
            while (true) {
                when (peek()) {
                    '!' -> { i++; v = factorial(v) }
                    '%' -> { i++; v /= 100 }
                    else -> return v
                }
            }
        }

        private fun primary(): Double {
            val c = peek() ?: throw Error("#ERROR!")
            if (c == '(') {
                i++
                val v = comparison()
                if (peek() != ')') throw Error("#ERROR!")
                i++
                return v
            }
            if (c.isDigit() || c == '.') return number()
            if (c.isLetter()) {
                val name = ident()
                if (peek() == '(') return call(name)
                return value(name)
            }
            throw Error("#ERROR!")
        }

        private fun number(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            // 1e5 / 2E-3; a lone "e" after a number is Euler's number (2e = 2 × e).
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                val j = if (i + 1 < s.length && (s[i + 1] == '+' || s[i + 1] == '-')) i + 2 else i + 1
                if (j < s.length && s[j].isDigit()) {
                    i = j
                    while (i < s.length && s[i].isDigit()) i++
                }
            }
            return s.substring(start, i).toDoubleOrNull() ?: throw Error("#ERROR!")
        }

        private fun ident(): String {
            val start = i
            while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_')) i++
            return s.substring(start, i)
        }

        private fun value(name: String): Double {
            vars.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.let { return it.value }
            when (name.lowercase()) {
                "pi" -> return PI
                "e" -> return E
                "true" -> return 1.0
                "false" -> return 0.0
            }
            if (cells != null) CELL.matchEntire(name)?.let { m ->
                if (peek() == ':') throw Error("#VALUE!") // a range only makes sense inside SUM(…) and friends
                return when (val v = cells.value(colIndex(m.groupValues[1]), m.groupValues[2].toInt())) {
                    null -> 0.0
                    is Double -> v
                    else -> v.toString().trim().toDoubleOrNull() ?: throw Error("#VALUE!")
                }
            }
            throw Error("#NAME?")
        }

        /** One function argument: a list of numbers (from a range) or a single number. */
        private fun argument(): List<Double> {
            skip()
            val save = i
            if (cells != null && i < s.length && s[i].isLetter()) {
                val first = ident()
                val a = CELL.matchEntire(first)
                if (a != null && peek() == ':') {
                    i++
                    skip()
                    val b = CELL.matchEntire(ident()) ?: throw Error("#REF!")
                    val c1 = colIndex(a.groupValues[1]); val r1 = a.groupValues[2].toInt()
                    val c2 = colIndex(b.groupValues[1]); val r2 = b.groupValues[2].toInt()
                    val out = mutableListOf<Double>()
                    for (r in minOf(r1, r2)..maxOf(r1, r2)) for (c in minOf(c1, c2)..maxOf(c1, c2)) {
                        // Like Excel, text and empty cells in a range are skipped.
                        (cells.value(c, r) as? Double)?.let { out += it }
                    }
                    return out
                }
                i = save
            }
            return listOf(comparison())
        }

        private fun call(name: String): Double {
            i++ // (
            val args = mutableListOf<List<Double>>()
            if (peek() != ')') {
                args += argument()
                while (peek() == ',' || peek() == ';') {
                    i++
                    args += argument()
                }
            }
            if (peek() != ')') throw Error("#ERROR!")
            i++
            val flat = args.flatten()
            fun one(): Double = args.singleOrNull()?.singleOrNull() ?: throw Error("#VALUE!")
            fun arg(n: Int): Double = args.getOrNull(n)?.singleOrNull() ?: throw Error("#VALUE!")
            fun toRad(x: Double) = if (degrees) Math.toRadians(x) else x
            fun fromRad(x: Double) = if (degrees) Math.toDegrees(x) else x
            return when (name.lowercase()) {
                "sin" -> cleanTrig(sin(toRad(one())))
                "cos" -> cleanTrig(cos(toRad(one())))
                "tan" -> {
                    val x = toRad(one())
                    if (abs(cos(x)) < 1e-12) throw Error("#DIV/0!")
                    cleanTrig(tan(x))
                }
                "asin" -> fromRad(asin(one()))
                "acos" -> fromRad(acos(one()))
                "atan" -> fromRad(atan(one()))
                "ln" -> ln(one())
                "log", "log10" -> if (args.size == 2) ln(arg(0)) / ln(arg(1)) else log10(one())
                "sqrt" -> sqrt(one())
                "abs" -> abs(one())
                "exp" -> exp(one())
                "floor" -> floor(one())
                "ceil", "ceiling" -> ceil(one())
                "int" -> floor(one())
                "fact" -> factorial(one())
                "pi" -> PI
                "power", "pow" -> arg(0).pow(arg(1))
                "mod" -> {
                    val d = arg(1)
                    if (d == 0.0) throw Error("#DIV/0!")
                    arg(0) - d * floor(arg(0) / d)
                }
                "round" -> {
                    val digits = if (args.size > 1) arg(1).toInt() else 0
                    java.math.BigDecimal(arg(0)).setScale(digits, java.math.RoundingMode.HALF_UP).toDouble()
                }
                "sum" -> flat.sum()
                "average", "avg" -> if (flat.isEmpty()) throw Error("#DIV/0!") else flat.average()
                "min" -> flat.minOrNull() ?: 0.0
                "max" -> flat.maxOrNull() ?: 0.0
                "count" -> flat.size.toDouble()
                "product" -> flat.fold(1.0) { a, b -> a * b }
                "if" -> when (args.size) {
                    2 -> if (arg(0) != 0.0) arg(1) else 0.0
                    3 -> if (arg(0) != 0.0) arg(1) else arg(2)
                    else -> throw Error("#VALUE!")
                }
                "and" -> if (flat.all { it != 0.0 }) 1.0 else 0.0
                "or" -> if (flat.any { it != 0.0 }) 1.0 else 0.0
                "not" -> if (one() == 0.0) 1.0 else 0.0
                else -> throw Error("#NAME?")
            }
        }

        /** sin(180°) should be 0, not 1.2e-16. */
        private fun cleanTrig(v: Double) = if (abs(v) < 1e-12) 0.0 else v

        private fun factorial(v: Double): Double {
            if (v < 0 || v != floor(v) || v > 170) throw Error("#NUM!")
            var r = 1.0
            for (k in 2..v.toInt()) r *= k
            return r
        }
    }
}
