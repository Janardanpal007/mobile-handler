package com.cleanai.debloat.agent

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.cleanai.debloat.data.Currency
import com.cleanai.debloat.data.MyFiles
import com.cleanai.debloat.data.Privacy
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cleanai.debloat.data.AppScanner
import com.cleanai.debloat.data.BloatDb
import com.cleanai.debloat.data.ChangeStore
import com.cleanai.debloat.data.Answers
import com.cleanai.debloat.data.PdfTools
import com.cleanai.debloat.data.SavedPdf
import com.cleanai.debloat.data.WebHelp
import com.cleanai.debloat.features.Feature
import com.cleanai.debloat.features.Features
import com.cleanai.debloat.features.Opens
import com.cleanai.debloat.overlay.FloatingCalcService
import com.cleanai.debloat.ui.CalcMemory
import com.cleanai.debloat.data.Decision
import com.cleanai.debloat.data.FoundApp
import com.cleanai.debloat.data.Guard
import com.cleanai.debloat.data.GuideStep
import com.cleanai.debloat.data.SetupGuide
import com.cleanai.debloat.data.Safety
import com.cleanai.debloat.data.ScanResult
import com.cleanai.debloat.data.ShizukuShell
import com.cleanai.debloat.data.TrackerScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

/**
 * The on-device agent. It walks the user through: connect Shizuku -> scan -> decide
 * per app -> confirm -> apply -> block trackers, and can undo everything.
 * It also checks the weather, does maths and runs the scan / PDF tools. It only handles the app's own features.
 */
class AgentViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication<Application>()
    private val scanner = AppScanner(app)
    private val store = ChangeStore(app)
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val items = mutableStateListOf<ChatItem>()
    val decisions = mutableStateMapOf<String, Decision>()
    var typing by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var shizuku by mutableStateOf(ShizukuShell.State.NOT_RUNNING); private set

    // About you: asked once on first launch, stored only in this app's private storage.
    var userName by mutableStateOf(prefs.getString("user_name", "") ?: ""); private set
    var userAge by mutableStateOf(prefs.getString("user_age", "") ?: ""); private set
    var userPlace by mutableStateOf(prefs.getString("user_place", "") ?: ""); private set
    var profileDone by mutableStateOf(prefs.getBoolean("profile_done", false)); private set

    fun saveProfile(name: String, age: String, place: String) {
        userName = name.trim(); userAge = age.trim(); userPlace = place.trim(); profileDone = true
        prefs.edit()
            .putString("user_name", userName).putString("user_age", userAge).putString("user_place", userPlace)
            .putBoolean("profile_done", true).apply()
        clearChat()
    }

    /** Wipes the conversation and starts fresh with a greeting. Nothing of the chat is kept anywhere. */
    fun clearChat() {
        items.clear()
        decisions.clear()
        viewModelScope.launch { greet() }
    }

    /** Private mode (default on): nothing is sent to any company or website. */
    var privateMode by mutableStateOf(prefs.getBoolean("private_mode", true)); private set

    fun updatePrivateMode(on: Boolean) {
        privateMode = on
        Privacy.allowOnline = !on
        prefs.edit().putBoolean("private_mode", on).apply()
    }

    private var scan: ScanResult? = null
    private var plan: List<Pair<FoundApp, Decision>> = emptyList()
    private var afterConnect: Act = Act.Clean
    private var guide: List<GuideStep> = emptyList()
    private var guideIdx = 0
    private var nextId = 0L
    private fun newId() = nextId++

    private val binderListener = Shizuku.OnBinderReceivedListener { refreshShizuku() }
    private val deadListener = Shizuku.OnBinderDeadListener { refreshShizuku() }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        viewModelScope.launch { onPermission(result == PackageManager.PERMISSION_GRANTED) }
    }

    init {
        Privacy.allowOnline = !privateMode
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(deadListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        refreshShizuku()
        viewModelScope.launch { greet() }
    }

    override fun onCleared() {
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeBinderDeadListener(deadListener)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    fun refreshShizuku() {
        viewModelScope.launch(Dispatchers.Main) { shizuku = ShizukuShell.state() }
    }

    fun setDecision(pkg: String, d: Decision) {
        decisions[pkg] = d
    }

    // ---------------------------------------------------------------- input

    fun onChoice(msgId: Long, choice: Choice) {
        if (choice.action.isLink) {
            openLink(choice.action)
            return
        }
        if (busy) return
        val idx = items.indexOfFirst { it.id == msgId }
        val msg = items.getOrNull(idx) as? ChoicesMsg ?: return
        if (msg.picked != null) return
        items[idx] = msg.copy(picked = choice.label)
        items += UserMsg(newId(), choice.label)
        viewModelScope.launch { handle(choice.action) }
    }

    fun onSend(text: String) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        items += UserMsg(newId(), t)
        viewModelScope.launch { understand(t) }
    }

    /** Set when a command asks for a feature screen (e.g. "open calculator"); the UI navigates and clears it. */
    var openScreen by mutableStateOf<String?>(null); private set

    fun screenOpened() {
        openScreen = null
    }

    /** Opens a feature screen from outside the chat, e.g. the floating calculator's "back to app". */
    fun show(screen: String) {
        openScreen = screen
    }

    /** "Hi Janardan, how can I help you?" */
    fun helloLine() = if (userName.isBlank()) "Hi! How can I help you?" else "Hi $userName, how can I help you?"

    // ---------------------------------------------------------------- helpers

    private suspend fun say(text: String, steps: List<String> = emptyList(), think: Long = 450) {
        typing = true
        delay(think)
        typing = false
        items += AgentMsg(newId(), text, steps)
    }

    private fun offer(vararg choices: Choice) {
        items += ChoicesMsg(newId(), choices.toList())
    }

    private fun brand() = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }

    private fun isColorOs() = brand().lowercase() in setOf("realme", "oppo", "oneplus")

    private suspend fun <T> working(block: suspend () -> T): T {
        busy = true
        try {
            return block()
        } finally {
            busy = false
        }
    }

    // ---------------------------------------------------------------- flow

    private suspend fun handle(act: Act) {
        when (act) {
            Act.Menu -> menu()
            Act.Clean -> clean()
            Act.How -> howItWorks()
            Act.Setup -> setupGuide()
            Act.Recheck -> recheck()
            Act.Grant -> grant()
            Act.Review -> review()
            Act.ResetChoices -> { resetDecisions(); say("Back to my recommendations. Review the cards above and tap Apply when ready."); reviewActions() }
            Act.ApplyRecommended -> confirm(recommendedPlan())
            Act.ApplyChoices -> confirm(chosenPlan())
            Act.Confirm -> execute()
            Act.Cancel -> { say("No problem — nothing was changed."); menu() }
            Act.Trackers -> trackers()
            is Act.SetDns -> setDns(act.provider)
            is Act.ManualDns -> manualDns(act.provider)
            Act.DnsOff -> dnsOff()
            Act.DnsStatus -> dnsStatus()
            Act.GuideNext -> guideNext()
            Act.GuideBack -> { guideIdx = (guideIdx - 1).coerceAtLeast(0); showStep() }
            Act.GuideStuck -> guideStuck()
            Act.Restore -> restore()
            Act.RestoreAll -> restoreApps(store.all().keys.toList())
            is Act.RestoreOne -> restoreApps(listOf(act.pkg))
            Act.ScanQr -> { say("Opening the QR scanner — point your camera at the code.", think = 100); launch = Launch.QR }
            Act.ScanDoc -> { say("Opening the document scanner. It finds the page edges and straightens them for you.", think = 100); launch = Launch.DOC }
            Act.MakePdf -> { say("Pick the photos for your PDF — they'll be pages in the order you select them.", think = 100); launch = Launch.PHOTOS }
            Act.Weather -> weather(null)
            Act.KeepAlive -> keepAlive()
            Act.OpenSettings -> openScreen = "settings"
            Act.ClearChat -> clearChat()
            Act.OpenFiles -> openScreen = "files"
            Act.GetShizuku, Act.OpenAbout, Act.OpenDevOptions, Act.OpenNetwork, Act.OpenLocation, is Act.OpenAppInfo,
            is Act.OpenUrl, is Act.Copy, is Act.OpenPdf, is Act.SharePdf, is Act.ShareImages -> openLink(act)
        }
    }

    private suspend fun greet() {
        say(
            helloLine().replace(",", " 👋") + "\nType what you need, or tap a tool.",
            steps = listOf(
                "\"Clean my phone\" or \"block ads\" — safe, and every change can be undone.",
                "\"Scan a QR\", \"scan a document\", \"make a PDF\", \"open calculator\".",
                "\"Merge PDFs\", \"open Word\", \"new spreadsheet\", \"make a note\", \"loan EMI\", \"floating calculator\".",
                "\"What is 25 x 4\", \"what's the time\", \"clear chat\", \"open settings\".",
                if (privateMode) "🔒 Private mode is on: everything stays on this phone."
                else "🌐 Online mode: weather, currency and web tips are on.",
            ),
            think = 500,
        )
        menu()
    }

    private fun menu() = offer(
        Choice("Clean my phone", Act.Clean, recommended = true, hint = "Find & remove bloatware"),
        Choice("Block ads & trackers", Act.Trackers, hint = "System-wide, no VPN needed"),
        Choice("Restore removed apps", Act.Restore),
        Choice("What DNS am I using?", Act.DnsStatus),
        Choice("How does this work?", Act.How),
    )

    private suspend fun howItWorks() {
        say(
            "Here's what happens behind the scenes:",
            steps = listOf(
                "Shizuku gives me the same permission a computer gets over USB debugging (ADB) — no root, no warranty issues.",
                "I scan your installed apps and match them against a curated list of known bloatware, rated Recommended / Your choice / Caution.",
                "Removing uses \"pm uninstall --user 0\": the app disappears for you, but its original copy stays in the system partition. That's why it can't brick your phone, and a factory reset brings everything back.",
                "For ads & trackers I switch on Android's built-in Private DNS with a filtering server. It covers every app and browser, uses no battery and doesn't occupy your VPN slot.",
            ),
        )
        menu()
    }

    // ---- connection

    /** Returns true when Shizuku is ready; otherwise starts the right setup step. */
    private suspend fun needShizuku(next: Act): Boolean {
        afterConnect = next
        return when (ShizukuShell.state().also { shizuku = it }) {
            ShizukuShell.State.READY -> true
            ShizukuShell.State.NO_PERMISSION -> { grant(); false }
            ShizukuShell.State.NOT_RUNNING -> { setupGuide(); false }
        }
    }

    // ---- Shizuku setup coach: one step at a time, tailored to the manufacturer

    private fun phoneName() = buildString {
        append("${brand()} ${Build.MODEL}")
        if (SetupGuide.skin.isNotBlank()) append(" (${SetupGuide.skin}, Android ${Build.VERSION.RELEASE})")
        else append(" (Android ${Build.VERSION.RELEASE})")
    }

    private suspend fun setupGuide() {
        say(
            "To manage preinstalled apps without root I use Shizuku, a free open-source helper. " +
                "I'll walk you through setting it up on your ${phoneName()} — one step at a time.",
        )
        if (privateMode) {
            guide = SetupGuide.builtIn()
        } else {
            guide = SetupGuide.builtIn()
            say("Checking the internet for the latest ${brand()} tips…", think = 200)
            typing = true
            val live = working { WebHelp.keepAliveSteps() }
            typing = false
            if (live != null) {
                guide = guide.map {
                    if (it.title.startsWith("Keep Shizuku alive"))
                        it.copy(actions = live + "After every phone restart, open Shizuku and tap Start again.")
                    else it
                }
                say("✓ Got the latest ${brand()} battery settings from dontkillmyapp.com. If you get stuck, I'll search the web for you.", think = 200)
            } else {
                say("I'm offline right now, so I'll use my built-in ${brand()} guide.", think = 200)
            }
        }
        guideIdx = 0
        if (ShizukuShell.isInstalled(ctx) && guide.first().title.contains("install", ignoreCase = true)) {
            say("Shizuku is already installed ✓ — skipping that step.", think = 200)
            guideIdx = 1
        }
        showStep()
    }

    /** "It stops working when I close the app": explain what keeps running and how to keep Shizuku alive. */
    private suspend fun keepAlive() {
        say(
            "Good news: the ad blocker keeps working when Mobile Handler is closed. It's saved in your phone's own settings.\n\n" +
                "What stops is Shizuku, the helper that gives this app access. ${brand()} closes it in the background to save battery, " +
                "and Android also stops it when Wireless debugging turns off or the phone restarts.",
        )
        typing = true
        val live = if (privateMode) null else working { WebHelp.keepAliveSteps() }
        typing = false
        val steps = live ?: SetupGuide.builtIn().firstOrNull { it.title.startsWith("Keep Shizuku alive") }?.actions.orEmpty()
        say("To keep Shizuku running on your ${brand()}:", steps)
        say(
            "Do the same for Mobile Handler so it isn't closed either. After a restart, open Shizuku and tap Start once — " +
                "Android doesn't let any app restart it by itself without root.",
        )
        offer(
            Choice("Shizuku battery settings", Act.OpenAppInfo(ShizukuShell.SHIZUKU_PKG), recommended = true),
            Choice("Mobile Handler battery settings", Act.OpenAppInfo(ctx.packageName)),
            Choice("Open Shizuku", Act.GetShizuku),
            Choice("Back to menu", Act.Menu),
        )
    }

    private suspend fun showStep() {
        val s = guide[guideIdx]
        say("Step ${guideIdx + 1} of ${guide.size} · ${s.title}", steps = s.actions, think = 300)
        offerStepChoices()
    }

    private fun offerStepChoices() {
        val s = guide[guideIdx]
        val last = guideIdx == guide.lastIndex
        val choices = mutableListOf<Choice>()
        when (s.open) {
            "shizuku" -> choices += Choice(if (ShizukuShell.isInstalled(ctx)) "Open Shizuku" else "Get Shizuku", Act.GetShizuku)
            "about" -> choices += Choice("Open About phone", Act.OpenAbout)
            "dev" -> choices += Choice("Open Developer options", Act.OpenDevOptions)
            "wifi" -> choices += Choice("Open Wi-Fi settings", Act.OpenNetwork)
        }
        choices += Choice(if (last) "Done — check Shizuku" else "Done, next step", Act.GuideNext, recommended = true)
        choices += Choice("I'm stuck on this step", Act.GuideStuck)
        if (guideIdx > 0) choices += Choice("Previous step", Act.GuideBack)
        if (!last) choices += Choice("Shizuku is already running", Act.Recheck)
        offer(*choices.toTypedArray())
    }

    private suspend fun guideNext() {
        // Skip the rest as soon as Shizuku is actually up.
        if (ShizukuShell.state() != ShizukuShell.State.NOT_RUNNING || guideIdx >= guide.lastIndex) {
            guide = emptyList()
            recheck()
            return
        }
        guideIdx++
        showStep()
    }

    private suspend fun guideStuck() {
        val s = guide[guideIdx]
        say(
            "Here's what usually fixes this step:",
            steps = s.tips.ifEmpty {
                listOf(
                    "Go back one step and check every switch is still on.",
                    "Restart the phone, then open Shizuku and try again.",
                )
            },
        )
        if (!privateMode) webHelp(s.search ?: s.title, "Shizuku ${s.search ?: s.title} ${brand()} ${Build.MODEL}")
        offerStepChoices()
    }

    private suspend fun recheck() {
        when (ShizukuShell.state().also { shizuku = it }) {
            ShizukuShell.State.READY -> { say("Connected ✓"); handle(afterConnect) }
            ShizukuShell.State.NO_PERMISSION -> grant()
            ShizukuShell.State.NOT_RUNNING -> {
                say(
                    "I still can't reach Shizuku. Check these:",
                    steps = listOf(
                        "Shizuku's main screen says \"Shizuku is running\".",
                        "Wireless debugging is still on and you're on Wi-Fi.",
                        "No battery saver killed Shizuku — open it once more and tap Start.",
                        "Got a PC? Plug in USB and run: adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh",
                    ),
                )
                offer(
                    Choice("Open Shizuku", Act.GetShizuku),
                    Choice("Check again", Act.Recheck, recommended = true),
                    Choice("Back to menu", Act.Menu),
                )
            }
        }
    }

    private suspend fun grant() {
        say("Shizuku is running — I just need your permission. Tap \"Allow\" on the pop-up.")
        ShizukuShell.requestPermission()
    }

    private suspend fun onPermission(granted: Boolean) {
        shizuku = ShizukuShell.state()
        if (granted) {
            say("Permission granted ✓")
            handle(afterConnect)
        } else if (isColorOs()) {
            // Shizuku auto-denies when ColorOS permission monitoring limits ADB.
            say(
                "Permission was denied. On ${brand()} phones this usually means \"permission monitoring\" is still on, " +
                    "so Shizuku says \"the permission of adb is limited\". To fix it:",
                steps = listOf(
                    "Sign in to your ${brand()} / HeyTap account (Settings → top of the list). Without it the toggle switches itself back off.",
                    "Developer options → turn ON \"Disable permission monitoring\" (at the very bottom) and confirm.",
                    "Restart the phone, then open Shizuku and tap Start.",
                    "Come back here and tap \"Ask again\" → Allow.",
                ),
            )
            offer(
                Choice("Open Developer options", Act.OpenDevOptions),
                Choice("Ask again", Act.Grant, recommended = true),
                Choice("Back to menu", Act.Menu),
            )
        } else {
            say("Permission was denied, so I can't change any apps. You can try again whenever you like.")
            offer(Choice("Ask again", Act.Grant, recommended = true), Choice("Back to menu", Act.Menu))
        }
    }

    // ---- debloat

    private suspend fun clean() {
        if (!needShizuku(Act.Clean)) return
        typing = true
        val result = working { withContext(Dispatchers.IO) { scanner.scan() } }
        typing = false
        scan = result
        resetDecisions()

        if (result.found.isEmpty()) {
            say("I checked ${result.totalApps} apps and didn't find any known bloatware. Your phone is already clean 🎉")
            offer(Choice("Block ads & trackers", Act.Trackers, recommended = true), Choice("Back to menu", Act.Menu))
            return
        }
        val rec = result.found.count { it.info.safety == Safety.RECOMMENDED }
        val opt = result.found.count { it.info.safety == Safety.OPTIONAL }
        val cau = result.found.count { it.info.safety == Safety.CAUTION }
        say(
            "I checked ${result.totalApps} apps (${result.systemApps} preinstalled). Here's what I found:",
            steps = listOf(
                "🟢 $rec recommended to remove — ads, telemetry and junk you won't miss.",
                "🔵 $opt your choice — real apps. Remove the ones you don't use.",
                "🟠 $cau use caution — some features may stop working, so I only offer Disable.",
                "🔒 ${result.locked.size} protected — system-critical or your default apps. I won't touch them.",
            ),
        )
        offer(
            Choice("Let me review each app", Act.Review, recommended = true, hint = "You decide keep / remove"),
            Choice("Remove only the recommended ($rec)", Act.ApplyRecommended),
            Choice("Not now", Act.Menu),
        )
    }

    private fun resetDecisions() {
        decisions.clear()
        scan?.found?.forEach { decisions[it.pkg] = it.recommended ?: Decision.KEEP }
    }

    private suspend fun review() {
        val found = scan?.found.orEmpty()
        val groups = listOf(
            Triple(Safety.RECOMMENDED, "Recommended to remove", "Pure bloat. Removing is safe."),
            Triple(Safety.OPTIONAL, "Your choice", "Real apps — keep the ones you use."),
            Triple(Safety.CAUTION, "Use caution", "Disable only. Some features may stop."),
        )
        say("Tap Remove, Disable or Keep on each app. ★ is my recommendation.")
        for ((safety, title, subtitle) in groups) {
            val apps = found.filter { it.info.safety == safety }
            if (apps.isNotEmpty()) items += ReviewMsg(newId(), title, subtitle, apps)
        }
        reviewActions()
    }

    private suspend fun reviewActions() {
        say("When you're happy with your choices:", think = 250)
        offer(
            Choice("Apply my choices", Act.ApplyChoices, recommended = true),
            Choice("Reset to recommended", Act.ResetChoices),
            Choice("Cancel", Act.Cancel),
        )
    }

    private fun recommendedPlan() = scan?.found.orEmpty()
        .filter { it.recommended == Decision.REMOVE }
        .map { it to Decision.REMOVE }

    private fun chosenPlan() = scan?.found.orEmpty()
        .mapNotNull { app ->
            val d = decisions[app.pkg] ?: Decision.KEEP
            if (d == Decision.KEEP || d !in app.allowed) null else app to d
        }

    private suspend fun confirm(newPlan: List<Pair<FoundApp, Decision>>) {
        plan = newPlan
        if (plan.isEmpty()) {
            say("You chose to keep everything, so there's nothing to change.")
            offer(Choice("Review again", Act.Review, recommended = true), Choice("Back to menu", Act.Menu))
            return
        }
        val lines = plan.take(12).map { (app, d) -> "${verb(d)} ${app.label}" } +
            if (plan.size > 12) listOf("…and ${plan.size - 12} more") else emptyList<String>()
        say("Here's my plan:", steps = lines)
        say(
            "Removed apps keep their original copy in the system, so this can't brick your phone. " +
                "You can bring any of them back later from \"Restore removed apps\".",
            think = 300,
        )
        offer(
            Choice("Yes, apply ${plan.size} change${if (plan.size == 1) "" else "s"}", Act.Confirm, recommended = true),
            Choice("Change my choices", Act.Review),
            Choice("Cancel", Act.Cancel),
        )
    }

    private fun verb(d: Decision) = when (d) {
        Decision.REMOVE -> "Remove"
        Decision.DISABLE -> "Disable"
        Decision.KEEP -> "Keep"
    }

    private suspend fun execute() {
        if (!needShizuku(Act.Confirm)) return
        val lines = mutableListOf<ResultLine>()
        var removed = 0
        var disabled = 0
        working {
            val progId = newId()
            items += ProgressMsg(progId, "Starting…", 0, plan.size)
            plan.forEachIndexed { i, (app, d) ->
                replace(progId) { ProgressMsg(progId, "${verb(d)} ${app.label}…", i, plan.size) }
                val blocked = Guard.blockReason(ctx, app.pkg)
                if (blocked != null) {
                    lines += ResultLine("${app.label}: skipped ($blocked)", false)
                    return@forEachIndexed
                }
                val r = withContext(Dispatchers.IO) {
                    if (d == Decision.REMOVE) ShizukuShell.remove(app.pkg) else ShizukuShell.disable(app.pkg)
                }
                if (r.ok) {
                    store.record(app.pkg, d)
                    if (d == Decision.REMOVE) removed++ else disabled++
                    lines += ResultLine("${verb(d).dropLast(1)}ed ${app.label}", true)
                } else {
                    lines += ResultLine("${app.label}: ${r.output.lineSequence().firstOrNull() ?: "failed"}", false)
                }
            }
            replace(progId) { ProgressMsg(progId, "Finished", plan.size, plan.size) }
            items += ResultMsg(newId(), "Results", lines)
            scan = withContext(Dispatchers.IO) { scanner.scan() }
            resetDecisions()
        }
        val failed = lines.count { !it.ok }
        say(
            buildString {
                append("Done! Removed $removed")
                if (disabled > 0) append(" and disabled $disabled")
                append(" app${if (removed + disabled == 1) "" else "s"}.")
                if (failed > 0) append(
                    " $failed couldn't be changed" +
                        when {
                            brand().equals("Xiaomi", true) -> " — on Xiaomi, turn on \"USB debugging (Security settings)\" and try again."
                            isColorOs() -> " — turn on \"Disable permission monitoring\" in Developer options and try again."
                            else -> "."
                        },
                )
            },
        )
        say("Next, shall I block ads & trackers across your whole phone?", think = 300)
        offer(
            Choice("Block ads & trackers", Act.Trackers, recommended = true),
            Choice("Back to menu", Act.Menu),
        )
    }

    private inline fun replace(id: Long, make: () -> ChatItem) {
        val idx = items.indexOfFirst { it.id == id }
        if (idx >= 0) items[idx] = make()
    }

    // ---- trackers

    private suspend fun trackers() {
        say("Let me check which of your apps carry tracking or ad SDKs…", think = 300)
        typing = true
        val hits = working { withContext(Dispatchers.IO) { TrackerScanner(ctx).scan() } }
        typing = false
        if (hits.isEmpty()) {
            say("I didn't spot any known tracker SDKs in your installed apps. Blocking at the DNS level still stops trackers in websites.")
        } else {
            val total = hits.sumOf { it.trackers.size }
            say(
                "I found $total tracker/ad SDKs inside ${hits.size} apps. The worst offenders:",
                steps = hits.take(6).map { "${it.label} — ${it.trackers.joinToString(", ")}" },
            )
        }
        say(
            "The best way to block them is Android's Private DNS: it filters every app and browser, " +
                "uses no battery and doesn't take your VPN slot. Pick a filter:",
        )
        offer(
            Choice(DnsProvider.ADGUARD.title, Act.SetDns(DnsProvider.ADGUARD), recommended = true, hint = DnsProvider.ADGUARD.blurb),
            Choice(DnsProvider.ADGUARD_FAMILY.title, Act.SetDns(DnsProvider.ADGUARD_FAMILY), hint = DnsProvider.ADGUARD_FAMILY.blurb),
            Choice(DnsProvider.MULLVAD.title, Act.SetDns(DnsProvider.MULLVAD), hint = DnsProvider.MULLVAD.blurb),
            Choice("Turn Private DNS off", Act.DnsOff, hint = "Undo ad blocking"),
        )
    }

    private suspend fun setDns(p: DnsProvider) {
        if (ShizukuShell.state() != ShizukuShell.State.READY) {
            say("Shizuku isn't connected, but you can still do this yourself in under a minute.")
            offer(
                Choice("Show me how (1 min)", Act.ManualDns(p), recommended = true),
                Choice("Connect Shizuku instead", Act.Setup),
            )
            afterConnect = Act.SetDns(p)
            return
        }
        val r = working {
            withContext(Dispatchers.IO) {
                ShizukuShell.run("settings put global private_dns_specifier ${p.host} && settings put global private_dns_mode hostname")
                ShizukuShell.run("settings get global private_dns_mode; settings get global private_dns_specifier")
            }
        }
        // Both must stick: a saved hostname does nothing while the mode is "off".
        if (r.output.lines().map { it.trim() } == listOf("hostname", p.host)) {
            say(
                "✓ Private DNS is now ${p.title} (${p.host}).",
                steps = listOf(
                    "Ads and trackers are blocked in apps, games and browsers — on Wi-Fi and mobile data.",
                    "Ads served from the same servers as the content (YouTube, Instagram) can't be blocked by DNS.",
                    "If a site or app misbehaves, come back and choose \"Turn Private DNS off\".",
                ),
            )
            menu()
        } else {
            say("Android didn't accept that setting (${r.output.ifBlank { "no response" }}). Let's do it by hand.")
            manualDns(p)
        }
    }

    private suspend fun manualDns(p: DnsProvider) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Private DNS", p.host))
        say(
            "I copied \"${p.host}\" to your clipboard. Now:",
            steps = listOf(
                "Tap \"Open network settings\" below.",
                "Find \"Private DNS\" (on Samsung: Connections → More connection settings; on Xiaomi: Connection & sharing).",
                "Choose \"Private DNS provider hostname\".",
                "Paste and tap Save.",
            ),
        )
        offer(
            Choice("Open network settings", Act.OpenNetwork),
            Choice("Done", Act.Menu, recommended = true),
        )
    }

    private suspend fun dnsOff() {
        if (ShizukuShell.state() == ShizukuShell.State.READY) {
            working { withContext(Dispatchers.IO) { ShizukuShell.run("settings put global private_dns_mode opportunistic") } }
            say("Private DNS is back to Automatic. Ad blocking is off.")
            menu()
        } else {
            say("Open network settings → Private DNS → choose \"Automatic\".")
            offer(Choice("Open network settings", Act.OpenNetwork), Choice("Done", Act.Menu, recommended = true))
        }
    }

    /** Reports the DNS the phone is actually using right now, from the active network's link properties. */
    private suspend fun dnsStatus() {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val net = cm.activeNetwork
        val lp = net?.let { cm.getLinkProperties(it) }
        val caps = net?.let { cm.getNetworkCapabilities(it) }
        val via = when {
            caps == null -> null
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile data"
            else -> "your network"
        }
        if (lp == null || via == null) {
            say("You're not connected to any network right now, so no DNS server is in use.")
            menu()
            return
        }

        // The saved setting can differ from what's in use (e.g. a hostname saved while the mode is "off").
        val (mode, saved) = if (ShizukuShell.state() == ShizukuShell.State.READY) {
            val lines = withContext(Dispatchers.IO) {
                ShizukuShell.run("settings get global private_dns_mode; settings get global private_dns_specifier")
            }.output.lines().map { it.trim() }
            lines.getOrNull(0) to lines.getOrNull(1)
        } else {
            runCatching { Settings.Global.getString(ctx.contentResolver, "private_dns_mode") }.getOrNull() to
                runCatching { Settings.Global.getString(ctx.contentResolver, "private_dns_specifier") }.getOrNull()
        }
        val servers = lp.dnsServers.mapNotNull { it.hostAddress }
        val serverList = servers.joinToString(", ").ifBlank { "unknown" }
        val activeHost = lp.privateDnsServerName

        fun describe(host: String) = when (host) {
            DnsProvider.ADGUARD.host, "dns.adguard.com" -> "AdGuard DNS — blocks ads & trackers"
            DnsProvider.ADGUARD_FAMILY.host, "dns-family.adguard.com" -> "AdGuard Family — blocks ads, trackers & adult sites"
            DnsProvider.MULLVAD.host -> "Mullvad Ad-block — blocks ads & trackers"
            else -> "a custom provider — blocking depends on that provider"
        }

        when {
            lp.isPrivateDnsActive && activeHost != null -> {
                say(
                    "You're using Private DNS: $activeHost",
                    steps = listOf(
                        "Provider: ${describe(activeHost)}.",
                        "Connection: $via — lookups are encrypted (DNS-over-TLS).",
                        "Underlying servers: $serverList",
                    ),
                )
                offer(Choice("Change DNS filter", Act.Trackers), Choice("Back to menu", Act.Menu, recommended = true))
            }
            lp.isPrivateDnsActive -> {
                say(
                    "Private DNS is on Automatic, so you're using your $via DNS: $serverList",
                    steps = listOf(
                        "Lookups are encrypted when that server supports it.",
                        "No ads or trackers are blocked.",
                    ),
                )
                offer(Choice("Turn on AdGuard DNS", Act.SetDns(DnsProvider.ADGUARD), recommended = true), Choice("Back to menu", Act.Menu))
            }
            else -> {
                say(
                    "Private DNS is off, so you're using your $via DNS: $serverList",
                    steps = listOfNotNull(
                        "Lookups aren't encrypted and no ads or trackers are blocked.",
                        saved?.takeIf { mode == "off" && it.isNotBlank() && it != "null" }
                            ?.let { "\"$it\" is saved in settings but switched off." },
                    ),
                )
                offer(Choice("Turn on AdGuard DNS", Act.SetDns(DnsProvider.ADGUARD), recommended = true), Choice("Back to menu", Act.Menu))
            }
        }
    }

    // ---- restore

    private suspend fun restore() {
        val changes = store.all()
        if (changes.isEmpty()) {
            say("I haven't removed or disabled anything yet, so there's nothing to restore.")
            menu()
            return
        }
        say(
            "These are the apps I changed. Pick one to bring back, or restore them all:",
            steps = changes.map { (pkg, d) -> "${scanner.labelOf(pkg)} — ${if (d == Decision.REMOVE) "removed" else "disabled"}" },
        )
        offer(
            *changes.keys.take(8).map { Choice("Restore ${scanner.labelOf(it)}", Act.RestoreOne(it)) }.toTypedArray(),
            Choice("Restore all (${changes.size})", Act.RestoreAll, recommended = true),
            Choice("Back to menu", Act.Menu),
        )
    }

    private suspend fun restoreApps(pkgs: List<String>) {
        if (!needShizuku(Act.Restore)) return
        val changes = store.all()
        val lines = working {
            pkgs.mapNotNull { pkg ->
                val d = changes[pkg] ?: return@mapNotNull null
                val label = scanner.labelOf(pkg)
                val r = withContext(Dispatchers.IO) {
                    if (d == Decision.REMOVE) ShizukuShell.reinstall(pkg) else ShizukuShell.enable(pkg)
                }
                if (r.ok) store.forget(pkg)
                ResultLine(if (r.ok) "Restored $label" else "$label: ${r.output.lineSequence().firstOrNull()}", r.ok)
            }
        }
        items += ResultMsg(newId(), "Restore", lines)
        say("All set. Restored apps are back in your app drawer.")
        menu()
    }

    // ---- free text

    private suspend fun understand(text: String) {
        val t = text.lowercase()
        // Office and calculator tools, before the generic "open …" match and the Photos → PDF rule below.
        when {
            Regex("""\bfloat\w*\b.*\bcalc\w*|\bcalc\w*\b.*\b(float\w*|overlay|bubble)""").containsMatchIn(t) -> {
                say("Opening the floating calculator. It stays on top of other apps — drag it by its top bar, ✕ closes it.", think = 100)
                FloatingCalcService.start(ctx)
                return
            }
            Regex("""\bscientific\b|\b(sin|cos|tan|log)\b.*\bcalc""").containsMatchIn(t) -> {
                CalcMemory.requestedTab = 1
                return openFeature(Features.byId("calculator")!!)
            }
            Regex("""\b(emi|sip|gst|loan|compound interest|finance calc\w*|financial calc\w*)\b""").containsMatchIn(t) -> {
                CalcMemory.requestedTab = 2
                return openFeature(Features.byId("calculator")!!)
            }
            Regex("""\b(excel|spreadsheets?|xlsx|csv)\b""").containsMatchIn(t) -> return openFeature(Features.byId("excel")!!)
            // "Make a note", "my notes", "to-do list" (but "scan my notes" is still the document scanner).
            Regex("""\b(notes?|notepad|checklists?|to-?do( list)?s?|memos?|jot)\b""").containsMatchIn(t) && !Regex("""\bscan\w*|\b(release|patch|update|version) notes?\b""").containsMatchIn(t) ->
                return openFeature(Features.byId("notes")!!)
            Regex("""\b(docx|ms word|word (doc|document|file)s?|write an? (document|letter))\b""").containsMatchIn(t) -> return openFeature(Features.byId("word")!!)
            Regex("""\bpdfs?\b""").containsMatchIn(t) && !Regex("""\b(photos?|images?|pictures?|pics?|jpe?gs?|scan\w*)\b""").containsMatchIn(t) &&
                Regex("""\b(read|view|open|edit|merge|combine|join|split|extract|compress|shrink|reduce|annotate|highlight|draw|sign|remove|delete)\b|reader|editor|viewer""").containsMatchIn(t) ->
                return openFeature(Features.byId("pdfreader")!!)
        }
        // "Open calculator", "start the QR scanner": jump straight to that feature.
        Regex("^(?:please\\s+)?(?:open|launch|start|show|go to)\\s+(?:the\\s+|my\\s+)?(.+)$").find(t)?.let { m ->
            val wanted = m.groupValues[1].trim()
            // Best match = most title words mentioned ("scan document" -> Scan Document, not Scan QR).
            Features.all
                .map { f -> f to f.title.lowercase().split(" ", "→").count { w -> w.length >= 2 && wanted.contains(w) } + if (wanted.contains(f.id)) 2 else 0 }
                .filter { it.second > 0 }
                .maxByOrNull { it.second }
                ?.let { return openFeature(it.first) }
        }
        if (Regex("^(calculator|calc|currency|currency converter|exchange rates?)$").matches(t.trim())) return openFeature(Features.byId("calculator")!!)

        // Tools first: "scan a QR" must not start a bloatware scan, "weather" must not match the Weather app.
        when {
            Regex("\\b(qr|barcode|bar code)\\b").containsMatchIn(t) -> return handle(Act.ScanQr)
            Regex("\\bscan\\w*\\b.*\\b(doc\\w*|page|paper|receipt|bill|form|notes?)\\b").containsMatchIn(t) -> return handle(Act.ScanDoc)
            Regex("\\bpdf\\b").containsMatchIn(t) -> return handle(Act.MakePdf)
            Regex("\\b(weather|temperature|forecast|rain\\w*|humid\\w*)\\b").containsMatchIn(t) ->
                return weather(Answers.cityIn(text))
            Regex("\\b(what(?:'s| is) the time|time now|current time|what time is it)\\b").containsMatchIn(t) ->
                return say("It's ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())}.")
            Regex("\\b(today'?s date|what(?:'s| is) the date|what day is (it|today))\\b").containsMatchIn(t) ->
                return say("Today is ${DateFormat.getDateInstance(DateFormat.FULL).format(Date())}.")
        }
        if (Regex("(when|after|if) (i )?(close|exit|swipe)|in (the )?background|keep (it |shizuku )?(alive|running)|stops? working").containsMatchIn(t)) return keepAlive()
        if (Regex("^(open |show )?(settings?|preferences)$").matches(t.trim())) return handle(Act.OpenSettings)
        if (Regex("\\b(clear|delete|erase|reset)\\b.*\\b(chat|history|conversation|messages)\\b").containsMatchIn(t)) return clearChat()
        Regex("^(?:my name is|call me)\\s+([a-z][a-z .'-]{0,30})$").find(t.trim())?.let { m ->
            val name = m.groupValues[1].trim().split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
            userName = name
            prefs.edit().putString("user_name", name).apply()
            return say("Nice to meet you, $name! How can I help you?")
        }
        if (privateMode && Regex("\\d\\s*[a-z₹$€£]+\\s+(to|in|into)\\s+[a-z]{3,}").containsMatchIn(t))
            return needsInternet("Currency conversion")
        Currency.convert(text)?.let { return say(it) }
        Answers.calculate(text)?.let { return say(it) }

        val app = scan?.found?.firstOrNull { f ->
            t.contains(f.pkg) || (f.label.length >= 3 && t.contains(f.label.lowercase()))
        } ?: BloatDb.all.values.firstOrNull { t.contains(it.pkg) || t.contains(it.name.lowercase()) }
            ?.let { FoundApp(it, it.name, true) }

        when {
            app != null -> explain(app)
            t.contains("dns") &&
                Regex("\\b(what|which|current\\w*|my|show|check|using|status|address|server)\\b").containsMatchIn(t) -> dnsStatus()
            Regex("\\b(clean|bloat\\w*|debloat|scan|remove|uninstall|junk)\\b").containsMatchIn(t) -> clean()
            Regex("\\b(ads?|tracker\\w*|tracking|dns|block\\w*|privacy)\\b").containsMatchIn(t) -> trackers()
            Regex("\\b(restore|undo|bring back|reinstall)\\b").containsMatchIn(t) -> restore()
            Regex("\\b(shizuku|setup|set up|connect|adb)\\b").containsMatchIn(t) -> setupGuide()
            Regex("\\b(safe|brick)\\b|how does (this|it) work").containsMatchIn(t) -> howItWorks()
            else -> onlyAppFeatures()
        }
    }

    /** Anything that isn't one of the app's features: say so and show what it can do. */
    private suspend fun onlyAppFeatures() {
        say(
            "Sorry, I can only help with Mobile Handler's features.",
            steps = listOf(
                "Clean bloatware, block ads, restore removed apps.",
                "Scan a QR code or a document, make a PDF, open My Files.",
                "Calculator and currency, weather, time and date, settings.",
            ),
        )
        offer(
            Choice("Clean my phone", Act.Clean, recommended = true),
            Choice("Block ads & trackers", Act.Trackers),
            Choice("Scan a document", Act.ScanDoc),
            Choice("Open My Files", Act.OpenFiles),
        )
    }

    // ---- tools: QR, document scan, photos -> PDF, weather

    /** Set when the chat needs MainActivity to open a scanner, picker or permission prompt. */
    var launch by mutableStateOf<Launch?>(null); private set

    fun launchHandled() {
        launch = null
    }

    /** A tool icon was tapped: show it as the user's message, then run it. */
    fun onTool(act: Act, label: String) {
        if (busy) return
        items += UserMsg(newId(), label)
        viewModelScope.launch { handle(act) }
    }

    fun onQr(scan: QrScan?, error: String?) = viewModelScope.launch {
        when {
            scan != null -> {
                val (title, details) = when {
                    scan.wifiName != null -> "📶 Wi-Fi network: ${scan.wifiName}" to
                        listOfNotNull(scan.wifiPassword?.let { "Password: $it" })
                    scan.url != null -> "🔗 Link to ${Uri.parse(scan.url).host ?: scan.url}" to
                        listOf(scan.url, "Only open links you trust — check the address first.")
                    scan.phone != null -> "📞 Phone number" to listOf(scan.phone)
                    else -> "📄 Text" to listOf(scan.raw)
                }
                say(title, steps = details, think = 100)
                offer(
                    *(listOfNotNull(
                        scan.url?.let { Choice("Open link", Act.OpenUrl(it), hint = Uri.parse(it).host) },
                        scan.wifiPassword?.let { Choice("Copy password", Act.Copy(it), recommended = true) },
                        scan.phone?.let { Choice("Call", Act.OpenUrl("tel:$it")) },
                        Choice("Copy", Act.Copy(scan.url ?: scan.raw), recommended = scan.wifiPassword == null),
                        Choice("Scan another", Act.ScanQr),
                    )).toTypedArray(),
                )
            }
            error != null -> {
                say(error)
                offer(Choice("Try again", Act.ScanQr, recommended = true), Choice("Back to menu", Act.Menu))
            }
            else -> say("Scan cancelled.")
        }
    }

    fun onDocScanned(pdf: Uri?, pages: Int, images: List<Uri>, error: String?) = viewModelScope.launch {
        when {
            pdf != null -> {
                typing = true
                val saved = runCatching { working { PdfTools.keep(ctx, pdf, pages, images) } }
                typing = false
                saved.onSuccess { showPdf(it) }.onFailure { say("⚠️ Couldn't save the scan: ${it.message}") }
            }
            error != null -> {
                say(error)
                offer(Choice("Try again", Act.ScanDoc, recommended = true), Choice("Back to menu", Act.Menu))
            }
            else -> say("Scan cancelled.")
        }
    }

    fun onImagesPicked(images: List<Uri>) = viewModelScope.launch {
        if (images.isEmpty()) return@launch say("No photos picked.")
        say("Making a PDF from ${images.size} photo${if (images.size == 1) "" else "s"}…", think = 100)
        typing = true
        val saved = runCatching { working { PdfTools.fromImages(ctx, images) } }
        typing = false
        saved.onSuccess { showPdf(it) }.onFailure { say("⚠️ Couldn't make the PDF: ${it.message}") }
    }

    private suspend fun showPdf(pdf: SavedPdf) {
        say(
            "✓ Saved ${pdf.name}",
            steps = listOf("${pdf.pages} page${if (pdf.pages == 1) "" else "s"} · in ${pdf.where}"),
            think = 100,
        )
        offer(
            Choice("Open PDF", Act.OpenPdf(pdf.uri.toString()), recommended = true),
            Choice("Share PDF", Act.SharePdf(pdf.uri.toString())),
            Choice("Share as images", Act.ShareImages(pdf.dir)),
            Choice("Open My Files", Act.OpenFiles),
        )
    }

    fun onLocationPermission(granted: Boolean) = viewModelScope.launch {
        if (granted) weather(null)
        else {
            say("No problem — tell me a city instead, like \"weather in Mumbai\".")
        }
    }

    private suspend fun weather(asked: String?) {
        if (privateMode) return needsInternet("Weather")
        // No city said: use the place from your profile before asking for location.
        val city = asked ?: userPlace.takeIf { it.isNotBlank() }
        if (city == null && ctx.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            say("To check the weather where you are, allow approximate location (only your rough area). Or type a city, like \"weather in Delhi\".")
            launch = Launch.LOCATION
            return
        }
        if (city == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            !ctx.getSystemService(android.location.LocationManager::class.java).isLocationEnabled
        ) {
            say("Location is turned off on your phone. Turn it on and tap Weather again — or type a city, like \"weather in Delhi\".")
            offer(Choice("Turn on Location", Act.OpenLocation, recommended = true))
            return
        }
        typing = true
        val result = working {
            if (city != null) Answers.weatherForCity(city)
            else currentLocation()?.let { Answers.weatherAt(it.latitude, it.longitude) }
        }
        typing = false
        if (result == null) {
            say(
                if (city != null) "I couldn't find weather for \"$city\". Check the spelling or try a bigger city nearby."
                else "I couldn't get your location or the forecast. Turn on Location, or type a city like \"weather in Delhi\".",
            )
            return
        }
        say("${result.now} in ${result.place}", steps = result.details)
    }

    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun currentLocation(): android.location.Location? {
        val lm = ctx.getSystemService(android.location.LocationManager::class.java)
        val last = listOf(
            android.location.LocationManager.NETWORK_PROVIDER,
            android.location.LocationManager.PASSIVE_PROVIDER,
            android.location.LocationManager.GPS_PROVIDER,
        ).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 3 * 60 * 60 * 1000) return last
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            !lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        ) return last
        return withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { cont ->
                lm.getCurrentLocation(android.location.LocationManager.NETWORK_PROVIDER, null, ctx.mainExecutor) {
                    cont.resume(it)
                }
            }
        } ?: last
    }

    private suspend fun openFeature(f: Feature) {
        when (val o = f.opens) {
            is Opens.Screen -> {
                say("Opening ${f.title}.", think = 100)
                openScreen = f.id
            }
            is Opens.Assistant -> o.act?.let { handle(it) } ?: say("I'm here — what would you like to do?")
        }
    }

    /** Searches the web (no API key needed) and offers the most helpful results as buttons. */
    /** Private mode blocks this feature: say so, and how to allow it. */
    private suspend fun needsInternet(feature: String) {
        say("$feature needs the internet. Private mode is on, so nothing leaves your phone. You can turn it off in Settings.")
        offer(Choice("Open Settings", Act.OpenSettings), Choice("Back to menu", Act.Menu))
    }

    private suspend fun webHelp(topic: String, searchQuery: String) {
        if (privateMode) return needsInternet("Searching the web for help")
        say("Searching the web for ${brand()} users with the same problem…", think = 200)
        typing = true
        val tips = working { WebHelp.search(topic) }
        typing = false
        if (tips.isEmpty()) {
            say("I couldn't find a close match online. These searches are set up for your exact phone:")
        } else {
            say(
                "Here are the most helpful things I found, best first:",
                steps = tips.map { "${it.title} — ${it.source} (${it.note})" },
            )
        }
        offer(
            *(tips.mapIndexed { i, tip ->
                Choice(tip.title.take(60), Act.OpenUrl(tip.url), recommended = i == 0, hint = tip.source)
            } + listOf(
                Choice("Watch a video guide", Act.OpenUrl(WebHelp.youtubeUrl(searchQuery)), hint = "YouTube"),
                Choice("Search Google", Act.OpenUrl(WebHelp.googleUrl(searchQuery))),
                Choice("Official Shizuku guide", Act.OpenUrl(WebHelp.SHIZUKU_GUIDE)),
            )).toTypedArray(),
        )
    }

    private suspend fun explain(app: FoundApp) {
        val verdict = when (app.info.safety) {
            Safety.RECOMMENDED -> "★ My recommendation: remove it. Nothing depends on it."
            Safety.OPTIONAL -> "It's your call — safe to remove if you don't use it."
            Safety.CAUTION -> "★ My recommendation: keep it. If you want it gone, disable it rather than remove it."
        }
        val installed = scan?.found?.any { it.pkg == app.pkg } == true
        say(
            "${app.label} (${app.pkg})",
            steps = listOf(app.info.why, verdict) +
                if (installed) emptyList<String>() else listOf("It isn't active on your phone right now."),
        )
        if (installed) offer(Choice("Review all apps", Act.Review, recommended = true), Choice("Back to menu", Act.Menu))
        else menu()
    }

    // ---- links

    private fun openLink(act: Act) {
        when (act) {
            is Act.Copy -> {
                ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Mobile Handler", act.text))
                Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                return
            }
            is Act.OpenPdf -> {
                val view = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(act.uri), "application/pdf")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    ctx.startActivity(view)
                } catch (e: Exception) {
                    Toast.makeText(ctx, "No PDF viewer installed — use Share instead.", Toast.LENGTH_LONG).show()
                }
                return
            }
            is Act.ShareImages -> {
                MyFiles.byPath(act.dir)?.let { MyFiles.shareImages(ctx, it) }
                return
            }
            is Act.SharePdf -> {
                val send = Intent(Intent.ACTION_SEND).setType("application/pdf")
                    .putExtra(Intent.EXTRA_STREAM, Uri.parse(act.uri))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                ctx.startActivity(Intent.createChooser(send, "Share PDF").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }
            else -> Unit
        }
        val intent = when (act) {
            Act.GetShizuku -> ctx.packageManager.getLaunchIntentForPackage(ShizukuShell.SHIZUKU_PKG)
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${ShizukuShell.SHIZUKU_PKG}"))
            Act.OpenAbout -> Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
            Act.OpenDevOptions -> Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            Act.OpenNetwork -> Intent(Settings.ACTION_WIRELESS_SETTINGS)
            Act.OpenLocation -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            is Act.OpenAppInfo -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${act.pkg}"))
            is Act.OpenUrl -> Intent(Intent.ACTION_VIEW, Uri.parse(act.url))
            else -> return
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallback = if (act == Act.GetShizuku) Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
        else Intent(Settings.ACTION_SETTINGS)
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            runCatching { ctx.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}

