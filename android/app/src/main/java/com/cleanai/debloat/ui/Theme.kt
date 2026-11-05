package com.cleanai.debloat.ui

import android.app.Activity
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Light / dark switch, saved on the phone. Every colour below follows it, so screens need no changes. */
object AppTheme {
    var dark by mutableStateOf(false)
        private set

    fun load(ctx: Context, systemDark: Boolean) {
        val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        dark = if (prefs.contains("dark_theme")) prefs.getBoolean("dark_theme", false) else systemDark
    }

    fun set(ctx: Context, on: Boolean) {
        dark = on
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("dark_theme", on).apply()
    }
}

private fun pick(light: Long, dark: Long) = Color(if (AppTheme.dark) dark else light)

// "Sparky Flow": Soft Porcelain (light) and Obsidian Forest (dark). Calm surfaces, coral accent, pastel tool chips.
val Bg get() = pick(0xFFF7F9F5, 0xFF101512)
/** Raised: chips, secondary buttons, pressed fills. */
val Card get() = pick(0xFFEAF0E9, 0xFF29332C)
/** Surface: cards and sheets. */
val CardHi get() = pick(0xFFFFFFFF, 0xFF1B231E)
val Line get() = pick(0xFFCBD6CC, 0xFF3B4A3E)
val Charcoal get() = pick(0xFF27312C, 0xFF35423B)
val Sage get() = pick(0xFF4E5F56, 0xFF8FA398)
val TextPrimary get() = pick(0xFF19241C, 0xFFEDF4ED)
val TextSecondary get() = pick(0xFF526357, 0xFFAAB9AD)

/** Always-dark ink for icons sitting on the pastel circles. */
val Ink = Color(0xFF27312C)
/** Brand accent: deeper on light so text and icons in it stay readable, brighter on dark. */
val Coral get() = pick(0xFFC24A36, 0xFFFF806B)
/** The original pastel coral, for tool chips with dark ink icons (same in both themes). */
val CoralTint = Color(0xFFEE6A57)
val Butter = Color(0xFFF1DF78)
val Sky = Color(0xFFAFD8EE)
val Mint = Color(0xFFBDE2AE)
val Ok = Color(0xFF3E9E68)
val Blue = Color(0xFF3B7FB0)
val Amber = Color(0xFFD98E1F)
val Red = Color(0xFFD64535)

@Composable
fun MobileHandlerTheme(content: @Composable () -> Unit) {
    val dark = AppTheme.dark
    // Status / navigation bar icons: dark on the light theme, light on the dark one.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = Coral, onPrimary = Color.White, secondary = Coral,
            background = Bg, onBackground = TextPrimary,
            surface = CardHi, onSurface = TextPrimary, onSurfaceVariant = TextSecondary,
        )
    } else {
        lightColorScheme(
            primary = Charcoal, onPrimary = Color.White, secondary = Coral,
            background = Bg, onBackground = TextPrimary,
            surface = CardHi, onSurface = TextPrimary, onSurfaceVariant = TextSecondary,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
