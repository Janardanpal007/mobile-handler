package com.cleanai.debloat.data

import android.content.Context

/** Remembers what was removed/disabled so every change can be undone. */
class ChangeStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("changes", Context.MODE_PRIVATE)

    fun all(): Map<String, Decision> =
        prefs.all.mapNotNull { (k, v) ->
            runCatching { k to Decision.valueOf(v as String) }.getOrNull()
        }.toMap()

    fun record(pkg: String, d: Decision) = prefs.edit().putString(pkg, d.name).apply()
    fun forget(pkg: String) = prefs.edit().remove(pkg).apply()
}
