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

const val RPM_MAX = 7000.0
const val RPM_RED = 6000.0

fun Double?.fmt(decimals: Int = 0): String = when {
    this == null -> "—"
    decimals == 0 -> roundToInt().toString()
    else -> "%.${decimals}f".format(this)
}

/** 270° rev gauge with the red zone from [RPM_RED], speed underneath. */
@Composable
fun RevGauge(rpm: Double?, speed: Double?, diameter: Dp, stroke: Dp, big: TextUnit, small: TextUnit) {
    Box(Modifier.height(diameter * 0.9f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Canvas(Modifier.height(diameter).fillMaxWidth()) {
            val d = diameter.toPx()
            val sw = stroke.toPx()
            val topLeft = Offset((this.size.width - d) / 2 + sw / 2, sw / 2)
            val arc = Size(d - sw, d - sw)
            drawArc(C.Track, 135f, 270f, false, topLeft, arc, style = Stroke(sw, cap = StrokeCap.Round))
            val redStart = (RPM_RED / RPM_MAX * 270).toFloat()
            drawArc(C.RedZone, 135f + redStart, 270f - redStart, false, topLeft, arc, style = Stroke(sw))
            val frac = ((rpm ?: 0.0) / RPM_MAX).coerceIn(0.0, 1.0).toFloat()
            if (frac > 0f) drawArc(C.Red, 135f, 270f * frac, false, topLeft, arc, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(Modifier.padding(top = diameter * 0.26f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("DEVİR", color = C.Muted, fontSize = small, letterSpacing = 2.sp)
            Text(rpm.fmt(), color = C.Text, fontSize = big, fontWeight = FontWeight.Bold)
            Text("rpm", color = C.Muted, fontSize = small)
        }
        Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(LocalUnits.current.speed(speed).fmt(), color = C.Text, fontSize = big * 0.62f, fontWeight = FontWeight.Bold)
            Text(LocalUnits.current.speedUnit, color = C.Muted, fontSize = small)
        }
    }
}

@Composable
fun ValueTile(
    label: String, value: String, unit: String, modifier: Modifier = Modifier,
    bar: Float? = null, barColor: Color = C.Green, valueSize: TextUnit = 30.sp,
) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(C.Card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, color = C.Muted, fontSize = 13.sp)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = if (value == "—") C.Dim else C.Text, fontWeight = FontWeight.Bold, fontSize = valueSize)) { append(value) }
                if (value != "—") withStyle(SpanStyle(color = C.Muted, fontSize = valueSize * 0.5f)) { append(" $unit") }
            }
        )
        if (bar != null) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.Track)) {
                Box(Modifier.fillMaxWidth(bar.coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(barColor))
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(), color = C.Muted, fontSize = 12.sp, letterSpacing = 1.5.sp, modifier = modifier)

@Composable
fun Badge(text: String) = Box(
    Modifier.clip(RoundedCornerShape(10.dp)).background(C.Red).padding(horizontal = 10.dp, vertical = 6.dp)
) { Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) }

@Composable
fun StatusDot(color: Color) = Box(Modifier.size(8.dp).clip(CircleShape).background(color))

/** Coolant colour: amber while cold, green at temperature, red when hot. */
fun coolantColor(c: Double?): Color = when {
    c == null -> C.Track
    c < 70 -> C.Amber
    c <= 105 -> C.Green
    else -> C.Red
}

fun voltageColor(v: Double?): Color = when {
    v == null -> C.Track
    v < 12.0 -> C.Red
    v < 12.4 -> C.Amber
    else -> C.Green
}

@Composable
fun RowItem(label: String, value: String, muted: Boolean = false) = Row(
    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
) {
    Text(label, color = if (muted) C.Muted else Color(0xFFD6D7DA), fontSize = 15.sp)
    Text(value, color = if (value == "—") C.Dim else C.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
}
