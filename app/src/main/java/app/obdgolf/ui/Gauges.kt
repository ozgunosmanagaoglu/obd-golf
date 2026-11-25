package app.obdgolf.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import app.obdgolf.core.CarData
import app.obdgolf.core.Gauge
import app.obdgolf.core.GaugeSize
import app.obdgolf.core.GaugeStyle
import app.obdgolf.core.Metric
import app.obdgolf.core.Str

/** A metric's name in the current language. */
@Composable
fun metricLabel(m: Metric): String = when (m) {
    is Metric.Core -> s(m.label)
    is Metric.Extra -> LocalStrings.current.pid(m.pid)
}

/** Value and unit after the user's unit settings, plus the decimals to show. */
private data class Shown(val value: Double?, val unit: String, val decimals: Int, val min: Double, val max: Double, val warnAbove: Double?, val warnBelow: Double?)

@Composable
private fun shown(g: Gauge, raw: Double?): Shown {
    val u = LocalUnits.current
    val m = g.metric
    fun conv(v: Double) = u.convert(v, m.unit).first
    val unit = u.convert(0.0, m.unit).second
    return Shown(
        raw?.let(::conv), unit, u.decimalsFor(m.unit, m.decimals),
        conv(m.range.start), conv(m.range.endInclusive), g.warnAbove?.let(::conv), g.warnBelow?.let(::conv),
    )
}

/**
 * One dashboard tile: label, value and the chosen visual, filling the cell it is given.
 * Warning state: red outline, an icon and a word next to the label (never colour alone), and a
 * TalkBack description that says it.
 */
