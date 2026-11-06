package com.cleanai.debloat.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Exchange rates from open.er-api.com: free, no key, refreshed through the day. */
object Currency {
    /** Shown first in the picker; any other code the API returns works too. */
    val COMMON = listOf(
        "INR" to "Indian Rupee", "USD" to "US Dollar", "EUR" to "Euro", "GBP" to "British Pound",
        "AED" to "UAE Dirham", "SAR" to "Saudi Riyal", "QAR" to "Qatari Riyal", "KWD" to "Kuwaiti Dinar",
        "SGD" to "Singapore Dollar", "AUD" to "Australian Dollar", "CAD" to "Canadian Dollar", "NZD" to "NZ Dollar",
        "JPY" to "Japanese Yen", "CNY" to "Chinese Yuan", "HKD" to "Hong Kong Dollar", "KRW" to "Korean Won",
        "THB" to "Thai Baht", "MYR" to "Malaysian Ringgit", "IDR" to "Indonesian Rupiah", "CHF" to "Swiss Franc",
        "NPR" to "Nepalese Rupee", "BDT" to "Bangladeshi Taka", "LKR" to "Sri Lankan Rupee", "PKR" to "Pakistani Rupee",
        "RUB" to "Russian Ruble", "ZAR" to "South African Rand",
    )

    // Words people say, for "100 dollars to rupees".
    private val NAMES = mapOf(
        "rupee" to "INR", "rupees" to "INR", "rs" to "INR", "₹" to "INR",
        "dollar" to "USD", "dollars" to "USD", "$" to "USD", "euro" to "EUR", "euros" to "EUR", "€" to "EUR",
        "pound" to "GBP", "pounds" to "GBP", "£" to "GBP", "dirham" to "AED", "dirhams" to "AED",
        "riyal" to "SAR", "riyals" to "SAR", "yen" to "JPY", "yuan" to "CNY", "ringgit" to "MYR", "baht" to "THB",
    )

    data class Rates(val usd: Map<String, Double>, val updated: Long) {
        fun rate(from: String, to: String): Double? {
            val f = usd[from] ?: return null
            val t = usd[to] ?: return null
            return t / f
        }
        fun updatedText(): String =
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(updated * 1000))
    }

    private var cache: Rates? = null
    private var fetchedAt = 0L

    /** Cached for 10 minutes so typing doesn't hit the network on every key. */
    suspend fun rates(force: Boolean = false): Rates? = withContext(Dispatchers.IO) {
        val c = cache
        if (!force && c != null && System.currentTimeMillis() - fetchedAt < 10 * 60_000) return@withContext c
        val json = get("https://open.er-api.com/v6/latest/USD") ?: return@withContext c
        if (json.optString("result") != "success") return@withContext c
        val r = json.getJSONObject("rates")
        val map = r.keys().asSequence().associateWith { r.getDouble(it) }
        Rates(map, json.optLong("time_last_update_unix")).also { cache = it; fetchedAt = System.currentTimeMillis() }
    }

    /** "100 usd to inr", "convert 50 dollars in rupees" -> a one-line answer, or null if it isn't a conversion. */
    suspend fun convert(question: String): String? {
        val q = question.lowercase(Locale.ROOT).replace(",", "")
        val m = Regex("([\\d.]+)\\s*([a-z₹$€£]+)\\s+(?:to|in|into)\\s+([a-z₹$€£]+)").find(q)
            ?: Regex("([₹$€£])\\s*([\\d.]+)\\s+(?:to|in|into)\\s+([a-z]+)").find(q)?.let { m2 ->
                // "$50 to inr": reorder to amount, from, to.
                return convertParts(m2.groupValues[2], m2.groupValues[1], m2.groupValues[3])
            }
            ?: return null
        return convertParts(m.groupValues[1], m.groupValues[2], m.groupValues[3])
    }

    private suspend fun convertParts(amountText: String, fromWord: String, toWord: String): String? {
        val amount = amountText.toDoubleOrNull() ?: return null
        val from = codeOf(fromWord) ?: return null
        val to = codeOf(toWord) ?: return null
        val r = rates() ?: return "I couldn't get exchange rates — check your internet connection."
        val rate = r.rate(from, to) ?: return null
        return "${money(amount)} $from = ${money(amount * rate)} $to\n" +
            "Rate: 1 $from = ${money(rate)} $to · updated ${r.updatedText()}"
    }

    private fun codeOf(word: String): String? {
        val w = word.trim()
        NAMES[w]?.let { return it }
        val up = w.uppercase(Locale.ROOT)
        return up.takeIf { it.length == 3 && (cache?.usd?.containsKey(it) ?: COMMON.any { c -> c.first == it }) }
    }

    fun money(v: Double): String = when {
        v == 0.0 -> "0"
        v >= 1 -> String.format(Locale.US, "%,.2f", v).removeSuffix(".00")
        else -> String.format(Locale.US, "%.4f", v).trimEnd('0').trimEnd('.')
    }

    private fun get(url: String): JSONObject? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "MobileHandler-Android")
            JSONObject(conn.inputStream.bufferedReader().readText())
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
