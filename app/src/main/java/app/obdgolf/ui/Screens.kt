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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.obdgolf.BlockState
import app.obdgolf.UnitsState
import app.obdgolf.LinkState
import app.obdgolf.ble.BleDevice
import app.obdgolf.bt.PairedDevice
import app.obdgolf.core.CarData
import app.obdgolf.DEMO
import app.obdgolf.core.Lang
import app.obdgolf.core.Msg
import app.obdgolf.core.Str
import app.obdgolf.core.PressureUnit
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import app.obdgolf.core.SpeedUnit
import app.obdgolf.core.TempUnit
import app.obdgolf.core.Units
import app.obdgolf.core.VagUnit
import app.obdgolf.core.VehicleInfo
import app.obdgolf.core.WifiAddress

@Composable
fun Header(link: LinkState, onLinkClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Badge(Brand.BADGE)
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(Brand.TITLE, color = C.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 0.7.sp)
            Text(Brand.SUBTITLE, color = C.Muted, fontSize = 12.sp)
        }
        LinkChip(link, onLinkClick)
    }
}

@Composable
fun LinkChip(link: LinkState, onClick: () -> Unit) {
    val (color, text) = when (link) {
        is LinkState.Connected -> C.Green to if (link.name == DEMO) DEMO else s(Str.CONNECTED)
        is LinkState.Connecting -> C.Amber to s(Str.CONNECTING)
        is LinkState.Failed -> C.Amber to s(Str.WAITING)
        LinkState.Disconnected -> C.Dim to s(Str.NOT_CONNECTED)
    }
    Row(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(C.Raised)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusDot(color)
        Text(text, color = C.Text, fontSize = 13.sp)
    }
}

@Composable
fun DetailsScreen(
    d: CarData,
    vehicle: VehicleInfo?,
    adapterLabel: String?,
    measuring: Boolean = false,
    block: BlockState = BlockState.Idle,
    onReadBlock: (VagUnit, Int) -> Unit = { _, _ -> },
    controlUnits: UnitsState = UnitsState.Idle,
    onReadUnits: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(s(Str.ALL_DATA), color = C.Text, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(d.refreshHz?.let { s(Str.LIVE_HZ, it.fmt(1)) } ?: s(Str.WAITING), color = C.Muted, fontSize = 12.sp)
        }
        if (vehicle != null || adapterLabel != null) {
            SectionLabel(s(Str.VEHICLE))
            Card {
                val rows = listOfNotNull(
                    vehicle?.let { s(Str.VEHICLE) to (it.label ?: s(Str.UNKNOWN_VEHICLE)) },
                    vehicle?.let { "VIN" to it.vin },
                    adapterLabel?.let { s(Str.ADAPTER) to it },
                )
                rows.forEachIndexed { i, (l, v) ->
                    if (i > 0) HorizontalDivider(color = C.Divider)
                    RowItem(l, v)
                }
            }
        }
        SectionLabel(s(Str.STANDARD_OBD))
        Card {
            listOf(
                s(Str.RPM) to "${d.rpm.fmt()} rpm",
                s(Str.SPEED) to "${LocalUnits.current.speed(d.speedKmh).fmt()} ${LocalUnits.current.speedUnit}",
                s(Str.COOLANT_TEMP) to "${LocalUnits.current.temp(d.coolantC).fmt()} ${LocalUnits.current.tempUnit}",
                s(Str.INTAKE_TEMP) to "${LocalUnits.current.temp(d.intakeC).fmt()} ${LocalUnits.current.tempUnit}",
                s(Str.OUTSIDE_TEMP) to "${LocalUnits.current.temp(d.ambientC).fmt()} ${LocalUnits.current.tempUnit}",
                s(Str.ENGINE_LOAD) to "${d.loadPct.fmt()} %",
                s(Str.ACCELERATOR) to "${d.throttlePct.fmt()} %",
                s(Str.BATTERY_VOLTAGE) to "${d.batteryV.fmt(1)} V",
            ).forEachIndexed { i, (l, v) ->
                if (i > 0) HorizontalDivider(color = C.Divider)
                RowItem(l, if (v.startsWith("—")) "—" else v)
            }
        }
        // Everything else this car reports, in the order of the OBD standard.
        val extras = d.extra.entries.sortedBy { it.key.number }
        if (extras.isNotEmpty()) {
            SectionLabel(s(Str.OTHER_DATA))
            Card {
                extras.forEachIndexed { i, (pid, value) ->
                    if (i > 0) HorizontalDivider(color = C.Divider)
                    val (v, unit) = LocalUnits.current.convert(value, pid.unit)
                    RowItem(LocalStrings.current.pid(pid), "${v.fmt(LocalUnits.current.decimalsFor(pid.unit, pid.decimals))} $unit")
                }
            }
            Text(s(Str.OTHER_DATA_HINT), color = C.Muted, fontSize = 12.sp)
        }
        // Maker-specific values (VW group for now) that this car answered while connecting.
        val brand = d.brand.entries.toList()
        if (brand.isNotEmpty()) {
            SectionLabel(s(Str.BRAND_DATA, vehicle?.make ?: "—"))
            Card {
                brand.forEachIndexed { i, (value, raw) ->
                    if (i > 0) HorizontalDivider(color = C.Divider)
                    val (v, unit) = LocalUnits.current.convert(raw, value.unit)
                    RowItem(s(value.label), "${v.fmt(LocalUnits.current.decimalsFor(value.unit, value.decimals))} $unit")
                }
            }
            Text(s(Str.BRAND_DATA_HINT), color = C.Muted, fontSize = 12.sp)
        }
        if (measuring) {
            MeasuringBlockCard(block, onReadBlock)
            ControlUnitsCard(controlUnits, onReadUnits)
        }
    }
}

