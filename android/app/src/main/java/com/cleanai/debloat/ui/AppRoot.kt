package com.cleanai.debloat.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.cleanai.debloat.agent.AgentViewModel
import com.cleanai.debloat.features.Feature
import com.cleanai.debloat.features.Features
import com.cleanai.debloat.features.Opens

private const val HOME = "home"
private const val ASSISTANT = "assistant"
private const val SETTINGS = "settings"
private const val PROFILE = "profile"

/** Simple screen switcher: Home, the Assistant, or a feature's own screen (by feature id). */
@Composable
fun AppRoot(vm: AgentViewModel) {
    var screen by rememberSaveable { mutableStateOf(HOME) }
    // A tool opened from inside another tool (Scan from the PDF Editor) goes back to that tool, not Home.
    var cameFrom by rememberSaveable { mutableStateOf<String?>(null) }
    val goHome = { cameFrom = null; screen = HOME }
    val goBack = { screen = cameFrom ?: HOME; cameFrom = null }
    // The opening animation plays once per launch, not after rotating the phone.
    var intro by rememberSaveable { mutableStateOf(true) }
    var closing by remember { mutableStateOf(false) }
    // The tool whose colour intro is playing over the screen.
    var splash by remember { mutableStateOf<Pair<Feature, () -> Unit>?>(null) }
    val activity = LocalContext.current as? Activity

    // Typed commands like "open calculator" ask for a screen through the view model.
    LaunchedEffect(vm.openScreen) {
        vm.openScreen?.let { screen = it; vm.screenOpened() }
    }
    BackHandler(enabled = screen != HOME) { goBack() }
    // Back on Home closes the app with its own animation instead of a plain cut.
    BackHandler(enabled = screen == HOME && !closing && vm.profileDone) { closing = true }

    // While opening, the app grows in behind the intro; while closing, it sinks away.
    val depth by animateFloatAsState(
        when { closing -> 0.9f; intro -> 1.08f; else -> 1f },
        tween(if (closing) 380 else 520),
        label = "depth",
    )

    Box(Modifier.fillMaxSize().background(Night)) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { scaleX = depth; scaleY = depth }.background(Bg),
        ) {
            if (!vm.profileDone) {
                // First launch: ask name, age and place before anything else.
                ProfileScreen(vm, firstTime = true, onDone = goHome)
            } else {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = { depthTransition(HOME) },
                    label = "screens",
                ) { s ->
                    Box(Modifier.fillMaxSize().background(Bg)) { Screen(s, vm, go = { screen = it }, goHome = goHome, goBack = goBack, fromTool = { cameFrom = s }, onIntro = { f, open -> splash = f to open }) }
                }
            }
        }
        splash?.let { (f, open) -> FeatureIntro(f, onCovered = open, onDone = { splash = null }) }
        if (intro) IntroOverlay(onDone = { intro = false })
        if (closing) OutroOverlay(onDone = { activity?.finish() ?: run { closing = false } })
    }
}

@Composable
private fun Screen(
    screen: String,
    vm: AgentViewModel,
    go: (String) -> Unit,
    goHome: () -> Unit,
    goBack: () -> Unit,
    fromTool: () -> Unit,
    onIntro: (Feature, open: () -> Unit) -> Unit,
) {
    // The tool opens behind its colour intro, once the colour covers the screen.
    val openFeature: (Feature) -> Unit = { f ->
        onIntro(f) {
            when (val o = f.opens) {
                is Opens.Screen -> go(f.id)
                is Opens.Assistant -> {
                    go(ASSISTANT)
                    o.act?.let { vm.onTool(it, f.title) }
                }
            }
        }
    }
    when (screen) {
        HOME -> MainTabs(
            name = vm.userName,
            shizuku = vm.shizuku,
            onFeature = openFeature,
            // Sparky, the Ask card and "Ask Sparky: …" in search all open the Assistant; typed text is sent for it.
            onAsk = { text ->
                go(ASSISTANT)
                text?.let { vm.onSend(it) }
            },
            onSettings = { go(SETTINGS) },
            onFiles = { go("files") },
            onOpenDoc = { doc ->
                if (doc.hasPdf) {
                    PdfLaunch.open(Uri.fromFile(doc.pdf))
                    go("pdfreader")
                } else go("files")
            },
        )
        ASSISTANT -> ChatScreen(vm, onBack = goHome, onSettings = { go(SETTINGS) })
        PROFILE -> ProfileScreen(vm, firstTime = false, onDone = { go(SETTINGS) })
        SETTINGS -> SettingsScreen(
            vm,
            onBack = goHome,
            onAct = { act, label -> go(ASSISTANT); vm.onTool(act, label) },
            onAbout = { go("about") },
            onProfile = { go(PROFILE) },
        )
        else -> {
            val content = (Features.byId(screen)?.opens as? Opens.Screen)?.content
            if (content != null) content(goBack) { id -> Features.byId(id)?.let { fromTool(); openFeature(it) } } else LaunchedEffect(screen) { goHome() }
        }
    }
}
