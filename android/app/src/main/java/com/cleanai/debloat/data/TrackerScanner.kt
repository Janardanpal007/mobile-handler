package com.cleanai.debloat.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

data class TrackerHit(val label: String, val pkg: String, val trackers: List<String>)

/** Finds ad/analytics SDKs inside installed apps by their component class names (Exodus-style). */
class TrackerScanner(private val ctx: Context) {
    private val signatures = listOf(
        "Google AdMob" to "com.google.android.gms.ads.",
        "Firebase Analytics" to "com.google.firebase.analytics",
        "Google Analytics" to "com.google.android.gms.analytics",
        "Facebook Ads" to "com.facebook.ads.",
        "Facebook Analytics" to "com.facebook.appevents",
        "AppsFlyer" to "com.appsflyer.",
        "Adjust" to "com.adjust.sdk.",
        "Branch" to "io.branch.",
        "CleverTap" to "com.clevertap.",
        "MoEngage" to "com.moengage.",
        "WebEngage" to "com.webengage.",
        "Netcore" to "com.netcore.",
        "Mixpanel" to "com.mixpanel.",
        "Amplitude" to "com.amplitude.",
        "Segment" to "com.segment.",
        "Braze" to "com.braze.",
        "OneSignal" to "com.onesignal.",
        "AppLovin" to "com.applovin.",
        "Unity Ads" to "com.unity3d.ads.",
        "ironSource" to "com.ironsource.",
        "InMobi" to "com.inmobi.",
        "Chartboost" to "com.chartboost.",
        "Vungle" to "com.vungle.",
        "Mintegral" to "com.mbridge.",
        "Pangle" to "com.bytedance.sdk.",
        "Huawei Ads" to "com.huawei.hms.ads",
        "Yandex Metrica" to "com.yandex.metrica",
        "Flurry" to "com.flurry.",
        "Kochava" to "com.kochava.",
        "Singular" to "com.singular.sdk",
        "Smaato" to "com.smaato.",
        "Start.io" to "com.startapp.",
    )

    fun scan(): List<TrackerHit> {
        val pm = ctx.packageManager
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or
            PackageManager.MATCH_DISABLED_COMPONENTS
        val apps = pm.getInstalledApplications(0).filter {
            it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        }
        return apps.mapNotNull { ai ->
            val info = runCatching { pm.getPackageInfo(ai.packageName, flags) }.getOrNull() ?: return@mapNotNull null
            val names = buildList {
                info.activities?.forEach { add(it.name) }
                info.services?.forEach { add(it.name) }
                info.receivers?.forEach { add(it.name) }
                info.providers?.forEach { add(it.name) }
            }
            val hits = signatures.filter { (_, prefix) -> names.any { it.startsWith(prefix) } }.map { it.first }
            if (hits.isEmpty()) null
            else TrackerHit(pm.getApplicationLabel(ai).toString(), ai.packageName, hits)
        }.sortedByDescending { it.trackers.size }
    }
}