/** The three kinds of OBD adapter on the market, as tabs on the Connect screen. */
enum class AdapterType(val label: String, val hint: Str) {
    CLASSIC("Bluetooth", Str.ADAPTER_HINT_CLASSIC),
    BLE("BLE", Str.ADAPTER_HINT_BLE),
    WIFI("Wi-Fi", Str.ADAPTER_HINT_WIFI),
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
    wifiAddress: WifiAddress,
    onConnectWifi: (WifiAddress) -> Unit,
    bleDevices: List<BleDevice>,
    bleScanning: Boolean,
    bleStatus: Msg?,
    onScanBle: () -> Unit,
    onConnectBle: (BleDevice) -> Unit,
    onShareLog: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onAutoConnect: (Boolean) -> Unit,
    onDemo: (Boolean) -> Unit,
    units: Units,
    onUnits: (Units) -> Unit,
    language: Lang?,
    onLanguage: (Lang?) -> Unit,
    batteryUnrestricted: Boolean = true,
    onOpenBattery: () -> Unit = {},
    theme: ThemeMode = ThemeMode.DARK,
    onTheme: (ThemeMode) -> Unit = {},
    startOnBoot: Boolean = false,
    onStartOnBoot: (Boolean) -> Unit = {},
    overlayAllowed: Boolean = true,
    onAllowOverlay: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(s(Str.CONNECTION), color = C.Text, fontWeight = FontWeight.Bold, fontSize = 22.sp)

        Card(Modifier.padding(0.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val (title, sub, color) = when (link) {
                    is LinkState.Connected -> Triple(link.name, s(Str.CONNECTED_WITH, link.adapterVersion ?: "ELM327"), C.Green)
                    is LinkState.Connecting -> Triple(link.name, s(Str.CONNECTING_ELLIPSIS), C.Amber)
                    is LinkState.Failed ->
                        Triple(s(if (permissionMissing) Str.PERMISSION_NEEDED else Str.RETRYING), s(link.message), C.Amber)
                    LinkState.Disconnected -> Triple(s(Str.NOT_CONNECTED), s(Str.PICK_ADAPTER), C.Muted)
                }
                Text(title, color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(sub, color = color, fontSize = 13.sp)
                if (permissionMissing) {
                    Button(
                        onClick = onGrantPermission,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = C.Red),
                    ) {
                        Text(s(Str.ALLOW), color = C.OnAccent, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        s(Str.ALLOW_HINT),
                        color = C.Muted, fontSize = 12.sp,
                    )
                } else if (link !is LinkState.Disconnected) {
                    OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(s(Str.DISCONNECT), color = C.Text)
                    }
                }
            }
        }

        // One adapter type at a time keeps the screen short; most people own one adapter.
        var type by rememberSaveable { mutableStateOf(AdapterType.CLASSIC) }
        Segmented(AdapterType.entries, type, { it.label }) { type = it }
        Text(s(type.hint), color = C.Muted, fontSize = 12.sp)

