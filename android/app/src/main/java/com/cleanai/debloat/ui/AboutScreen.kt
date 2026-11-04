package com.cleanai.debloat.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val DEVELOPER = "Janardan Pal"
const val BUILT_BY = "Built by $DEVELOPER"

/** Where people can reach the developer; each opens in the matching app or the browser. */
private val Links = listOf(
    Triple("GitHub", "github.com/Janardanpal007", "https://github.com/Janardanpal007"),
    Triple("LinkedIn", "linkedin.com/in/janardan-pal", "https://www.linkedin.com/in/janardan-pal"),
    Triple("Discord", "Add me on Discord", "https://discord.dog/704385778809307226"),
)

private val Policy = listOf(
    "Who we are" to
        "Mobile Handler is developed and maintained by $DEVELOPER. For questions about this app or this policy, contact the developer through the links above.",
    "Private mode — on by default" to
        "With Private mode on, Mobile Handler sends nothing to any company or website. Everything happens on your phone.",
    "What stays on your phone" to
        "Your name, age and place, your installed-apps list, the changes you make, scanned QR codes, scanned documents, PDFs, calculator history and the chat. We have no account system and no server. Clear the chat any time from the Assistant or Settings.",
    "Camera and scanning" to
        "QR and document scanning run on the phone through Google Play services; Mobile Handler only receives the result.",
    "Only if you turn Private mode off" to
        "Then, and only when you use these features: questions go to DuckDuckGo, Wikipedia, GitHub or Stack Exchange; weather sends a city or rough location to Open-Meteo; and currency rates come from open.er-api.com.",
    "Ad blocker" to
        "The ad blocker works by setting your phone's Private DNS to a provider you choose (AdGuard or Mullvad). Like any DNS service, that provider sees the website names your phone looks up. It is off unless you turn it on.",
    "Your control" to
        "Every app removal can be undone from Restore Apps. You can turn ad blocking off at any time, remove your AI key in Settings, and delete saved PDFs from Downloads.",
)

/** About the app, the developer credit and the privacy policy. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "1.0"
    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .systemBarsPadding(),
    ) {
        ScreenTopBar("About & Privacy", onBack)
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Charcoal)
                    .padding(22.dp),
            ) {
                AppMark(Modifier.size(48.dp))
                Spacer(Modifier.height(14.dp))
                Text("Mobile Handler", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text("Version $version", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                StatusChip(BUILT_BY, Coral)
            }
            Text("Connect with me", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(CardHi)
                    .border(1.dp, Line, RoundedCornerShape(22.dp)),
            ) {
                Links.forEachIndexed { i, (name, handle, url) ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                    .onFailure { Toast.makeText(ctx, "No app can open $url", Toast.LENGTH_SHORT).show() }
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Charcoal), contentAlignment = Alignment.Center) {
                            Text(name.take(1), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(handle, color = TextSecondary, fontSize = 13.sp)
                        }
                        Text("↗", color = TextSecondary, fontSize = 18.sp)
                    }
                }
            }
            Text("Privacy policy", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
            Policy.forEach { (title, body) ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(CardHi)
                        .border(1.dp, Line, RoundedCornerShape(22.dp))
                        .padding(16.dp),
                ) {
                    Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(body, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
            Text(
                "© 2026 $DEVELOPER",
                color = TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}
