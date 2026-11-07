package com.cleanai.debloat.agent

import com.cleanai.debloat.data.FoundApp

sealed interface ChatItem { val id: Long }

data class AgentMsg(override val id: Long, val text: String, val steps: List<String> = emptyList()) : ChatItem
data class UserMsg(override val id: Long, val text: String) : ChatItem
data class ChoicesMsg(override val id: Long, val choices: List<Choice>, val picked: String? = null) : ChatItem
data class ReviewMsg(override val id: Long, val title: String, val subtitle: String, val apps: List<FoundApp>) : ChatItem
data class ProgressMsg(override val id: Long, val label: String, val done: Int, val total: Int) : ChatItem
data class ResultMsg(override val id: Long, val title: String, val lines: List<ResultLine>) : ChatItem
data class ResultLine(val text: String, val ok: Boolean)

data class Choice(
    val label: String,
    val action: Act,
    val recommended: Boolean = false,
    val hint: String? = null,
)

enum class DnsProvider(val title: String, val host: String, val blurb: String) {
    ADGUARD("AdGuard DNS", "dns.adguard-dns.com", "Blocks ads & trackers"),
    ADGUARD_FAMILY("AdGuard Family", "family.adguard-dns.com", "Ads, trackers + adult sites"),
    MULLVAD("Mullvad Ad-block", "adblock.dns.mullvad.net", "Privacy-first, no logging"),
}

sealed interface Act {
    /** Link actions open a settings screen or store page; they don't advance the conversation. */
    val isLink: Boolean get() = false

    data object Menu : Act
    data object Clean : Act
    data object How : Act
    data object Setup : Act
    data object Recheck : Act
    data object Grant : Act
    data object ApplyRecommended : Act
    data object Review : Act
    data object ResetChoices : Act
    data object ApplyChoices : Act
    data object Confirm : Act
    data object Cancel : Act
    data object Trackers : Act
    data class SetDns(val provider: DnsProvider) : Act
    data class ManualDns(val provider: DnsProvider) : Act
    data object DnsOff : Act
    data object DnsStatus : Act
    data object GuideNext : Act
    data object GuideBack : Act
    data object GuideStuck : Act
    data object Restore : Act
    data object RestoreAll : Act
    data class RestoreOne(val pkg: String) : Act
    data object ScanQr : Act
    data object ScanDoc : Act
    data object MakePdf : Act
    data object Weather : Act
    data object KeepAlive : Act
    data object OpenSettings : Act
    data object ClearChat : Act
    data object OpenFiles : Act

    data object GetShizuku : Act { override val isLink get() = true }
    data object OpenAbout : Act { override val isLink get() = true }
    data object OpenDevOptions : Act { override val isLink get() = true }
    data object OpenNetwork : Act { override val isLink get() = true }
    data object OpenLocation : Act { override val isLink get() = true }
    data class OpenAppInfo(val pkg: String) : Act { override val isLink get() = true }
    data class OpenUrl(val url: String) : Act { override val isLink get() = true }
    data class Copy(val text: String) : Act { override val isLink get() = true }
    data class OpenPdf(val uri: String) : Act { override val isLink get() = true }
    data class SharePdf(val uri: String) : Act { override val isLink get() = true }
    data class ShareImages(val dir: String) : Act { override val isLink get() = true }
    data class RenameDoc(val dir: String) : Act { override val isLink get() = true }
}

/** Screens the chat asks MainActivity to open; their results come back to the view model. */
enum class Launch { QR, DOC, PHOTOS, LOCATION }

/** What the QR scanner read, with the parts the chat can act on. */
data class QrScan(
    val raw: String,
    val url: String? = null,
    val wifiName: String? = null,
    val wifiPassword: String? = null,
    val phone: String? = null,
)
