package com.cleanai.debloat.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.pow

data class Weather(
    val place: String,
    val now: String,
    val details: List<String>,
)

/** Weather from Open-Meteo and maths worked out on the phone. */
object Answers {

    // ---------------------------------------------------------------- weather

    /** City named in the question ("weather in Pune tomorrow" -> "Pune"), or null. */
    fun cityIn(question: String): String? =
        Regex("\\b(?:in|at|for|of)\\s+([\\p{L} .'-]{2,40})", RegexOption.IGNORE_CASE).find(question)
            ?.groupValues?.get(1)
            ?.replace(Regex("\\b(today|tomorrow|now|right now|this week|please)\\b.*$", RegexOption.IGNORE_CASE), "")
            ?.trim(' ', '.', '?')
            ?.takeIf { it.length >= 2 }

    suspend fun weatherForCity(city: String): Weather? = withContext(Dispatchers.IO) {
        val geo = get("https://geocoding-api.open-meteo.com/v1/search?name=${Uri.encode(city)}&count=1")
            ?.optJSONArray("results")?.optJSONObject(0) ?: return@withContext null
        val place = listOf(geo.optString("name"), geo.optString("admin1"), geo.optString("country"))
            .filter { it.isNotBlank() }.distinct().joinToString(", ")
        forecast(geo.optDouble("latitude"), geo.optDouble("longitude"), place)
    }

    suspend fun weatherAt(lat: Double, lon: Double): Weather? = withContext(Dispatchers.IO) {
        forecast(lat, lon, "your location")
    }

    private fun forecast(lat: Double, lon: Double, place: String): Weather? {
        val json = get(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m" +
                "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code" +
                "&timezone=auto&forecast_days=3",
        ) ?: return null
        val c = json.optJSONObject("current") ?: return null
        val now = "${sky(c.optInt("weather_code"))}, ${c.optDouble("temperature_2m").fmt()}°C " +
            "(feels like ${c.optDouble("apparent_temperature").fmt()}°C)"
        val details = mutableListOf(
            "Humidity ${c.optInt("relative_humidity_2m")}% · wind ${c.optDouble("wind_speed_10m").fmt()} km/h",
        )
        json.optJSONObject("daily")?.let { d ->
            val days = d.optJSONArray("time")
            for (i in 0 until (days?.length() ?: 0)) {
                val label = when (i) {
                    0 -> "Today"
                    1 -> "Tomorrow"
                    else -> LocalDate.parse(days!!.getString(i)).format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
                }
                details += "$label: ${sky(d.optJSONArray("weather_code").optInt(i))}, " +
                    "${d.optJSONArray("temperature_2m_min").optDouble(i).fmt()}–${d.optJSONArray("temperature_2m_max").optDouble(i).fmt()}°C, " +
                    "rain chance ${d.optJSONArray("precipitation_probability_max").optInt(i)}%"
            }
        }
        return Weather(place, now, details)
    }

    private fun Double.fmt() = if (isNaN()) "–" else String.format(Locale.ROOT, "%.0f", this)

    /** WMO weather codes used by Open-Meteo. */
    private fun sky(code: Int) = when (code) {
        0 -> "☀️ Clear"
        1, 2 -> "🌤️ Partly cloudy"
        3 -> "☁️ Cloudy"
        45, 48 -> "🌫️ Fog"
        in 51..57 -> "🌦️ Drizzle"
        in 61..67 -> "🌧️ Rain"
        in 71..77 -> "❄️ Snow"
        in 80..82 -> "🌧️ Showers"
        85, 86 -> "🌨️ Snow showers"
        in 95..99 -> "⛈️ Thunderstorm"
        else -> "Weather"
    }

    // ---------------------------------------------------------------- maths

    /** Works out "12*(3+4)", "2^10", "15% of 240" or "what is 7 x 8"; null when it isn't maths. */
    fun calculate(question: String): String? {
        val q = question.lowercase(Locale.ROOT).replace("what is", "").replace("calculate", "")
            .replace("=", "").replace("?", "").replace(",", "").trim()
        Regex("^([\\d.]+)\\s*%\\s*of\\s*([\\d.]+)$").find(q)?.let {
            val (p, n) = it.destructured
            return "$q = ${pretty(p.toDouble() / 100 * n.toDouble())}"
        }
        val expr = q.replace('x', '*').replace('×', '*').replace('÷', '/')
        if (!Regex("^[\\d.\\s+\\-*/^()%]+$").matches(expr) || !Regex("\\d\\s*[-+*/^%]\\s*[\\d(]").containsMatchIn(expr)) return null
        return runCatching { "$expr = ${pretty(Parser(expr.replace(" ", "")).parse())}" }.getOrNull()
    }

    /** Evaluates a plain expression like "12*(3+4)" or "2^10"; null if it isn't complete or valid. */
    fun evaluate(expr: String): Double? =
        runCatching { Parser(expr.replace(" ", "")).parse() }.getOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() }

    fun pretty(v: Double) =
        if (v == Math.floor(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString()
        else String.format(Locale.ROOT, "%.6f", v).trimEnd('0').trimEnd('.')

    /** Small recursive-descent parser: + - * / % ^ and brackets. */
    private class Parser(val s: String) {
        var i = 0
        fun parse(): Double = expr().also { require(i == s.length) }
        fun expr(): Double {
            var v = term()
            while (i < s.length && s[i] in "+-") v = if (s[i++] == '+') v + term() else v - term()
            return v
        }
        fun term(): Double {
            var v = power()
            while (i < s.length && s[i] in "*/%") {
                val op = s[i++]
                val r = power()
                v = when (op) { '*' -> v * r; '/' -> v / r; else -> v % r }
            }
            return v
        }
        fun power(): Double {
            val b = unary()
            return if (i < s.length && s[i] == '^') { i++; b.pow(power()) } else b
        }
        fun unary(): Double = if (i < s.length && s[i] == '-') { i++; -unary() } else atom()
        fun atom(): Double {
            if (s[i] == '(') { i++; val v = expr(); require(s[i++] == ')'); return v }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            return s.substring(start, i).toDouble()
        }
    }

    private fun get(url: String): JSONObject? = if (Privacy.blocked()) null else runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", "MobileHandler-Android")
            if (conn.responseCode !in 200..299) return null
            JSONObject(conn.inputStream.bufferedReader().readText())
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
