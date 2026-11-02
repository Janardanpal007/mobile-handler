package com.cleanai.debloat.data

import android.os.Build

/**
 * One step of the Shizuku walkthrough.
 * [open] names a screen the step can jump to: "shizuku", "about", "dev", "wifi" or null.
 * [search] is the web search used when the user is stuck (defaults to the title).
 */
data class GuideStep(
    val title: String,
    val actions: List<String>,
    val open: String? = null,
    val tips: List<String> = emptyList(),
    /** What to search the web for when the user is stuck on this step. */
    val search: String? = null,
)

/** Builds manufacturer-specific Shizuku setup steps: built-in (offline) or live from Claude + web search. */
object SetupGuide {
    enum class Family { XIAOMI, SAMSUNG, COLOROS, ONEPLUS, VIVO, HUAWEI, GENERIC }

    val family: Family
        get() = when (Build.MANUFACTURER.lowercase()) {
            "xiaomi", "redmi", "poco" -> Family.XIAOMI
            "samsung" -> Family.SAMSUNG
            "realme", "oppo" -> Family.COLOROS
            "oneplus" -> Family.ONEPLUS
            "vivo", "iqoo" -> Family.VIVO
            "huawei", "honor" -> Family.HUAWEI
            else -> Family.GENERIC
        }

