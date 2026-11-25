package app.obdgolf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.obdgolf.Edition
import app.obdgolf.LinkState
import app.obdgolf.bt.PairedDevice
import app.obdgolf.core.CarData

@Composable
fun Header(link: LinkState, onLinkClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Badge(Edition.BADGE)
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(Edition.TITLE, color = C.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 0.7.sp)
            Text(Edition.SUBTITLE, color = C.Muted, fontSize = 12.sp)
        }
        LinkChip(link, onLinkClick)
    }
}

@Composable
fun LinkChip(link: LinkState, onClick: () -> Unit) {
    val (color, text) = when (link) {
        is LinkState.Connected -> C.Green to if (link.name == "Demo") "Demo" else "Bağlı"
        is LinkState.Connecting -> C.Amber to "Bağlanıyor"
        is LinkState.Failed -> C.Amber to "Bekleniyor"
        LinkState.Disconnected -> C.Dim to "Bağlı değil"
    }
    Row(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF1A1C20))
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusDot(color)
        Text(text, color = C.Text, fontSize = 13.sp)
    }
}

@Composable
fun DashboardScreen(d: CarData, link: LinkState, onLinkClick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Header(link, onLinkClick)
        RevGauge(d.rpm, d.speedKmh, diameter = 300.dp, stroke = 16.dp, big = 64.sp, small = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ValueTile("Su sıcaklığı", d.coolantC.fmt(), "°C", Modifier.weight(1f),
                bar = ((d.coolantC ?: 0.0) / 130).toFloat(), barColor = coolantColor(d.coolantC))
            ValueTile("Akü voltajı", d.batteryV.fmt(1), "V", Modifier.weight(1f),
                bar = (((d.batteryV ?: 11.0) - 11) / 4).toFloat(), barColor = voltageColor(d.batteryV))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ValueTile("Emme sıcaklığı", d.intakeC.fmt(), "°C", Modifier.weight(1f))
            ValueTile("Motor yükü", d.loadPct.fmt(), "%", Modifier.weight(1f))
        }
    }
}

@Composable
fun DetailsScreen(d: CarData) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Tüm veriler", color = C.Text, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(d.refreshHz?.let { "Canlı · ${it.fmt(1)} Hz" } ?: "Bekleniyor", color = C.Muted, fontSize = 12.sp)
        }
        SectionLabel("Standart OBD")
        Card {
            listOf(
                "Devir" to "${d.rpm.fmt()} rpm",
                "Hız" to "${d.speedKmh.fmt()} km/h",
                "Su sıcaklığı" to "${d.coolantC.fmt()} °C",
                "Emme sıcaklığı" to "${d.intakeC.fmt()} °C",
                "Dış sıcaklık" to "${d.ambientC.fmt()} °C",
                "Motor yükü" to "${d.loadPct.fmt()} %",
                "Gaz pedalı" to "${d.throttlePct.fmt()} %",
                "Akü voltajı" to "${d.batteryV.fmt(1)} V",
            ).forEachIndexed { i, (l, v) ->
                if (i > 0) HorizontalDivider(color = C.Divider)
                RowItem(l, if (v.startsWith("—")) "—" else v)
            }
        }
        if (Edition.SHOW_VW_EXTRAS) VwExtras()
    }
}

/** Placeholder rows for the VW-only values (personal build only, for now). */
@Composable
private fun VwExtras() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("VW'ye özel", Modifier.weight(1f))
            Text("Yakında", color = Color(0xFFFF8A95), fontSize = 11.sp,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF3A0A10)).padding(horizontal = 8.dp, vertical = 3.dp))
        }
        Card {
            listOf("Yağ sıcaklığı", "DSG sıcaklığı", "Turbo basıncı", "Klima / iç sıcaklık", "Yakıt tüketimi").forEachIndexed { i, l ->
                if (i > 0) HorizontalDivider(color = C.Divider)
                RowItem(l, "—", muted = true)
            }
        }
    }
}

