package com.cleanai.debloat.ui

import androidx.annotation.DrawableRes
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.cleanai.debloat.data.Updates
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanai.debloat.R
import com.cleanai.debloat.agent.Act
import com.cleanai.debloat.agent.AgentViewModel

/**
 * Full Settings page, grouped like the phone's own settings.
 * [onAct] runs an assistant action (opens the chat); [onAbout] opens About & Privacy.
 */
@Composable
fun SettingsScreen(
    vm: AgentViewModel,
    onBack: () -> Unit,
    onAct: (Act, String) -> Unit,
    onAbout: () -> Unit,
    onProfile: () -> Unit,
) {
    val ctx = LocalContext.current
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "1.0" }

    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        ScreenTopBar("Settings", onBack)
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 40.dp),
        ) {
            Section("Appearance")
            Group(1) {
                SettingRow(
                    icon = if (AppTheme.dark) R.drawable.ic_moon else R.drawable.ic_sun, tint = Sky,
                    title = "Theme",
                    subtitle = if (AppTheme.dark) "Dark" else "Light",
                    onClick = null,
                )
                Row(
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    listOf(false to "Light", true to "Dark").forEach { (dark, label) ->
                        val on = AppTheme.dark == dark
                        Box(
                            Modifier.bouncy { AppTheme.set(ctx, dark) }
                                .weight(1f)
                                .clip(RoundedCornerShape(50))
                                .background(if (on) Charcoal else Card)
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, color = if (on) Color.White else TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Section("Privacy")
            Group(2) {
                SettingRow(
                    icon = R.drawable.ic_tool_shield, tint = Mint,
                    title = "Private mode",
                    subtitle = if (vm.privateMode) "On — nothing leaves your phone"
                    else "Off — weather, currency and web tips use the internet",
                    onClick = { vm.updatePrivateMode(!vm.privateMode) },
                ) {
                    Switch(
                        checked = vm.privateMode,
                        onCheckedChange = { vm.updatePrivateMode(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Ok, checkedThumbColor = Color.White),
                    )
                }
                Divider()
                SettingRow(
                    icon = R.drawable.ic_tool_assistant, tint = Accent,
                    title = "Clear chat history",
                    subtitle = "Delete the whole conversation",
                    onClick = { vm.clearChat(); Toast.makeText(ctx, "Chat cleared", Toast.LENGTH_SHORT).show() },
                )
            }

            Section("You")
            Group(3) {
                SettingRow(
                    icon = R.drawable.ic_tool_info, tint = Butter,
                    title = vm.userName.ifBlank { "Your profile" },
                    subtitle = listOfNotNull(
                        vm.userAge.takeIf { it.isNotBlank() }?.let { "$it years" },
                        vm.userPlace.takeIf { it.isNotBlank() },
                    ).joinToString(" · ").ifBlank { "Name, age and place" },
                    onClick = onProfile,
                )
            }

            Section("Phone")
            Group(5) {
                SettingRow(
                    icon = R.drawable.ic_tool_clean, tint = Mint,
                    title = "Shizuku",
                    subtitle = shizukuLabel(vm.shizuku),
                    onClick = { onAct(Act.Setup, "Set up Shizuku") },
                ) { Box(Modifier.size(10.dp).clip(CircleShape).background(shizukuDot(vm.shizuku))) }
                Divider()
                SettingRow(
                    icon = R.drawable.ic_tool_restore, tint = Butter,
                    title = "Keep working when closed",
                    subtitle = "Stop your phone from closing Shizuku",
                    onClick = { onAct(Act.KeepAlive, "Keep working when closed") },
                )
                Divider()
                SettingRow(
                    icon = R.drawable.ic_tool_shield, tint = Sky,
                    title = "Ad blocker (Private DNS)",
                    subtitle = "See what's active, turn on or off",
                    onClick = { onAct(Act.DnsStatus, "Ad blocker status") },
                )
                Divider()
                SettingRow(
                    icon = R.drawable.ic_tool_restore, tint = Accent,
                    title = "Restore removed apps",
                    subtitle = "Undo anything you removed",
                    onClick = { onAct(Act.Restore, "Restore apps") },
                )
            }


            Section("App updates")
            UpdatesGroup(6)

            Section("About")
            Group(7) {
                SettingRow(
                    icon = R.drawable.ic_tool_info, tint = Butter,
                    title = "About & Privacy",
                    subtitle = "Privacy policy and credits",
                    onClick = onAbout,
                )
                Divider()
                SettingRow(icon = null, tint = Color.Transparent, title = "Version", subtitle = null, onClick = null) {
                    Text(version, color = TextSecondary, fontSize = 15.sp)
                }
                Divider()
                SettingRow(icon = null, tint = Color.Transparent, title = "Developer", subtitle = null, onClick = null) {
                    Text(DEVELOPER, color = TextSecondary, fontSize = 15.sp)
                }
            }
            Text(
                BUILT_BY,
                color = TextSecondary, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.bouncy(enabled = enabled, onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) bg else bg.copy(alpha = 0.35f))
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) { Text(text, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
}

/** Current version, a "Check for updates" button and the switch for new-version notifications. */
@Composable
private fun UpdatesGroup(index: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Updates.Check?>(null) }
    var notifyOn by remember { mutableStateOf(Updates.notifyOn(ctx)) }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(ctx, "Allow notifications in Android settings to hear about updates", Toast.LENGTH_LONG).show()
    }
    val installed = remember { Updates.installed(ctx) }
    val available = (result as? Updates.Check.Available)?.release

    Group(index) {
        SettingRow(
            icon = R.drawable.ic_update, tint = if (available != null) Accent else Sky,
            title = if (available != null) "Version ${available.version} is available" else "Mobile Handler $installed",
            subtitle = when {
                checking -> "Checking for a new version…"
                available != null -> "You have $installed. Download the update and open it to install."
                result == Updates.Check.UpToDate -> "✓ You have the latest version"
                result is Updates.Check.Failed -> (result as Updates.Check.Failed).reason
                else -> "Check GitHub for a newer version"
            },
            onClick = null,
        )
        if (available != null && available.notes.isNotBlank()) {
            Text(
                available.notes.take(400),
                color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            )
        }
        Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (available != null) Pill("Download update", Accent, Color.White) { Updates.download(ctx, available) }
            Pill(if (checking) "Checking…" else "Check for updates", if (available != null) Card else Charcoal, if (available != null) TextPrimary else Color.White, enabled = !checking) {
                checking = true
                scope.launch { result = Updates.check(ctx); checking = false }
            }
        }
        Divider()
        SettingRow(
            icon = null, tint = Color.Transparent,
            title = "Notify me about new versions",
            subtitle = "Checks GitHub about once a day, even in Private mode. Nothing about you is sent.",
            onClick = null,
        ) {
            Switch(
                checked = notifyOn,
                onCheckedChange = { on ->
                    notifyOn = on
                    Updates.setNotify(ctx, on)
                    if (on && !Updates.canNotify(ctx) && Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                colors = SwitchDefaults.colors(checkedTrackColor = Ok, checkedThumbColor = Color.White),
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 8.dp, top = 22.dp, bottom = 8.dp),
    )
}

@Composable
private fun Group(index: Int, content: @Composable () -> Unit) {
    Column(
        Modifier
            .enter(index)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CardHi)
            .border(1.dp, Line, RoundedCornerShape(24.dp)),
    ) { content() }
}

@Composable
private fun Divider() = HorizontalDivider(color = Line, modifier = Modifier.padding(start = 16.dp))

/** A settings row: tinted icon, title / subtitle, and a trailing control (or a chevron when tappable). */
@Composable
private fun SettingRow(
    @DrawableRes icon: Int?,
    tint: Color,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)?,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.bouncy(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), null, tint = Ink, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = TextSecondary, fontSize = 14.sp)
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Text("›", color = TextSecondary, fontSize = 26.sp)
        }
    }
}
