package com.cleanai.debloat.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.cleanai.debloat.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * New versions are published as GitHub releases. This asks GitHub for the latest one, compares it
 * with the installed version and, when it's newer, can show a notification. Only the public release
 * list is read; nothing about the user or the phone is sent.
 */
object Updates {
    private const val REPO = "Janardanpal007/mobile-handler"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases/latest"
    private const val CHANNEL = "updates"
    private const val DAY_MS = 20L * 60 * 60 * 1000

    data class Release(val version: String, val notes: String, val apkUrl: String)

    sealed interface Check {
        data object UpToDate : Check
        data class Available(val release: Release) : Check
        data class Failed(val reason: String) : Check
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun installed(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "1.0"

    /** "Notify me about new versions" (on unless the user turns it off). */
    fun notifyOn(ctx: Context) = prefs(ctx).getBoolean("update_notify", true)
    fun setNotify(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("update_notify", on).apply()

    /** True when [a] is a higher version than [b] ("1.10" > "1.9"). */
    fun isNewer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    suspend fun check(ctx: Context): Check = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(API).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "MobileHandler")
            if (c.responseCode != 200) return@runCatching Check.Failed("GitHub didn't answer (${c.responseCode}). Try again later.")
            val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val version = json.getString("tag_name").removePrefix("v").trim()
            val assets = json.optJSONArray("assets")
            val apk = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }?.optString("browser_download_url")
            prefs(ctx).edit().putLong("update_checked", System.currentTimeMillis()).apply()
            if (isNewer(version, installed(ctx)))
                Check.Available(Release(version, plainNotes(json.optString("body")), apk ?: RELEASES_PAGE))
            else Check.UpToDate
        }.getOrElse { Check.Failed("Couldn't reach GitHub. Check your internet and try again.") }
    }

    /** Release notes are written for the GitHub page: drop the Markdown and the "download below" line. */
    private fun plainNotes(body: String) = body.lines()
        .filterNot { it.trim().startsWith("Download") }
        .joinToString("\n") { it.replace("**", "").replace("- ", "• ") }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    /** Opens the download of [release] (or the releases page) in the browser. */
    fun download(ctx: Context, release: Release?) {
        val url = release?.apkUrl ?: RELEASES_PAGE
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /**
     * Called when the app opens: at most about once a day, and only when notifications are on,
     * check quietly and notify once per new version.
     */
    suspend fun backgroundCheck(ctx: Context) {
        if (!notifyOn(ctx)) return
        val last = prefs(ctx).getLong("update_checked", 0)
        if (System.currentTimeMillis() - last < DAY_MS) return
        val r = check(ctx) as? Check.Available ?: return
        if (prefs(ctx).getString("update_notified", null) == r.release.version) return
        notify(ctx, r.release)
        prefs(ctx).edit().putString("update_notified", r.release.version).apply()
    }

    fun canNotify(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notify(ctx: Context, release: Release) {
        if (!canNotify(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val tap = PendingIntent.getActivity(
            ctx, 0,
            Intent(Intent.ACTION_VIEW, Uri.parse(release.apkUrl)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        @Suppress("DEPRECATION")
        val b = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(ctx, CHANNEL) else android.app.Notification.Builder(ctx)
        val n = b.setSmallIcon(R.drawable.ic_update)
            .setContentTitle("Mobile Handler ${release.version} is available")
            .setContentText("Tap to download the update.")
            .setStyle(android.app.Notification.BigTextStyle().bigText(
                "Tap to download the update." + if (release.notes.isNotBlank()) "\n\n" + release.notes.take(300) else "",
            ))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        nm.notify(7001, n)
    }
}