@Composable
fun ConnectScreen(
    link: LinkState,
    bluetoothReady: Boolean,
    devices: List<PairedDevice>,
    autoConnect: Boolean,
    demo: Boolean,
    permissionMissing: Boolean,
    onGrantPermission: () -> Unit,
    onConnect: (PairedDevice) -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onAutoConnect: (Boolean) -> Unit,
    onDemo: (Boolean) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Bağlantı", color = C.Text, fontWeight = FontWeight.Bold, fontSize = 22.sp)

        Card(Modifier.padding(0.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val (title, sub, color) = when (link) {
                    is LinkState.Connected -> Triple(link.name, "Bağlı · ${link.adapterVersion ?: "ELM327"}", C.Green)
                    is LinkState.Connecting -> Triple(link.name, "Bağlanıyor…", C.Amber)
                    is LinkState.Failed ->
                        Triple(if (permissionMissing) "İzin gerekli" else "Yeniden deneniyor", link.message, C.Amber)
                    LinkState.Disconnected -> Triple("Bağlı değil", "Aşağıdan adaptörü seç", C.Muted)
                }
                Text(title, color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(sub, color = color, fontSize = 13.sp)
                if (permissionMissing) {
                    Button(
                        onClick = onGrantPermission,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = C.Red),
                    ) {
                        Text("İzin ver", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "Pencere açılmazsa ayarlar sayfası açılır: İzinler > Yakındaki cihazlar > İzin ver.",
                        color = C.Muted, fontSize = 12.sp,
                    )
                } else if (link !is LinkState.Disconnected) {
                    OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Bağlantıyı kes", color = C.Text)
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Eşleşmiş cihazlar", Modifier.weight(1f))
            Text("Yenile", color = Color(0xFFFF4D5E), fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onRefresh).padding(8.dp))
        }
        Card {
            when {
                !bluetoothReady -> RowItem("Bluetooth kapalı ya da izin verilmedi", "", muted = true)
                devices.isEmpty() -> Text(
                    "Eşleşmiş cihaz yok. Telefonun Bluetooth ayarlarından adaptörü eşleştir (PIN genelde 1234 ya da 0000).",
                    color = C.Muted, fontSize = 14.sp, modifier = Modifier.padding(16.dp),
                )
                else -> devices.forEachIndexed { i, dev ->
                    if (i > 0) HorizontalDivider(color = C.Divider)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onConnect(dev) }.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(dev.name, color = C.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        val current = (link as? LinkState.Connected)?.name == dev.name
                        Text(
                            when { current -> "Bağlı"; dev.looksLikeObd -> "Bağlan"; else -> "OBD değil?" },
                            color = if (current) C.Green else C.Muted, fontSize = 13.sp,
                        )
                    }
                }
            }
        }

        SectionLabel("Ayarlar")
        Card {
            ToggleRow("Açılışta otomatik bağlan", autoConnect, onAutoConnect)
            HorizontalDivider(color = C.Divider)
            ToggleRow("Demo modu (araba olmadan)", demo, onDemo)
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) = Row(
    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(label, color = C.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
    Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = C.Red))
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Card)) { content() }

/** Landscape layout for the permanent in-car screen. */
@Composable
fun CarScreen(d: CarData, link: LinkState, onLinkClick: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(Edition.BADGE)
            Text(Edition.TITLE, color = C.Text, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                modifier = Modifier.padding(start = 12.dp).weight(1f))
            Text("Dış ${d.ambientC.fmt()} °C", color = C.Muted, fontSize = 15.sp, modifier = Modifier.padding(end = 16.dp))
            LinkChip(link, onLinkClick)
        }
        Spacer(Modifier.padding(6.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(400.dp).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                RevGauge(d.rpm, d.speedKmh, diameter = 380.dp, stroke = 18.dp, big = 88.sp, small = 15.sp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val tiles = listOf(
                    Triple("Su sıcaklığı", d.coolantC.fmt(), "°C"),
                    Triple("Akü", d.batteryV.fmt(1), "V"),
                    Triple("Motor yükü", d.loadPct.fmt(), "%"),
                    Triple("Emme sıcaklığı", d.intakeC.fmt(), "°C"),
                ) + if (Edition.SHOW_VW_EXTRAS) listOf(
                    Triple("Yağ sıcaklığı", "—", "°C"),
                    Triple("DSG sıcaklığı", "—", "°C"),
                ) else listOf(
                    Triple("Gaz pedalı", d.throttlePct.fmt(), "%"),
                    Triple("Dış sıcaklık", d.ambientC.fmt(), "°C"),
                )
                tiles.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        pair.forEach { (l, v, u) -> ValueTile(l, v, u, Modifier.weight(1f), valueSize = 40.sp) }
                    }
                }
            }
        }
    }
}