        when (type) {
            AdapterType.CLASSIC -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(s(Str.CLASSIC_PAIRED), Modifier.weight(1f))
                    Text(s(Str.REFRESH), color = C.Alarm, fontSize = 13.sp,
                        modifier = Modifier.clickable(onClick = onRefresh).padding(8.dp))
                }
                Card {
                    when {
                        !bluetoothReady -> RowItem(s(Str.BT_OFF_OR_DENIED), "", muted = true)
                        devices.isEmpty() -> Text(
                            s(Str.NO_PAIRED),
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
                                    when { current -> s(Str.CONNECTED); dev.looksLikeObd -> s(Str.CONNECT); else -> s(Str.NOT_OBD) },
                                    color = if (current) C.Green else C.Muted, fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
            }
            AdapterType.BLE -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(s(Str.BLE_ADAPTER), Modifier.weight(1f))
                    Text(if (bleScanning) s(Str.SEARCHING) else s(Str.SEARCH), color = C.Alarm, fontSize = 13.sp,
                        modifier = Modifier.clickable(enabled = !bleScanning, onClick = onScanBle).padding(8.dp))
                }
                Card {
                    if (bleDevices.isEmpty()) {
                        Text(
                            bleStatus?.let { s(it) } ?: s(Str.BLE_INTRO, s(Str.SEARCH)),
                            color = C.Muted, fontSize = 14.sp, modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        bleDevices.forEachIndexed { i, dev ->
                            if (i > 0) HorizontalDivider(color = C.Divider)
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onConnectBle(dev) }.padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(dev.name, color = C.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                val current = (link as? LinkState.Connected)?.name == dev.name
                                Text(
                                    when { current -> s(Str.CONNECTED); dev.looksLikeObd -> s(Str.CONNECT); else -> s(Str.NOT_OBD) },
                                    color = if (current) C.Green else C.Muted, fontSize = 13.sp,
                                )
                            }
                        }
                        bleStatus?.let {
                            HorizontalDivider(color = C.Divider)
                            Text(s(it), color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            }
            AdapterType.WIFI -> {
                SectionLabel(s(Str.WIFI_ADAPTER))
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            s(Str.WIFI_INTRO),
                            color = C.Muted, fontSize = 13.sp,
                        )
                        var text by remember(wifiAddress) { mutableStateOf(wifiAddress.toString()) }
                        val parsed = WifiAddress.parse(text)
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text(s(Str.WIFI_ADDRESS)) },
                            singleLine = true,
                            isError = parsed == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = C.Text, unfocusedTextColor = C.Text,
                                focusedBorderColor = C.Red, unfocusedBorderColor = C.Divider,
                                focusedLabelColor = C.Red, unfocusedLabelColor = C.Muted, cursorColor = C.Red,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = { parsed?.let(onConnectWifi) },
                            enabled = parsed != null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = C.Red),
                        ) {
                            Text(s(Str.WIFI_CONNECT), color = C.OnAccent, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        SectionLabel(s(Str.SETTINGS))
        Card {
            ToggleRow(s(Str.AUTO_CONNECT), autoConnect, onAutoConnect)
            HorizontalDivider(color = C.Divider)
            ToggleRow(s(Str.DEMO_MODE), demo, onDemo)
            HorizontalDivider(color = C.Divider)
            ToggleRow(s(Str.BOOT_START), startOnBoot, onStartOnBoot)
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(s(Str.BOOT_HINT), color = C.Muted, fontSize = 13.sp)
                if (startOnBoot && !overlayAllowed) {
                    Text(s(Str.BOOT_NEEDS_OVERLAY), color = C.Amber, fontSize = 13.sp)
                    OutlinedButton(onClick = onAllowOverlay, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(s(Str.BOOT_ALLOW), color = C.Text)
                    }
                }
            }
        }

        SectionLabel(s(Str.LANGUAGE))
        LanguagePicker(language, onLanguage)

        SectionLabel(s(Str.THEME))
        val strings = LocalStrings.current
        Segmented(ThemeMode.entries, theme, { strings[it.label] }, onTheme)

        SectionLabel(s(Str.UNITS))
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(s(Str.SPEED_DISTANCE), color = C.Muted, fontSize = 13.sp)
                Segmented(SpeedUnit.entries, units.speed, { it.label }) { onUnits(units.copy(speed = it)) }
                Text(s(Str.TEMPERATURE), color = C.Muted, fontSize = 13.sp)
                Segmented(TempUnit.entries, units.temp, { it.label }) { onUnits(units.copy(temp = it)) }
                Text(s(Str.PRESSURE), color = C.Muted, fontSize = 13.sp)
                Segmented(PressureUnit.entries, units.pressure, { it.label }) { onUnits(units.copy(pressure = it)) }
            }
        }

        SectionLabel(s(Str.BG_TITLE))
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(s(if (batteryUnrestricted) Str.BG_OK else Str.BG_HINT), color = C.Muted, fontSize = 13.sp)
                if (!batteryUnrestricted) {
                    OutlinedButton(onClick = onOpenBattery, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(s(Str.BG_OPEN), color = C.Text)
                    }
                }
            }
        }

        SectionLabel(s(Str.ADAPTER_LOG))
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    s(Str.ADAPTER_LOG_HINT),
                    color = C.Muted, fontSize = 13.sp,
                )
                OutlinedButton(onClick = onShareLog, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(s(Str.SHARE_LOG), color = C.Text)
                }
            }
        }
    }
}

/** Phone language first, then every language we ship, each in its own name. Wraps on narrow screens. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguagePicker(selected: Lang?, onSelect: (Lang?) -> Unit) = Card {
    FlowRow(
        Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        (listOf<Lang?>(null) + Lang.entries).forEach { lang ->
            val on = lang == selected
            Text(
                lang?.nativeName ?: s(Str.LANGUAGE_SYSTEM),
                color = if (on) C.OnAccent else C.Muted,
                fontSize = 13.sp,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (on) C.Red else Color.Transparent)
                    .clickable { onSelect(lang) }.padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
    }
}

/** Pill-style single choice row (adapter type, units). */
@Composable
internal fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) = Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Card).padding(4.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
) {
    options.forEach { t ->
        val on = t == selected
        Text(
            label(t),
            color = if (on) C.OnAccent else C.Muted,
            fontSize = 13.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                .background(if (on) C.Red else Color.Transparent)
                .clickable { onSelect(t) }.padding(vertical = 12.dp),
        )
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
