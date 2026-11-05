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

// "Mobile Handler 2.0" (ChatGPT's Apple-inspired brief): warm-white / deep-charcoal surfaces, one blue accent.
val Bg get() = pick(0xFFF7F8FA, 0xFF101114)
/** Raised: chips, secondary buttons, pressed fills. */
val Card get() = pick(0xFFECEEF2, 0xFF2A2D33)
/** Surface: cards and sheets. */
val CardHi get() = pick(0xFFFFFFFF, 0xFF1C1E23)
val Line get() = pick(0xFFD8DCE2, 0xFF383C44)
/** Solid buttons. */
val Charcoal get() = pick(0xFF1F2329, 0xFF3A3F48)
val Sage get() = pick(0xFF5E646E, 0xFF8E949E)
val TextPrimary get() = pick(0xFF17191D, 0xFFF5F5F7)
val TextSecondary get() = pick(0xFF5E646E, 0xFFA1A6B0)

/** Always-dark ink for icons sitting on the pastel circles. */
val Ink = Color(0xFF27312C)
/** Accent blue (#3478F6 family): a shade deeper on light and brighter on dark so text in it stays readable. */
val Accent get() = pick(0xFF2A6BE4, 0xFF4C8BFF)
/** Sparky's orange, for mascot touches. */
val SparkyOrange = Color(0xFFFF873E)
/** Pastel coral for tool chips with dark ink icons (same in both themes). */
val CoralTint = Color(0xFFEE6A57)
val Butter = Color(0xFFF1DF78)
val Sky = Color(0xFFAFD8EE)
val Mint = Color(0xFFBDE2AE)
val Ok = Color(0xFF30A46C)
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
            primary = Accent, onPrimary = Color.White, secondary = Accent,
            background = Bg, onBackground = TextPrimary,
            surface = CardHi, onSurface = TextPrimary, onSurfaceVariant = TextSecondary,
        )
    } else {
        lightColorScheme(
            primary = Charcoal, onPrimary = Color.White, secondary = Accent,
            background = Bg, onBackground = TextPrimary,
            surface = CardHi, onSurface = TextPrimary, onSurfaceVariant = TextSecondary,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
