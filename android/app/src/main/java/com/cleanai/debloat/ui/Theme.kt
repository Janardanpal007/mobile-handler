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

// HaHo-style palette: warm greys, deep green-charcoal, coral accent and soft pastel chips.
// Dark mode keeps the same accents on a deep green-black.
val Bg get() = pick(0xFFEEEDE9, 0xFF121614)
val Card get() = pick(0xFFE3E2DD, 0xFF1D2320)
val CardHi get() = pick(0xFFF8F7F4, 0xFF222926)
val Line get() = pick(0xFFD5D4CE, 0xFF333B37)
val Charcoal get() = pick(0xFF27312C, 0xFF35423B)
val Sage get() = pick(0xFF4E5F56, 0xFF8FA398)
val TextPrimary get() = pick(0xFF1E2521, 0xFFECEEEA)
val TextSecondary get() = pick(0xFF6D7570, 0xFF9BA59F)

/** Always-dark ink for icons sitting on the pastel circles. */
val Ink = Color(0xFF27312C)
val Coral = Color(0xFFEE6A57)
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
