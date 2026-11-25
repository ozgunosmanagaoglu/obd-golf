package app.obdgolf.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.obdgolf.R
import app.obdgolf.core.Str

/*
 * The Revora design system (see docs/DESIGN.md). Screens use these tokens only:
 * colours from [C], text styles from [Type], spacing from [Space], radii from [Radius].
 */

val Sora = FontFamily(
    Font(R.font.sora_extralight, FontWeight.ExtraLight),
    Font(R.font.sora_light, FontWeight.Light),
    Font(R.font.sora_semibold, FontWeight.SemiBold),
)

/** Colour tokens of one theme. Contrast of [text]/[muted] on [bg] and [card] is WCAG AA or better. */
data class Palette(
    val bg: Color,
    val card: Color,
    val raised: Color,
    val track: Color,
    val divider: Color,
    val text: Color,
    val muted: Color,
    val dim: Color,
    val accent: Color,
    val accentZone: Color,
    val ok: Color,
    val warn: Color,
    val alarm: Color,
    val onAccent: Color,
    val dark: Boolean,
)

enum class ThemeMode(val id: String, val label: Str) {
    /** Default: night driving and OLED screens. */
    DARK("dark", Str.THEME_DARK),
    LIGHT("light", Str.THEME_LIGHT),
    /** Pure black, white text, brighter signal colours: bright sun or weaker eyesight. */
    HIGH_CONTRAST("contrast", Str.THEME_CONTRAST);

    val palette: Palette
        get() = when (this) {
            DARK -> Palette(
                bg = Color(0xFF0D0E10), card = Color(0xFF17191D), raised = Color(0xFF1F2126), track = Color(0xFF2A2D33),
                divider = Color(0xFF202227), text = Color(0xFFF2F2F3), muted = Color(0xFFA3A5AB), dim = Color(0xFF8A8D94),
                accent = Color(0xFFD0021B), accentZone = Color(0xFF5A1018), ok = Color(0xFF3DDC84), warn = Color(0xFFFFB020),
                alarm = Color(0xFFFF4D5E), onAccent = Color.White, dark = true,
            )
            LIGHT -> Palette(
                bg = Color(0xFFF4F5F7), card = Color(0xFFFFFFFF), raised = Color(0xFFFFFFFF), track = Color(0xFFE1E3E8),
                divider = Color(0xFFE6E8EC), text = Color(0xFF111316), muted = Color(0xFF5B5F67), dim = Color(0xFF6E727A),
                accent = Color(0xFFC00018), accentZone = Color(0xFFF6C9CE), ok = Color(0xFF1E8E4E), warn = Color(0xFFB36B00),
                alarm = Color(0xFFC00018), onAccent = Color.White, dark = false,
            )
            HIGH_CONTRAST -> Palette(
                bg = Color(0xFF000000), card = Color(0xFF0B0B0B), raised = Color(0xFF141414), track = Color(0xFF3A3A3A),
                divider = Color(0xFF3A3A3A), text = Color(0xFFFFFFFF), muted = Color(0xFFD6D6D6), dim = Color(0xFFBDBDBD),
                accent = Color(0xFFFF2D44), accentZone = Color(0xFF6B0A16), ok = Color(0xFF5CFF9D), warn = Color(0xFFFFD000),
                alarm = Color(0xFFFF2D44), onAccent = Color.White, dark = true,
            )
        }

    companion object {
        fun byId(id: String?) = entries.firstOrNull { it.id == id } ?: DARK
    }
}

/**
 * Colour tokens of the active theme. Backed by Compose state, so a theme switch recomposes every screen.
 * The short names are kept from before the design system so existing screens keep working.
 */
object C {
    var palette: Palette by mutableStateOf(ThemeMode.DARK.palette)

    val Bg get() = palette.bg
    val Card get() = palette.card
    val Raised get() = palette.raised
    val Track get() = palette.track
    val Divider get() = palette.divider
    val Text get() = palette.text
    val Muted get() = palette.muted
    val Dim get() = palette.dim
    val Red get() = palette.accent
    val RedZone get() = palette.accentZone
    val Green get() = palette.ok
    val Amber get() = palette.warn
    val Alarm get() = palette.alarm
    val OnAccent get() = palette.onAccent
}

/** Type scale. Values use tabular figures so digits do not jump while they change. */
object Type {
    private const val TNUM = "tnum"
    /** The one value a page is about. */
    val hero = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TNUM, lineHeight = 68.sp)
    /** A tile's value. Never below 32 sp on a driving screen. */
    val value = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TNUM, lineHeight = 44.sp)
    val headline = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    val title = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 15.sp)
    /** Tile labels: at least 14 sp so they read at arm's length. */
    val label = TextStyle(fontSize = 14.sp, letterSpacing = 0.3.sp)
    val caption = TextStyle(fontSize = 12.sp)
}

/** 4/8 dp spacing grid. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object Radius {
    val tile = 16.dp
    val sheet = 20.dp
    val chip = 10.dp
}

/** Minimum touch target: 48 dp, 64 dp on head units. */
object Touch {
    val min = 48.dp
    val car = 64.dp
}

@Composable
fun ObdTheme(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val p = mode.palette
    C.palette = p
    MaterialTheme(
        colorScheme = if (p.dark) {
            darkColorScheme(primary = p.accent, background = p.bg, surface = p.card, onPrimary = p.onAccent, onBackground = p.text, onSurface = p.text)
        } else {
            lightColorScheme(primary = p.accent, background = p.bg, surface = p.card, onPrimary = p.onAccent, onBackground = p.text, onSurface = p.text)
        },
        content = content,
    )
}
