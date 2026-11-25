package app.obdgolf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.obdgolf.FaultState
import app.obdgolf.LinkState
import app.obdgolf.core.Dtc
import app.obdgolf.core.DtcKind
import app.obdgolf.core.FaultReport
import app.obdgolf.core.FreezeFrame
import app.obdgolf.core.Str
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FaultScreen(state: FaultState, link: LinkState, onRead: () -> Unit, onClear: () -> Unit, onLinkClick: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    val connected = link is LinkState.Connected
    val busy = state is FaultState.Busy
    val report = (state as? FaultState.Ready)?.report

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s(Str.FAULT_CODES), color = C.Text, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text(
                    report?.let { s(Str.LAST_READ, SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it.readAtMs))) }
                        ?: s(Str.FAULT_SUBTITLE),
                    color = C.Muted, fontSize = 12.sp,
                )
            }
            LinkChip(link, onLinkClick)
        }

        MilCard(report)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onRead, enabled = connected && !busy,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.Red, disabledContainerColor = C.Track),
            ) { Text(s(Str.READ_CODES), color = C.OnAccent, fontWeight = FontWeight.SemiBold) }
            OutlinedButton(
                onClick = { confirm = true }, enabled = connected && !busy && report != null,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text(s(Str.CLEAR_CODES), color = if (connected && report != null) C.Text else C.Dim) }
        }

        when (state) {
            FaultState.Idle -> Hint(
                if (connected) s(Str.FAULT_HINT_CONNECTED, s(Str.READ_CODES))
                else s(Str.FAULT_HINT_DISCONNECTED)
            )
            is FaultState.Busy -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(22.dp), color = C.Red, strokeWidth = 3.dp)
                Text(s(state.text), color = C.Muted, fontSize = 14.sp)
            }
            is FaultState.Error -> Text(s(state.message), color = C.Amber, fontSize = 14.sp)
            is FaultState.Ready -> {
                if (state.cleared) Text(s(Str.CODES_CLEARED), color = C.Green, fontSize = 14.sp)
                Codes(state.report)
            }
        }

        Hint(s(Str.FAULT_FOOTER))
    }

    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        containerColor = C.Card,
        title = { Text(s(Str.CLEAR_TITLE), color = C.Text) },
        text = {
            Text(
                s(Str.CLEAR_BODY),
                color = C.Muted, fontSize = 14.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = { confirm = false; onClear() }) { Text(s(Str.CLEAR), color = C.Alarm, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(s(Str.CANCEL), color = C.Text) } },
    )
}

@Composable
private fun MilCard(report: FaultReport?) {
    val mil = report?.mil
    val (color, title, sub) = when {
        mil == null && report == null -> Triple(C.Track, s(Str.MIL), s(Str.NOT_READ_YET))
        mil == null -> Triple(C.Dim, s(Str.MIL), s(Str.MIL_UNKNOWN))
        mil.milOn -> Triple(C.Amber, s(Str.MIL_ON), s(Str.STORED_CODES, mil.dtcCount))
        else -> Triple(C.Green, s(Str.MIL_OFF), if (mil.dtcCount == 0) s(Str.NO_STORED_CODES) else s(Str.STORED_CODES, mil.dtcCount))
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Card).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(16.dp).clip(CircleShape).background(color))
        }
        Column {
            Text(title, color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(sub, color = C.Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Codes(report: FaultReport) {
    if (report.codes.isEmpty()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Card).padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(C.Green)
            Text("  " + s(Str.NO_CODES), color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        return
    }
    SectionLabel(s(Str.CODES_FOUND, report.codes.size))
    Card {
        report.codes.forEachIndexed { i, dtc ->
            if (i > 0) HorizontalDivider(color = C.Divider)
            CodeRow(dtc)
        }
    }
    report.freezeFrame?.let { FreezeCard(it) }
}

@Composable
private fun CodeRow(dtc: Dtc) {
    val kindColor = when (dtc.kind) {
        DtcKind.STORED -> C.Red
        DtcKind.PENDING -> C.Amber
        DtcKind.PERMANENT -> C.Muted
    }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(dtc.code, color = C.Text, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
            Text(
                LocalStrings.current.dtcKind(dtc.kind), color = kindColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(kindColor.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Text(LocalStrings.current.describe(dtc), color = C.Text, fontSize = 14.sp)
        if (dtc.manufacturerSpecific) Text(s(Str.MANUFACTURER_HINT), color = C.Dim, fontSize = 12.sp)
    }
}

@Composable
private fun FreezeCard(f: FreezeFrame) {
    SectionLabel(s(Str.FREEZE_FRAME) + (f.dtc?.let { " · $it" } ?: ""))
    Card {
        listOf(
            s(Str.RPM) to "${f.rpm.fmt()} rpm",
            s(Str.SPEED) to "${LocalUnits.current.speed(f.speedKmh).fmt()} ${LocalUnits.current.speedUnit}",
            s(Str.COOLANT_TEMP) to "${LocalUnits.current.temp(f.coolantC).fmt()} ${LocalUnits.current.tempUnit}",
            s(Str.ENGINE_LOAD) to "${f.loadPct.fmt()} %",
            s(Str.ACCELERATOR) to "${f.throttlePct.fmt()} %",
            s(Str.INTAKE_TEMP) to "${LocalUnits.current.temp(f.intakeC).fmt()} ${LocalUnits.current.tempUnit}",
        ).forEachIndexed { i, (l, v) ->
            if (i > 0) HorizontalDivider(color = C.Divider)
            RowItem(l, if (v.startsWith("—")) "—" else v)
        }
    }
}

@Composable
private fun Hint(text: String) = Text(text, color = C.Dim, fontSize = 13.sp, lineHeight = 18.sp)
