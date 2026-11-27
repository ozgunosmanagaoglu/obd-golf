package app.obdgolf.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.text.format.DateFormat
import app.obdgolf.core.Drive
import app.obdgolf.core.DriveAnalysis
import app.obdgolf.core.Str
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Label: Color get() = C.Muted
private val Glass: Color get() = C.Text.copy(alpha = 0.04f)
private val GlassLine: Color get() = C.Text.copy(alpha = 0.07f)
private val Blue = Color(0xFF5AA9FF)

private val timeFmt = SimpleDateFormat("HH:mm", Locale.ROOT)

/** "Wednesday, 25 November" in the order and words of the app language. */
@Composable
private fun dayFormat(): SimpleDateFormat {
    val locale = LocalStrings.current.lang.locale
    return remember(locale) { SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale) }
}

/** Upper-case label in the app language (Turkish i → İ). */
@Composable
private fun caps(key: Str): String = s(key).uppercase(LocalStrings.current.lang.locale)

@Composable
private fun duration(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return if (h > 0) s(Str.HOURS_MINUTES, h, m) else s(Str.MINUTES, m)
}

@Composable
fun HistoryScreen(drives: List<Drive>, onDelete: (Drive) -> Unit) {
    var openStart by rememberSaveable { mutableStateOf<Long?>(null) }
    val open = drives.firstOrNull { it.startMs == openStart }
    val dayFmt = dayFormat()
    val locale = LocalStrings.current.lang.locale
    if (open != null) {
        DriveDetail(open, onBack = { openStart = null }, onDelete = { onDelete(open); openStart = null })
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().background(C.Bg),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(caps(Str.TAB_HISTORY), color = Label, fontFamily = Sora, fontSize = 11.sp, letterSpacing = 3.sp)
                Text(s(Str.YOUR_DRIVES), color = C.Text, fontFamily = Sora, fontWeight = FontWeight.Light, fontSize = 24.sp)
            }
        }
        item {
            val real = drives.filterNot { it.demo }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryTile(caps(Str.TOTAL), "%.0f".format(LocalUnits.current.distance(real.sumOf { it.distanceKm })), LocalUnits.current.distanceUnit, Modifier.weight(1f))
                SummaryTile(caps(Str.DRIVES), real.size.toString(), "", Modifier.weight(1f))
                SummaryTile(caps(Str.DURATION), duration(real.sumOf { it.durationSec }), "", Modifier.weight(1f))
            }
        }
        if (drives.isEmpty()) {
            item {
                Text(
                    s(Str.NO_DRIVES),
                    color = Label, fontFamily = Sora, fontSize = 14.sp, modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
        drives.groupBy { dayFmt.format(Date(it.startMs)) }.forEach { (day, list) ->
            item(key = "day-$day") {
                Text(day.uppercase(locale), color = Label, fontFamily = Sora, fontSize = 11.sp, letterSpacing = 2.sp,
                    modifier = Modifier.padding(top = 8.dp))
            }
            items(list, key = { it.startMs }) { d -> DriveCard(d) { openStart = d.startMs } }
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, unit: String, modifier: Modifier) = Column(
    modifier.clip(RoundedCornerShape(20.dp)).background(Glass).border(1.dp, GlassLine, RoundedCornerShape(20.dp))
        .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
) {
    Text(label, color = Label, fontFamily = Sora, fontSize = 10.sp, letterSpacing = 2.sp)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, color = C.Text, fontFamily = Sora, fontWeight = FontWeight.Light, fontSize = 18.sp, maxLines = 1)
        if (unit.isNotEmpty()) Text(" $unit", color = Label, fontFamily = Sora, fontSize = 11.sp, modifier = Modifier.padding(bottom = 2.dp))
    }
}

@Composable
private fun DriveCard(d: Drive, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Glass)
            .border(1.dp, GlassLine, RoundedCornerShape(22.dp)).clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${timeFmt.format(Date(d.startMs))} – ${timeFmt.format(Date(d.endMs))}",
                color = C.Text, fontFamily = Sora, fontSize = 15.sp, modifier = Modifier.weight(1f),
            )
            ScoreBadge(remember(d) { DriveAnalysis.of(d).score })
            if (d.demo) Text("DEMO", color = Color(0xFFFF8A95), fontFamily = Sora, fontSize = 10.sp, letterSpacing = 1.5.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF3A0A10)).padding(horizontal = 8.dp, vertical = 3.dp))
        }
        Sparkline(d.samples.map { LocalUnits.current.speed(it.speedKmh.toDouble())!!.toFloat() }, C.Red, Modifier.fillMaxWidth().height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Stat("%.1f ${LocalUnits.current.distanceUnit}".format(LocalUnits.current.distance(d.distanceKm)))
            Stat(duration(d.durationSec))
            Stat(s(Str.MAX_VALUE, "${LocalUnits.current.speed(d.maxSpeedKmh)!!.toInt()} ${LocalUnits.current.speedUnit}"))
        }
    }
}

