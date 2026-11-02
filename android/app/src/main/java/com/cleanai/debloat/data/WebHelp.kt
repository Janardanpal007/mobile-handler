package com.cleanai.debloat.data

import android.net.Uri
import android.os.Build
import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One thing found online, ranked by how much it helped other people. */
data class WebTip(val title: String, val url: String, val source: String, val score: Int, val note: String)

/**
 * Live help from the internet that needs no API key:
 * dontkillmyapp.com (per-brand battery steps), Shizuku's GitHub issues (real reports from the
 * same brand) and Android Stack Exchange (top-voted answers).
 */
object WebHelp {
    private val brand get() = Build.MANUFACTURER.lowercase()

    /** Battery / background steps for this manufacturer, or null when offline or unknown. */
    suspend fun keepAliveSteps(): List<String>? = withContext(Dispatchers.IO) {
        val slugs = listOf(brand) + when (SetupGuide.family) {
            SetupGuide.Family.COLOROS -> listOf("oppo")
            SetupGuide.Family.XIAOMI -> listOf("xiaomi")
            SetupGuide.Family.HUAWEI -> listOf("huawei")
            else -> emptyList()
        }
        for (slug in slugs.distinct()) {
            val json = get("https://dontkillmyapp.com/api/v2/$slug.json") ?: continue
            val steps = htmlLines(json.optString("user_solution"))
            if (steps.isNotEmpty()) return@withContext steps.take(8)
        }
        null
    }

    /** Best matches for [topic] on this brand, most helpful first. Empty when offline. */
    suspend fun search(topic: String): List<WebTip> = coroutineScope {
        val words = topic.lowercase().replace(Regex("[^a-z0-9 ]"), " ").split(" ")
            .filter { it.length > 2 && it !in STOP }.distinct().take(4)
        if (words.isEmpty()) return@coroutineScope emptyList()
        val q = words.joinToString(" ")
        val gh = async(Dispatchers.IO) { github("$brand $q").ifEmpty { github(q) } }
        val se = async(Dispatchers.IO) { stackExchange("$brand $q").ifEmpty { stackExchange(q) } }
        // Popular-but-unrelated posts are worse than nothing: keep only titles that mention the topic,
        // then rank by how on-topic they are, whether they mention this brand, and how much they helped others.
        listOf(gh, se).awaitAll().flatten()
            .mapNotNull { tip ->
                val t = tip.title.lowercase()
                val hits = words.count { t.contains(it.take(5)) }
                if (hits == 0) null
                else tip.copy(score = tip.score + hits * 8 + if (t.contains(brand)) 6 else 0)
            }
            .sortedByDescending { it.score }
            .distinctBy { it.url }
            .take(4)
    }

    fun googleUrl(query: String) = "https://www.google.com/search?q=" + Uri.encode(query)
    fun youtubeUrl(query: String) = "https://www.youtube.com/results?search_query=" + Uri.encode(query)
    const val SHIZUKU_GUIDE = "https://shizuku.rikka.app/guide/setup/"

    private fun github(query: String): List<WebTip> {
        val q = URLEncoder.encode("repo:RikkaApps/Shizuku is:issue $query", "UTF-8")
        val json = get("https://api.github.com/search/issues?q=$q&sort=reactions&order=desc&per_page=8") ?: return emptyList()
        val items = json.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val it = items.getJSONObject(i)
            val title = it.optString("title").trim()
            if (title.length < 12) return@mapNotNull null // skip "Ff"-style junk reports
            val comments = it.optInt("comments")
            val reactions = it.optJSONObject("reactions")?.optInt("total_count") ?: 0
            val closed = it.optString("state") == "closed"
            WebTip(
                title, it.optString("html_url"), "Shizuku GitHub",
                score = comments + reactions * 3 + if (closed) 4 else 0,
                note = (if (closed) "resolved · " else "") + "$comments replies",
            )
        }
    }

    private fun stackExchange(query: String): List<WebTip> {
        val q = URLEncoder.encode(query, "UTF-8")
        val json = get("https://api.stackexchange.com/2.3/search/advanced?order=desc&sort=votes&q=$q&site=android&pagesize=5")
            ?: return emptyList()
        val items = json.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).map { i ->
            val it = items.getJSONObject(i)
            val answered = it.optBoolean("is_answered")
            val votes = it.optInt("score")
            WebTip(
                Html.fromHtml(it.optString("title"), Html.FROM_HTML_MODE_LEGACY).toString(),
                it.optString("link"), "Android Stack Exchange",
                score = votes * 2 + it.optInt("answer_count") + if (answered) 4 else 0,
                note = (if (answered) "answered · " else "") + "$votes votes",
            )
        }
    }

    private fun htmlLines(html: String): List<String> =
        Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
            .replace("￼", "") // image placeholders
            .split("\n")
            .map { it.trim().trimStart('•', '-', ' ') }
            .filter { it.length > 3 }
            .map { if (it.length > 220) it.take(217) + "…" else it }

    private fun get(url: String): JSONObject? = if (Privacy.blocked()) null else runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", "MobileHandler-Android")
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode !in 200..299) return null
            JSONObject(conn.inputStream.bufferedReader().readText())
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private val STOP = setOf(
        "the", "and", "for", "how", "what", "why", "does", "not", "can", "with", "this", "that", "my", "your",
        "step", "turn", "set", "phone", "shizuku", "when", "doesn", "don", "won", "into", "from", "you",
        "realme", "xiaomi", "samsung", "oppo", "oneplus", "vivo", "huawei", "honor", "redmi", "poco", "iqoo", "google",
    )
}
