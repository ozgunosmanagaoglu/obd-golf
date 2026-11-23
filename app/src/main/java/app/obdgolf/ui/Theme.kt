package app.obdgolf.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.obdgolf.R

val Sora = FontFamily(
    Font(R.font.sora_extralight, FontWeight.ExtraLight),
    Font(R.font.sora_light, FontWeight.Light),
    Font(R.font.sora_semibold, FontWeight.SemiBold),
)

object C {
    val Bg = Color(0xFF0D0E10)
    val Card = Color(0xFF17191D)
    val Track = Color(0xFF24262B)
    val Divider = Color(0xFF202227)
    val Text = Color(0xFFF2F2F3)
    val Muted = Color(0xFFA3A5AB)
    val Dim = Color(0xFF8A8D94)
    val Red = Color(0xFFD0021B)
    val RedZone = Color(0xFF5A1018)
    val Green = Color(0xFF3DDC84)
    val Amber = Color(0xFFFFB020)
}

@Composable
fun ObdTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Red, background = C.Bg, surface = C.Card,
            onPrimary = Color.White, onBackground = C.Text, onSurface = C.Text,
        ),
        content = content,
    )
}
