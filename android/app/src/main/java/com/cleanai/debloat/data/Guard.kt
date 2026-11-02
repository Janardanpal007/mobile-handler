package com.cleanai.debloat.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager

/**
 * Last line of defence before any command runs. A package is refused if it is
 * system-critical, not in [BloatDb], or currently one of the user's default apps.
 */
object Guard {
    private val critical = setOf(
        "android", "com.android.systemui", "com.android.settings", "com.android.phone",
        "com.android.shell", "com.android.packageinstaller", "com.google.android.packageinstaller",
        "com.android.permissioncontroller", "com.google.android.permissioncontroller",
        "com.android.bluetooth", "com.android.nfc", "com.android.se", "com.android.keychain",
        "com.android.location.fused", "com.android.inputdevices", "com.android.externalstorage",
        "com.android.documentsui", "com.google.android.documentsui", "com.android.vending",
        "com.google.android.gms", "com.google.android.gsf", "com.google.android.webview",
        "com.android.webview", "com.google.android.ext.services", "com.android.ext.services",
        "com.google.android.modulemetadata", "com.android.launcher3", "com.miui.home",
        "com.sec.android.app.launcher", "com.android.incallui", "com.android.mms.service",
        "com.android.carrierconfig", "com.android.emergency", "com.android.certinstaller",
        "com.miui.securitycenter", "com.miui.global.packageinstaller", "com.android.camera",
        "com.android.mtp", "com.android.storagemanager", "com.android.vpndialogs",
        "com.android.backupconfirm", "com.android.localtransport", "com.android.wallpaperbackup",
        "com.coloros.safecenter", "com.oplus.securitypermission",
    )
    private val criticalPrefixes = listOf(
        "com.android.providers.", "com.android.server.", "com.android.networkstack",
        "com.google.android.networkstack", "com.android.wifi", "com.android.cellbroadcast",
        "com.google.android.cellbroadcast", "com.qualcomm.", "com.mediatek.", "vendor.",
    )
    private val pkgPattern = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*$")

    fun isCritical(pkg: String) = pkg in critical || criticalPrefixes.any { pkg.startsWith(it) }

    /** Returns why a package must not be touched, or null if it is safe to act on. */
    fun blockReason(ctx: Context, pkg: String): String? = when {
        !pkgPattern.matches(pkg) -> "invalid package name"
        pkg == ctx.packageName -> "that's me"
        isCritical(pkg) -> "system-critical"
        pkg !in BloatDb.all -> "not in my safe list"
        else -> defaultApps(ctx)[pkg]?.let { "it's your $it" }
    }

    /** Package -> role, for apps the user relies on right now. */
    fun defaultApps(ctx: Context): Map<String, String> {
        val pm = ctx.packageManager
        val out = mutableMapOf<String, String>()
        fun resolve(intent: Intent) = runCatching {
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        }.getOrNull()?.takeIf { it != "android" }

        resolve(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))?.let { out[it] = "home screen" }
        resolve(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")))?.let { out[it] = "default browser" }
        runCatching {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        }.getOrNull()?.substringBefore('/')?.let { out[it] = "keyboard" }
        runCatching { Telephony.Sms.getDefaultSmsPackage(ctx) }.getOrNull()?.let { out[it] = "SMS app" }
        runCatching {
            (ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).defaultDialerPackage
        }.getOrNull()?.let { out[it] = "phone app" }
        return out
    }
}