@Composable
private fun Stat(text: String) = Text(text, color = C.Text, fontFamily = Sora, fontSize = 13.sp)

@Composable
private fun DriveDetail(d: Drive, onBack: () -> Unit, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    val dayFmt = dayFormat()
    Column(
        Modifier.fillMaxSize().background(C.Bg).verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹ " + s(Str.TAB_HISTORY), color = C.Alarm, fontFamily = Sora, fontSize = 14.sp,
                modifier = Modifier.heightIn(min = 44.dp).clickable(onClick = onBack).padding(vertical = 12.dp, horizontal = 4.dp).weight(1f))
            Text(s(Str.DELETE), color = Label, fontFamily = Sora, fontSize = 14.sp,
                modifier = Modifier.heightIn(min = 44.dp).clickable { confirm = true }.padding(12.dp))
        }
        Column {
            Text(dayFmt.format(Date(d.startMs)).uppercase(LocalStrings.current.lang.locale), color = Label, fontFamily = Sora, fontSize = 11.sp, letterSpacing = 2.sp)
            Text(
                "${timeFmt.format(Date(d.startMs))} – ${timeFmt.format(Date(d.endMs))}",
                color = C.Text, fontFamily = Sora, fontWeight = FontWeight.Light, fontSize = 28.sp,
            )
        }
        val tiles = listOf(
            Triple(caps(Str.DISTANCE), "%.1f".format(LocalUnits.current.distance(d.distanceKm)), LocalUnits.current.distanceUnit),
            Triple(caps(Str.DURATION), duration(d.durationSec), ""),
            Triple(caps(Str.AVG_SPEED), LocalUnits.current.speed(d.avgMovingSpeedKmh)!!.toInt().toString(), LocalUnits.current.speedUnit),
            Triple(caps(Str.MAX_SPEED), LocalUnits.current.speed(d.maxSpeedKmh)!!.toInt().toString(), LocalUnits.current.speedUnit),
            Triple(caps(Str.MAX_RPM), d.maxRpm.toInt().toString(), "rpm"),
            Triple(caps(Str.IDLE), duration(d.idleSec.toLong()), ""),
            Triple(caps(Str.MAX_COOLANT), LocalUnits.current.temp(d.maxCoolantC)?.toInt()?.toString() ?: "—", LocalUnits.current.tempUnit),
            Triple(caps(Str.MIN_BATTERY), d.minBatteryV?.let { "%.1f".format(it) } ?: "—", "V"),
        )
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (l, v, u) -> SummaryTile(l, v, if (v == "—") "" else u, Modifier.weight(1f)) }
            }
        }
        DriveAnalysisCard(remember(d) { DriveAnalysis.of(d) })
        ChartCard("${caps(Str.SPEED)} · ${LocalUnits.current.speedUnit}", d.samples.map { LocalUnits.current.speed(it.speedKmh.toDouble())!!.toFloat() }, C.Red)
        ChartCard("${caps(Str.RPM)} · rpm", d.samples.map { it.rpm.toFloat() }, C.Text)
        ChartCard("${caps(Str.COOLANT_TEMP)} · ${LocalUnits.current.tempUnit}", d.samples.mapNotNull { LocalUnits.current.temp(it.coolantC?.toDouble())?.toFloat() }, Blue)
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(s(Str.DELETE_DRIVE_TITLE)) },
            text = { Text(s(Str.DELETE_DRIVE_BODY)) },
            confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text(s(Str.DELETE), color = C.Alarm) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(s(Str.CANCEL)) } },
        )
    }
}

