package com.cleanai.debloat.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.DateFormat
import java.util.Date

data class CheckItem(val text: String, val done: Boolean = false)

/**
 * One note: plain text, or a checklist when [items] is not null.
 * [trashed] is when it went to the trash (0 = not in the trash); the trash empties itself after 30 days.
 * [locked] notes only open after the fingerprint or screen lock, and their text isn't shown in the list.
 */
data class Note(
    val id: Long,
    val title: String = "",
    val body: String = "",
    val items: List<CheckItem>? = null,
    val color: Int = 0,
    val pinned: Boolean = false,
    val locked: Boolean = false,
    val tags: List<String> = emptyList(),
    val created: Long = System.currentTimeMillis(),
    val updated: Long = created,
    val trashed: Long = 0,
) {
    val isList get() = items != null
    val isEmpty get() = title.isBlank() && body.isBlank() && items.orEmpty().all { it.text.isBlank() }

    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        if (title.lowercase().contains(q) || tags.any { it.lowercase().contains(q) }) return true
        if (locked) return false
        return body.lowercase().contains(q) || items.orEmpty().any { it.text.lowercase().contains(q) }
    }

    /** The note as Markdown, for sharing and export. */
    fun toMarkdown(): String = buildString {
        if (title.isNotBlank()) append("# ").append(title.trim()).append("\n\n")
        if (items != null) items.forEach { append(if (it.done) "- [x] " else "- [ ] ").append(it.text).append('\n') }
        else append(body.trimEnd()).append('\n')
        if (tags.isNotEmpty()) append('\n').append(tags.joinToString(" ") { "#" + it.replace(' ', '_') }).append('\n')
    }

    fun editedText(): String = "Edited " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(updated))
}

/** Notes live in one JSON file in the app's private storage; nothing leaves the phone unless you share or export. */
object Notes {
    private const val TRASH_DAYS = 30L
    private val mutex = Mutex()

    private fun file(ctx: Context) = File(ctx.filesDir, "notes/notes.json")

    suspend fun load(ctx: Context): List<Note> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val f = file(ctx)
            if (!f.exists()) return@withLock emptyList()
            val arr = runCatching { JSONArray(f.readText()) }.getOrElse { return@withLock emptyList() }
            val cutoff = System.currentTimeMillis() - TRASH_DAYS * 24 * 3600 * 1000
            (0 until arr.length()).mapNotNull { runCatching { fromJson(arr.getJSONObject(it)) }.getOrNull() }
                .filter { it.trashed == 0L || it.trashed > cutoff }
        }
    }

    /** Writes all notes; a temporary file is renamed over the old one so a crash never leaves half a file. */
    suspend fun save(ctx: Context, notes: List<Note>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val f = file(ctx)
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, "notes.json.tmp")
            tmp.writeText(JSONArray().apply { notes.forEach { put(toJson(it)) } }.toString())
            if (!tmp.renameTo(f)) { tmp.copyTo(f, overwrite = true); tmp.delete() }
        }
    }

    /** Saves without waiting, and keeps going after the screen has closed. Saves run one at a time, in order. */
    fun saveLater(ctx: Context, notes: List<Note>) {
        val app = ctx.applicationContext
        background.launch { save(app, notes) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private fun toJson(n: Note) = JSONObject().apply {
        put("id", n.id); put("title", n.title); put("body", n.body)
        n.items?.let { list -> put("items", JSONArray().apply { list.forEach { put(JSONObject().put("t", it.text).put("d", it.done)) } }) }
        put("color", n.color); put("pinned", n.pinned); put("locked", n.locked)
        put("tags", JSONArray(n.tags)); put("created", n.created); put("updated", n.updated); put("trashed", n.trashed)
    }

    private fun fromJson(o: JSONObject) = Note(
        id = o.getLong("id"),
        title = o.optString("title"),
        body = o.optString("body"),
        items = o.optJSONArray("items")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).let { i -> CheckItem(i.optString("t"), i.optBoolean("d")) } } },
        color = o.optInt("color"),
        pinned = o.optBoolean("pinned"),
        locked = o.optBoolean("locked"),
        tags = o.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
        created = o.optLong("created"),
        updated = o.optLong("updated"),
        trashed = o.optLong("trashed"),
    )
}