    private fun prop(key: String): String = runCatching {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java)
            .invoke(null, key) as String
    }.getOrDefault("")

    /** Skin name + version, e.g. "realme UI V2.0" or "HyperOS OS1.0", when the ROM exposes it. */
    val skin: String
        get() = listOf(
            "HyperOS" to "ro.mi.os.version.name",
            "MIUI" to "ro.miui.ui.version.name",
            "realme UI" to "ro.build.version.realmeui",
            "ColorOS" to "ro.build.version.opporom",
            "ColorOS" to "ro.build.version.oplusrom",
            "One UI" to "ro.build.version.oneui",
            "Funtouch/Origin OS" to "ro.vivo.os.version",
            "EMUI" to "ro.build.version.emui",
        ).firstNotNullOfOrNull { (name, key) -> prop(key).takeIf { it.isNotBlank() }?.let { "$name $it" } }
            ?: ""

    /** Offline steps, tailored by manufacturer family. */
    fun builtIn(): List<GuideStep> {
        val f = family
        val wireless = Build.VERSION.SDK_INT >= 30

        val buildNumber = when (f) {
            Family.XIAOMI -> "Settings → About phone → tap \"OS version\" (HyperOS) or \"MIUI version\" 7 times"
            Family.SAMSUNG -> "Settings → About phone → Software information → tap \"Build number\" 7 times"
            Family.COLOROS -> "Settings → About phone → Version → tap \"Build number\" 7 times"
            Family.ONEPLUS -> "Settings → About device → Version → tap \"Build number\" 7 times"
            Family.VIVO -> "Settings → About phone → Software version → tap it 7 times"
            Family.HUAWEI -> "Settings → About phone → tap \"Build number\" 7 times"
            Family.GENERIC -> "Settings → About phone → tap \"Build number\" 7 times"
        }
        val devOptions = when (f) {
            Family.XIAOMI, Family.COLOROS -> "Settings → Additional settings → Developer options"
            Family.SAMSUNG -> "Settings → Developer options (at the very bottom)"
            Family.ONEPLUS -> "Settings → System → Developer options"
            Family.VIVO -> "Settings → System management (or More settings) → Developer options"
            Family.HUAWEI -> "Settings → System & updates → Developer options"
            Family.GENERIC -> "Settings → System → Developer options"
        }
        val brandSwitches: List<String> = when (f) {
            Family.XIAOMI -> listOf(
                "Turn ON \"USB debugging\" and \"USB debugging (Security settings)\". The second one needs a SIM card and a Mi account, and may ask you to wait a few seconds.",
                "Turn ON \"Install via USB\".",
            )
            Family.COLOROS, Family.ONEPLUS -> listOf(
                "Make sure you're signed in to your ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} / HeyTap account — without it the next switch turns itself back off.",
                "Scroll to the very bottom and turn ON \"Disable permission monitoring\".",
                "Restart the phone so the change takes effect.",
            )
            Family.HUAWEI -> listOf("Turn ON \"Allow ADB debugging in charge only mode\".")
            else -> emptyList()
        }
        val brandTips: List<String> = when (f) {
            Family.XIAOMI -> listOf("If \"USB debugging (Security settings)\" won't turn on, insert a SIM, turn mobile data on and sign in to your Mi account.")
            Family.COLOROS, Family.ONEPLUS -> listOf(
                "If Shizuku says \"the permission of adb is limited\", permission monitoring is still on — sign in to your account, toggle it again and restart.",
            )
            Family.SAMSUNG -> listOf("If Wireless debugging keeps turning off, turn off \"Auto Blocker\" (Settings → Security and privacy).")
            else -> emptyList()
        }
        val batteryTip = when (f) {
            Family.XIAOMI -> "Settings → Apps → Shizuku → Autostart ON, Battery saver → No restrictions."
            Family.COLOROS, Family.ONEPLUS -> "Settings → Apps → Shizuku → Battery usage → allow background activity."
            Family.SAMSUNG -> "Settings → Apps → Shizuku → Battery → Unrestricted."
            Family.VIVO -> "Settings → Battery → Background power consumption → allow Shizuku."
            else -> "Settings → Apps → Shizuku → Battery → Unrestricted."
        }

        val steps = mutableListOf(
            GuideStep(
                "Install Shizuku",
                listOf("Tap \"Open Shizuku\" below. Install it from the Play Store (or GitHub), then come back here."),
                open = "shizuku",
            ),
            GuideStep(
                "Turn on Developer options",
                listOf("Go to $buildNumber.", "Enter your PIN if asked. You'll see \"You are now a developer\"."),
                open = "about",
                search = "enable developer options build number",
                tips = listOf("Already see \"No need, you are already a developer\"? Great — this step is done."),
            ),
            GuideStep(
                "Set up Developer options",
                listOf("Open $devOptions.") + brandSwitches,
                open = "dev",
                tips = brandTips,
                search = when (f) {
                    Family.XIAOMI -> "usb debugging security settings"
                    Family.COLOROS, Family.ONEPLUS -> "disable permission monitoring"
                    else -> "usb debugging developer options"
                },
            ),
        )
        if (wireless) {
            steps += GuideStep(
                "Turn on Wireless debugging",
                listOf(
                    "Connect to a Wi-Fi network (mobile data won't work for this step).",
                    "In Developer options, turn ON \"Wireless debugging\" and tap \"Allow\".",
                ),
                open = "dev",
                search = "wireless debugging",
                tips = listOf("Wireless debugging turns off when you change Wi-Fi network — just turn it on again."),
            )
            steps += GuideStep(
                "Pair Shizuku",
                listOf(
                    "Open Shizuku → \"Start via Wireless debugging\" → \"Pairing\". Allow notifications if asked.",
                    "Go back to Developer options → tap the words \"Wireless debugging\" → \"Pair device with pairing code\".",
                    "Pull down the notification bar. In Shizuku's notification tap \"Enter pairing code\" and type the 6-digit code.",
                    "You'll see \"Pairing successful\". You only need to pair once.",
                ),
                open = "shizuku",
                search = "pairing code wireless debugging",
                tips = listOf(
                    "No box to type the code? Open Shizuku and Settings in split screen and try again.",
                    "Code expired? Close the pairing dialog and tap \"Pair device with pairing code\" again for a new one.",
                ),
            )
            steps += GuideStep(
                "Start Shizuku",
                listOf("In Shizuku tap \"Start\" (under Start via Wireless debugging).", "Wait until the top card says \"Shizuku is running\"."),
                open = "shizuku",
                search = "adb limited permission wireless start",
                tips = brandTips + listOf("Stuck on \"Waiting for service…\"? Close that screen, open Shizuku again and check the top card."),
            )
        } else {
            steps += GuideStep(
                "Start Shizuku from a computer",
                listOf(
                    "Android ${Build.VERSION.RELEASE} has no Wireless debugging, so you need a PC once.",
                    "Turn on \"USB debugging\", connect the phone with USB and allow the computer.",
                    "On the PC run: adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh",
                    "Shizuku should now say \"Shizuku is running\".",
                ),
                open = "shizuku",
                search = "adb start.sh usb debugging",
            )
        }
        steps += GuideStep(
            "Keep Shizuku alive (optional)",
            listOf(batteryTip, "After every phone restart, open Shizuku and tap Start again."),
            search = "stops background battery killed",
        )
        return steps
    }
}