private fun scoreColor(score: Int) = when {
    score >= 85 -> C.Green
    score >= 60 -> C.Amber
    else -> C.Alarm
}

@Composable
private fun ScoreBadge(score: Int) {
    val color = scoreColor(score)
    Text(
        score.toString(), color = color, fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
        modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Faz 5.6: the score and one plain sentence per finding, car problems first. */
@Composable
internal fun DriveAnalysisCard(a: DriveAnalysis) {
    val units = LocalUnits.current
    fun temp(c: Double?) = "${units.temp(c)?.toInt() ?: "—"} ${units.tempUnit}"
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Glass)
            .border(1.dp, GlassLine, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(caps(Str.DRIVE_ANALYSIS), color = Label, fontFamily = Sora, fontSize = 10.sp, letterSpacing = 2.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(a.score.toString(), color = scoreColor(a.score), fontFamily = Sora, fontWeight = FontWeight.Light, fontSize = 40.sp)
            Text(" / 100  ·  " + s(Str.DRIVE_SCORE), color = Label, fontFamily = Sora, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
        a.findings.forEach { f ->
            val text = when (f.kind) {
                DriveAnalysis.Kind.LONG_IDLE -> s(f.kind.label, f.count, f.percent)
                DriveAnalysis.Kind.OVERHEAT -> s(f.kind.label, temp(f.value))
                DriveAnalysis.Kind.NEVER_WARM -> s(f.kind.label, f.count, temp(f.value))
                DriveAnalysis.Kind.LOW_CHARGING -> s(f.kind.label, "%.1f V".format(f.value ?: 0.0))
                else -> s(f.kind.label, f.count)
            }
            val dot = when {
                f.kind.warning -> C.Alarm
                f.kind == DriveAnalysis.Kind.SMOOTH -> C.Green
                else -> C.Amber
            }
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 6.dp, end = 10.dp).size(8.dp).clip(RoundedCornerShape(4.dp)).background(dot))
                Text(text, color = C.Text, fontFamily = Sora, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, values: List<Float>, color: Color) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Glass)
            .border(1.dp, GlassLine, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row {
            Text(title, color = Label, fontFamily = Sora, fontSize = 10.sp, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
            if (values.isNotEmpty()) Text("${values.min().toInt()} – ${values.max().toInt()}", color = Label, fontFamily = Sora, fontSize = 11.sp)
        }
        if (values.size < 2) Text(s(Str.NO_DATA), color = C.Dim, fontFamily = Sora, fontSize = 13.sp)
        else Sparkline(values, color, Modifier.fillMaxWidth().height(110.dp), fill = true)
    }
}

/** A simple line chart scaled to its own min and max. */
@Composable
private fun Sparkline(values: List<Float>, color: Color, modifier: Modifier, fill: Boolean = false) {
    if (values.size < 2) { Spacer(modifier); return }
    Canvas(modifier) {
        val lo = values.min()
        val hi = values.max().let { if (it - lo < 1f) lo + 1f else it }
        val stepX = size.width / (values.size - 1)
        fun pt(i: Int) = Offset(i * stepX, size.height - (values[i] - lo) / (hi - lo) * size.height * 0.92f - size.height * 0.04f)
        val line = Path().apply {
            moveTo(pt(0).x, pt(0).y)
            for (i in 1 until values.size) lineTo(pt(i).x, pt(i).y)
        }
        if (fill) {
            val area = Path().apply {
                addPath(line)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)))
        }
        drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
