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
import app.obdgolf.core.Drive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Label = Color(0xFF9A9EA6)
private val Glass = Color(0x0AFFFFFF)
private val GlassLine = Color(0x12FFFFFF)
private val Blue = Color(0xFF5AA9FF)

private val dayFmt = SimpleDateFormat("d MMMM EEEE", Locale("tr"))
private val timeFmt = SimpleDateFormat("HH:mm", Locale("tr"))

private fun duration(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return if (h > 0) "${h} sa ${m} dk" else "${m} dk"
}

@Composable
fun HistoryScreen(drives: List<Drive>, onDelete: (Drive) -> Unit) {
    var openStart by rememberSaveable { mutableStateOf<Long?>(null) }
    val open = drives.firstOrNull { it.startMs == openStart }
    if (open != null) {
        DriveDetail(open, onBack = { openStart = null }, onDelete = { onDelete(open); openStart = null })
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().background(Color(0xFF050608)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text("GEÇMİŞ", color = Label, fontFamily = Sora, fontSize = 11.sp, letterSpacing = 3.sp)
                Text("Sürüşlerin", color = C.Text, fontFamily = Sora, fontWeight = FontWeight.Light, fontSize = 24.sp)
            }
        }
        item {
            val real = drives.filterNot { it.demo }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryTile("TOPLAM", "%.0f".format(real.sumOf { it.distanceKm }), "km", Modifier.weight(1f))
                SummaryTile("SÜRÜŞ", real.size.toString(), "", Modifier.weight(1f))
                SummaryTile("SÜRE", duration(real.sumOf { it.durationSec }), "", Modifier.weight(1f))
            }
        }
        if (drives.isEmpty()) {
            item {
                Text(
                    "Henüz kayıtlı sürüş yok. Araca bağlanıp motoru çalıştırdığında sürüşler kendiliğinden kaydedilir.",
                    color = Label, fontFamily = Sora, fontSize = 14.sp, modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
        drives.groupBy { dayFmt.format(Date(it.startMs)) }.forEach { (day, list) ->
            item(key = "day-$day") {
                Text(day.uppercase(Locale("tr")), color = Label, fontFamily = Sora, fontSize = 11.sp, letterSpacing = 2.sp,
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
            if (d.demo) Text("DEMO", color = Color(0xFFFF8A95), fontFamily = Sora, fontSize = 10.sp, letterSpacing = 1.5.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF3A0A10)).padding(horizontal = 8.dp, vertical = 3.dp))
        }
        Sparkline(d.samples.map { it.speedKmh.toFloat() }, Color(0xFFE3001B), Modifier.fillMaxWidth().height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Stat("%.1f km".format(d.distanceKm))
            Stat(duration(d.durationSec))
            Stat("maks ${d.maxSpeedKmh.toInt()} km/h")
        }
    }
}

@Composable
private fun Stat(text: String) = Text(text, color = Color(0xFFD7D9DE), fontFamily = Sora, fontSize = 13.sp)

@Composable
private fun DriveDetail(d: Drive, onBack: () -> Unit, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(Color(0xFF050608)).verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹ Geçmiş", color = Color(0xFFFF4D5E), fontFamily = Sora, fontSize = 14.sp,
                modifier = Modifier.heightIn(min = 44.dp).clickable(onClick = onBack).padding(vertical = 12.dp, horizontal = 4.dp).weight(1f))
            Text("Sil", color = Label, fontFamily = Sora, fontSize = 14.sp,
                modifier = Modifier.heightIn(min = 44.dp).clickable { confirm = true }.padding(12.dp))
        }
        Column {
            Text(dayFmt.format(Date(d.startMs)).uppercase(Locale("tr")), color = Label, fontFamily = Sora, fontSize = 11.sp, letterSpacing = 2.sp)
            Text(
                "${timeFmt.format(Date(d.startMs))} – ${timeFmt.format(Date(d.endMs))}",
                color = C.Text, fontFamily = Sora, fontWeight = FontWeight.Light, fontSize = 28.sp,
            )
        }
        val tiles = listOf(
            Triple("MESAFE", "%.1f".format(d.distanceKm), "km"),
            Triple("SÜRE", duration(d.durationSec), ""),
            Triple("ORT. HIZ", d.avgMovingSpeedKmh.toInt().toString(), "km/h"),
            Triple("MAKS HIZ", d.maxSpeedKmh.toInt().toString(), "km/h"),
            Triple("MAKS DEVİR", d.maxRpm.toInt().toString(), "rpm"),
            Triple("RÖLANTİ", duration(d.idleSec.toLong()), ""),
            Triple("MAKS SU", d.maxCoolantC?.toInt()?.toString() ?: "—", "°C"),
            Triple("MİN AKÜ", d.minBatteryV?.let { "%.1f".format(it) } ?: "—", "V"),
        )
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (l, v, u) -> SummaryTile(l, v, if (v == "—") "" else u, Modifier.weight(1f)) }
            }
        }
        ChartCard("HIZ · km/h", d.samples.map { it.speedKmh.toFloat() }, Color(0xFFE3001B))
        ChartCard("DEVİR · rpm", d.samples.map { it.rpm.toFloat() }, C.Text)
        ChartCard("SU SICAKLIĞI · °C", d.samples.mapNotNull { it.coolantC?.toFloat() }, Blue)
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Sürüş silinsin mi?") },
            text = { Text("Bu kayıt kalıcı olarak silinir.") },
            confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text("Sil", color = Color(0xFFFF4D5E)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Vazgeç") } },
        )
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
        if (values.size < 2) Text("Veri yok", color = C.Dim, fontFamily = Sora, fontSize = 13.sp)
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
