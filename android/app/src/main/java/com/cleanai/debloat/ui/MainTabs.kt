package com.cleanai.debloat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanai.debloat.R
import com.cleanai.debloat.data.MyFiles
import com.cleanai.debloat.data.ShizukuShell
import com.cleanai.debloat.features.Feature
import com.cleanai.debloat.features.Features
import com.cleanai.debloat.features.ToolGroup

/**
 * The app's main frame: Home and Tools tabs with the bottom bar (Home · Tools · Files · Ask).
 * Files and Ask open their own full screens.
 */
@Composable
fun MainTabs(
    name: String,
    shizuku: ShizukuShell.State,
    onFeature: (Feature) -> Unit,
    onAsk: (String?) -> Unit,
    onSettings: () -> Unit,
    onFiles: () -> Unit,
    onOpenDoc: (MyFiles.Doc) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var group by rememberSaveable { mutableStateOf<ToolGroup?>(null) }
    BackHandler(enabled = tab == 1) { tab = 0 }
    Column(Modifier.fillMaxSize().background(Bg)) {
        Box(Modifier.weight(1f).statusBarsPadding()) {
            AnimatedContent(tab, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "tab") { t ->
                if (t == 0) HomeScreen(name, shizuku, onFeature, onAsk, onSettings, onOpenDoc, onGroup = { group = it; tab = 1 })
                else ToolsScreen(group, onFeature)
            }
        }
        NavBar(
            selected = tab,
            onHome = { tab = 0 },
            onTools = { group = null; tab = 1 },
            onFiles = onFiles,
            onAsk = { onAsk(null) },
        )
    }
}

/** Every tool, grouped, with a filter box. Opens scrolled to [start] when a Home group was tapped. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolsScreen(start: ToolGroup?, onFeature: (Feature) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    val focus = LocalFocusManager.current
    val shown = if (query.isBlank()) Features.all else searchTools(query)
    val grouped = ToolGroup.entries.mapNotNull { g -> shown.filter { it.group == g }.takeIf { it.isNotEmpty() }?.let { g to it } }
    LaunchedEffect(start) {
        if (start == null) return@LaunchedEffect
        // Two rows (title and search) come first, then each group is a header plus its tools.
        var index = 2
        for ((g, tools) in grouped) {
            if (g == start) break
            index += 1 + tools.size
        }
        list.scrollToItem(index)
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = list,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("Tools", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)) }
        item { SearchField(query, { query = it }, placeholder = "Find a tool…", onGo = { focus.clearFocus(); shown.singleOrNull()?.let(onFeature) }) }
        grouped.forEach { (g, tools) ->
            stickyHeader(key = g.name) {
                Text(
                    g.title, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().background(Bg).padding(start = 4.dp, top = 14.dp, bottom = 6.dp),
                )
            }
            items(tools, key = { it.id }) { f -> ToolRow(f) { focus.clearFocus(); onFeature(f) } }
        }
        if (grouped.isEmpty()) item { Text("No tool matches “$query”.", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(4.dp)) }
    }
}

@Composable
private fun NavBar(selected: Int, onHome: () -> Unit, onTools: () -> Unit, onFiles: () -> Unit, onAsk: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(CardHi).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            NavItem("Home", selected == 0, Modifier.weight(1f), onHome) { tint -> Icon(painterResource(R.drawable.ic_home), null, tint = tint, modifier = Modifier.size(24.dp)) }
            NavItem("Tools", selected == 1, Modifier.weight(1f), onTools) { tint -> Icon(painterResource(R.drawable.ic_grid), null, tint = tint, modifier = Modifier.size(24.dp)) }
            NavItem("Files", false, Modifier.weight(1f), onFiles) { tint -> Icon(painterResource(R.drawable.ic_folder), null, tint = tint, modifier = Modifier.size(24.dp)) }
            NavItem("Ask", false, Modifier.weight(1f), onAsk) { _ -> SparkyPortrait(SparkyMood.AFTERNOON, Modifier.size(28.dp), animate = false) }
        }
    }
}

@Composable
private fun NavItem(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit, icon: @Composable (androidx.compose.ui.graphics.Color) -> Unit) {
    val tint = if (on) Coral else TextSecondary
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).bouncy(onClick = onClick)
            .semantics { role = Role.Tab; this.selected = on }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(if (on) Card else androidx.compose.ui.graphics.Color.Transparent)
                .height(32.dp).padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) { icon(tint) }
        Text(label, color = tint, fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
    }
}