@Composable
fun GaugeTile(g: Gauge, data: CarData, history: List<Double>, modifier: Modifier = Modifier, carScreen: Boolean = false) {
    val raw = g.metric.read(data)
    val sh = shown(g, raw)
    val alarm = g.alarm(data)
    val label = metricLabel(g.metric)
    val valueText = sh.value.fmt(sh.decimals)
    val status = when {
        raw == null -> s(Str.GAUGE_NO_DATA)
        alarm == null -> s(Str.GAUGE_NORMAL)
        alarm.above -> s(Str.GAUGE_HIGH)
        else -> s(Str.GAUGE_LOW)
    }
    val big = g.size == GaugeSize.S2X2
    val valueStyle = when {
        big -> Type.hero
        carScreen -> Type.value.copy(fontSize = 48.sp, lineHeight = 52.sp)
        else -> Type.value
    }
    Column(
        modifier
            .clip(RoundedCornerShape(Radius.tile))
            .background(C.Card)
            .then(if (alarm != null) Modifier.border(2.dp, C.Alarm, RoundedCornerShape(Radius.tile)) else Modifier)
            .padding(Space.l)
            .semantics { contentDescription = "$label, $valueText ${sh.unit}, $status" },
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Text(label, style = Type.label, color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (alarm != null) WarningChip(if (alarm.above) s(Str.GAUGE_HIGH) else s(Str.GAUGE_LOW))
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (raw == null) {
                // No answer (yet): a calm dash and the reason, never an empty gauge that looks like zero.
                Column(Modifier.align(Alignment.CenterStart), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    BigValue(valueText, sh.unit, valueStyle, false)
                    Text(s(Str.GAUGE_NO_DATA), style = Type.caption, color = C.Dim, maxLines = 1)
                }
            } else when (g.style) {
                GaugeStyle.DIGITAL -> BigValue(valueText, sh.unit, valueStyle, alarm != null, Modifier.align(Alignment.CenterStart))
                GaugeStyle.ARC -> ArcGauge(sh, valueText, valueStyle, alarm != null)
                GaugeStyle.BAR -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                    BigValue(valueText, sh.unit, valueStyle, alarm != null)
                    BarGauge(sh, alarm != null)
                }
                GaugeStyle.SPARKLINE -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    BigValue(valueText, sh.unit, valueStyle, alarm != null)
                    GaugeSparkline(history.map { LocalUnits.current.convert(it, g.metric.unit).first }, sh, alarm != null, Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }
}

@Composable
private fun WarningChip(word: String) = Row(
    Modifier.clip(RoundedCornerShape(Radius.chip)).background(C.Alarm).padding(horizontal = Space.s, vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Space.xs),
) {
    Text("!", style = Type.label.copy(fontSize = 13.sp), color = C.OnAccent)
    Text(word.uppercase(LocalStrings.current.lang.locale), style = Type.caption, color = C.OnAccent, maxLines = 1)
}

@Composable
private fun BigValue(value: String, unit: String, style: TextStyle, alarm: Boolean, modifier: Modifier = Modifier) {
    FitText(
        buildAnnotatedString {
            withStyle(SpanStyle(color = if (value == "—") C.Dim else if (alarm) C.Alarm else C.Text)) { append(value) }
            if (value != "—" && unit.isNotEmpty()) withStyle(SpanStyle(color = C.Muted, fontSize = style.fontSize * 0.4f)) { append(" $unit") }
        },
        style, modifier,
    )
}

/**
 * One line of text that shrinks (down to half) instead of being cut off when the tile is narrow
 * or the user has large fonts. Starts again at full size when the number of characters changes.
 */
@Composable
internal fun FitText(text: AnnotatedString, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    var scale by remember(text.length, style) { mutableFloatStateOf(1f) }
    Text(
        text,
        style = style.copy(fontSize = style.fontSize * scale, lineHeight = if (style.lineHeight.isSpecified) style.lineHeight * scale else style.lineHeight),
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
        onTextLayout = { if (it.hasVisualOverflow && scale > 0.5f) scale *= 0.9f },
    )
}

private fun frac(v: Double?, min: Double, max: Double): Float =
    if (v == null || max <= min) 0f else ((v - min) / (max - min)).coerceIn(0.0, 1.0).toFloat()

/** 240° arc, warning zone drawn in the track, value in the middle. Animated, 250 ms. */
@Composable
private fun ArcGauge(sh: Shown, valueText: String, style: TextStyle, alarm: Boolean) {
    val target = frac(sh.value, sh.min, sh.max)
    val f by animateFloatAsState(target, tween(250), label = "arc")
    val track = C.Track
    val zone = C.RedZone
    val fill = if (alarm) C.Alarm else C.Red
    val zoneFrom = sh.warnAbove?.let { frac(it, sh.min, sh.max) }
    val zoneTo = sh.warnBelow?.let { frac(it, sh.min, sh.max) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = (size.minDimension * 0.08f).coerceIn(6.dp.toPx(), 18.dp.toPx())
            val d = size.minDimension - stroke
            val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2 + d * 0.06f)
            val arc = Size(d, d)
            val start = 150f
            val sweep = 240f
            drawArc(track, start, sweep, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            zoneFrom?.let { drawArc(zone, start + sweep * it, sweep * (1 - it), false, topLeft, arc, style = Stroke(stroke)) }
            zoneTo?.let { drawArc(zone, start, sweep * it, false, topLeft, arc, style = Stroke(stroke)) }
            if (f > 0f) drawArc(fill, start, sweep * f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(Modifier.padding(horizontal = Space.l), horizontalAlignment = Alignment.CenterHorizontally) {
            FitText(AnnotatedString(valueText), style, color = if (valueText == "—") C.Dim else if (alarm) C.Alarm else C.Text)
            if (sh.unit.isNotEmpty()) Text(sh.unit, style = Type.label, color = C.Muted)
        }
    }
}

/** Horizontal bar with a marker where the warning zone starts. */
@Composable
private fun BarGauge(sh: Shown, alarm: Boolean) {
    val f by animateFloatAsState(frac(sh.value, sh.min, sh.max), tween(250), label = "bar")
    val track = C.Track
    val fill = if (alarm) C.Alarm else C.Green
    val mark = C.Muted
    val marks = listOfNotNull(sh.warnAbove, sh.warnBelow).map { frac(it, sh.min, sh.max) }
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val r = size.height / 2
        drawRoundRect(track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
        if (f > 0f) drawRoundRect(fill, size = Size(size.width * f, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
        for (m in marks) {
            val x = size.width * m
            drawLine(mark, Offset(x, -3.dp.toPx()), Offset(x, size.height + 3.dp.toPx()), strokeWidth = 2.dp.toPx())
        }
    }
}

/** The last minute as a line; the warning limit as a dashed-looking thin line. */
@Composable
private fun GaugeSparkline(history: List<Double>, sh: Shown, alarm: Boolean, modifier: Modifier) {
    val line = if (alarm) C.Alarm else C.Red
    val limitColor = C.Muted
    Canvas(modifier) {
        if (history.size < 2) return@Canvas
        val values = history
        val lo = minOf(values.min(), sh.warnBelow ?: Double.MAX_VALUE)
        val hi = maxOf(values.max(), sh.warnAbove ?: -Double.MAX_VALUE)
        val span = (hi - lo).takeIf { it > 1e-9 } ?: 1.0
        fun y(v: Double) = (size.height - ((v - lo) / span * size.height)).toFloat()
        val step = size.width / (values.size - 1)
        val p = Path()
        values.forEachIndexed { i, v -> if (i == 0) p.moveTo(0f, y(v)) else p.lineTo(i * step, y(v)) }
        drawPath(p, line, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        listOfNotNull(sh.warnAbove, sh.warnBelow).forEach { lim ->
            if (lim in lo..hi) drawLine(limitColor, Offset(0f, y(lim)), Offset(size.width, y(lim)), strokeWidth = 1.dp.toPx())
        }
    }
}
