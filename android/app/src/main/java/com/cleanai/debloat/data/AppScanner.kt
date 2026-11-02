package com.cleanai.debloat.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

enum class Decision { REMOVE, DISABLE, KEEP }

data class FoundApp(
    val info: BloatInfo,
    val label: String,
    val isSystem: Boolean,
) {
    val pkg get() = info.pkg

    /** null means "your call" – no recommendation either way. */
    val recommended: Decision?
        get() = when (info.safety) {
            Safety.RECOMMENDED -> Decision.REMOVE
            Safety.CAUTION -> Decision.KEEP
            Safety.OPTIONAL -> null
        }

    val allowed: List<Decision>
        get() = if (info.safety == Safety.CAUTION) listOf(Decision.DISABLE, Decision.KEEP)
        else Decision.entries
}

data class LockedApp(val label: String, val reason: String)

data class ScanResult(
    val totalApps: Int,
    val systemApps: Int,
    val found: List<FoundApp>,
    val locked: List<LockedApp>,
)

class AppScanner(private val ctx: Context) {

    fun scan(): ScanResult {
        val pm = ctx.packageManager
        val apps = pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
            .filter { it.flags and ApplicationInfo.FLAG_INSTALLED != 0 }
        val defaults = Guard.defaultApps(ctx)

        val found = mutableListOf<FoundApp>()
        val locked = mutableListOf<LockedApp>()
        for (ai in apps) {
            val label = runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(ai.packageName)
            val info = BloatDb.all[ai.packageName]
            val defaultRole = defaults[ai.packageName]
            when {
                info != null && defaultRole != null -> locked += LockedApp(label, "your $defaultRole")
                info != null && ai.enabled -> found += FoundApp(info, label, ai.flags and ApplicationInfo.FLAG_SYSTEM != 0)
                Guard.isCritical(ai.packageName) -> locked += LockedApp(label, "system-critical")
            }
        }
        return ScanResult(
            totalApps = apps.size,
            systemApps = apps.count { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 },
            found = found.sortedWith(compareBy({ it.info.safety.ordinal }, { it.label.lowercase() })),
            locked = locked,
        )
    }

    fun labelOf(pkg: String): String = runCatching {
        val ai = ctx.packageManager.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES)
        ctx.packageManager.getApplicationLabel(ai).toString()
    }.getOrNull() ?: BloatDb.all[pkg]?.name ?: pkg
}
