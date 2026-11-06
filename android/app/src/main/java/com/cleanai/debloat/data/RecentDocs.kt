package com.cleanai.debloat.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Word and Excel files opened or saved recently, so they can be opened again in one tap.
 * Only the file's name and its link are kept, on the phone; the file itself stays where it is.
 */
object RecentDocs {
    enum class Kind(val key: String) { WORD("recent_word"), EXCEL("recent_excel") }

    data class Entry(val uri: Uri, val name: String, val time: Long)

    private const val MAX_KEPT = 8

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("recent_docs", Context.MODE_PRIVATE)

    fun list(ctx: Context, kind: Kind): List<Entry> = runCatching {
        val arr = JSONArray(prefs(ctx).getString(kind.key, "[]"))
        (0 until arr.length()).map { arr.getJSONObject(it) }
            .map { Entry(Uri.parse(it.getString("uri")), it.getString("name"), it.getLong("time")) }
    }.getOrDefault(emptyList())

    /** Remembers [uri] and keeps the right to open it again after the app restarts. */
    fun add(ctx: Context, kind: Kind, uri: Uri, name: String) {
        val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, rw) }
            .recoverCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val entries = listOf(Entry(uri, name, System.currentTimeMillis())) + list(ctx, kind).filter { it.uri != uri }
        save(ctx, kind, entries.take(MAX_KEPT))
    }

    fun remove(ctx: Context, kind: Kind, uri: Uri) = save(ctx, kind, list(ctx, kind).filter { it.uri != uri })

    private fun save(ctx: Context, kind: Kind, entries: List<Entry>) {
        val arr = JSONArray()
        entries.forEach { arr.put(JSONObject().put("uri", it.uri.toString()).put("name", it.name).put("time", it.time)) }
        prefs(ctx).edit().putString(kind.key, arr.toString()).apply()
    }
}
