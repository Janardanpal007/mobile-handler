package com.cleanai.debloat.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat

private val Policy = listOf(
    "Who we are" to
        "Mobile Handler is developed and maintained by $DEVELOPER. For questions about this app or this policy, contact the developer through the links above.",
    "On your phone first" to
        "Scanning, PDFs, Word, Excel, notes, the calculator, the photo tools and the phone cleaner all work on your phone, without the internet.",
    "What stays on your phone" to
        "Your name, age and place, your installed-apps list, the changes you make, scanned QR codes, scanned documents, PDFs, calculator history and the chat. We have no account system and no server. Clear the chat any time from the Assistant or Settings.",
    "Camera and scanning" to
        "QR and document scanning run on the phone through Google Play services; Mobile Handler only receives the result.",
    "When the app goes online" to
        "Only when you use these features: questions go to DuckDuckGo, Wikipedia, GitHub or Stack Exchange; weather sends a city or rough location to Open-Meteo; " +
        "currency rates come from open.er-api.com. About once a day the app also asks GitHub whether a new version is out (turn off update notifications in Settings to stop it). " +
        "Nothing about you is sent with any of these.",
    "Ad blocker" to
        "The ad blocker works by setting your phone's Private DNS to a provider you choose (AdGuard or Mullvad). Like any DNS service, that provider sees the website names your phone looks up. It is off unless you turn it on.",
    "Your control" to
        "Every app removal can be undone from Restore Apps. You can turn ad blocking off at any time, remove your AI key in Settings, and delete saved PDFs from Downloads.",
)

/** "1.9 (12)": the installed version name and build number, read from the app itself. */
private fun installedVersion(ctx: Context): String = runCatching {
    val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
    "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
}.getOrDefault("unknown")

private fun openLink(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Toast.makeText(ctx, "No app can open $url", Toast.LENGTH_SHORT).show() }
}

/** About the app: identity, the developer, what's new, permissions, licences and the privacy policy. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val version = remember { installedVersion(ctx) }
    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        ScreenTopBar("About", onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Identity(version)
            SectionLabel("Mobile Handler")
            LinkGroup(ProjectLinks) { openLink(ctx, it) }
            SectionLabel("Developer · $DEVELOPER")
            LinkGroup(DeveloperLinks) { openLink(ctx, it) }
            SectionLabel("More")
            Folding("What's new", "Version ${Changelog.first().version}") {
                Changelog.forEachIndexed { i, r ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    Text("${r.version} · ${r.date}", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    r.notes.forEach { Bullet(it) }
                }
            }
            Folding("Permissions", "What each one is for") {
                PermissionReasons.forEachIndexed { i, (name, why) ->
                    if (i > 0) Spacer(Modifier.height(8.dp))
                    Text(name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(why, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
            Folding("Open-source licences", "Libraries and models in this app") {
                OpenSourceLicenses.forEach { l ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { openLink(ctx, l.url) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(l.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(l.license, color = TextSecondary, fontSize = 12.sp)
                        }
                        Text("↗", color = TextSecondary, fontSize = 16.sp)
                    }
                }
                Text("Sparky's pictures were made with ChatGPT image generation.", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Folding("Privacy policy", "No account, no ads, no tracking") {
                Policy.forEachIndexed { i, (title, body) ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(body, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
            Text(
                "Designed with care for Android.\n© 2026 $DEVELOPER",
                color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun Identity(version: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CardHi).border(1.dp, Line, RoundedCornerShape(24.dp))
            .padding(vertical = 22.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SparkyPortrait(timeMood(rememberHour()), Modifier.size(96.dp), description = "Sparky, the Mobile Handler mascot")
        Spacer(Modifier.height(10.dp))
        Text("Mobile Handler", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text(TAGLINE, color = TextSecondary, fontSize = 15.sp)
        Spacer(Modifier.height(12.dp))
        StatusChip("Version $version", Accent, background = Card, textColor = TextSecondary)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
}

@Composable
private fun LinkGroup(links: List<AboutLink>, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardHi).border(1.dp, Line, RoundedCornerShape(20.dp))) {
        links.forEachIndexed { i, link ->
            if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 70.dp).height(1.dp).background(Line))
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Open ${link.name}") { onOpen(link.url) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
                    Text(link.name.take(1), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(link.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(link.detail, color = TextSecondary, fontSize = 13.sp)
                }
                Text("↗", color = TextSecondary, fontSize = 18.sp)
            }
        }
    }
}

/** A card that opens to show its content when tapped. */
@Composable
private fun Folding(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardHi).border(1.dp, Line, RoundedCornerShape(20.dp))) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button) { open = !open }
                .semantics { stateDescription = if (open) "Open" else "Closed" }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = TextSecondary, fontSize = 13.sp)
            }
            Text(if (open) "⌃" else "⌄", color = TextSecondary, fontSize = 20.sp)
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), content = content)
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(top = 3.dp)) {
        Text("•", color = Accent, fontSize = 13.sp, modifier = Modifier.width(14.dp))
        Text(text, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
    }
}
