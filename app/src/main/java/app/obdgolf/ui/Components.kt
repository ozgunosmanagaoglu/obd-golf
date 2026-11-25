package app.obdgolf.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import app.obdgolf.core.Str

const val RPM_MAX = 7000.0
const val RPM_RED = 6000.0

fun Double?.fmt(decimals: Int = 0): String = when {
    this == null -> "—"
    decimals == 0 -> roundToInt().toString()
    else -> "%.${decimals}f".format(this)
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(), color = C.Muted, fontSize = 12.sp, letterSpacing = 1.5.sp, modifier = modifier)

@Composable
fun Badge(text: String) = Box(
    Modifier.clip(RoundedCornerShape(10.dp)).background(C.Red).padding(horizontal = 10.dp, vertical = 6.dp)
) { Text(text, color = C.OnAccent, fontWeight = FontWeight.Bold, fontSize = 15.sp) }

@Composable
fun StatusDot(color: Color) = Box(Modifier.size(8.dp).clip(CircleShape).background(color))

@Composable
fun RowItem(label: String, value: String, muted: Boolean = false) = Row(
    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
) {
    Text(label, color = if (muted) C.Muted else C.Text, fontSize = 15.sp)
    Text(value, color = if (value == "—") C.Dim else C.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
}
