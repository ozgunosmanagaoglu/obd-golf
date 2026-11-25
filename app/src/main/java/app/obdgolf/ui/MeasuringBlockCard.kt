package app.obdgolf.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.obdgolf.BlockState
import app.obdgolf.core.MeasuringValue
import app.obdgolf.core.Str
import app.obdgolf.core.VagUnit

/**
 * Older VW group cars: pick a control unit and a group number, read the measuring block once.
 * Each value carries its own unit, so this works without knowing the car.
 */
@Composable
fun MeasuringBlockCard(state: BlockState, onRead: (VagUnit, Int) -> Unit) {
    var unit by rememberSaveable { mutableStateOf(VagUnit.ENGINE) }
    var group by rememberSaveable { mutableStateOf(1) }
    val busy = state is BlockState.Busy

    SectionLabel(s(Str.MB_TITLE))
    Segmented(VagUnit.entries, unit, { s(it.label) }) { unit = it }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { group = (group - 1).coerceAtLeast(0) }, shape = RoundedCornerShape(12.dp)) { Text("−", color = C.Text) }
        Text(s(Str.MB_GROUP, "%03d".format(group)), color = C.Text, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, modifier = Modifier.width(96.dp))
        OutlinedButton(onClick = { group = (group + 1).coerceAtMost(255) }, shape = RoundedCornerShape(12.dp)) { Text("+", color = C.Text) }
        Button(
            onClick = { onRead(unit, group) }, enabled = !busy,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.Red, disabledContainerColor = C.Track),
        ) { Text(if (busy) s(Str.MB_READING) else s(Str.MB_READ), color = Color.White, fontWeight = FontWeight.SemiBold) }
    }
    when (state) {
        is BlockState.Ready -> Card {
            state.block.values.forEachIndexed { i, v ->
                if (i > 0) HorizontalDivider(color = C.Divider)
                RowItem(s(Str.MB_VALUE, i + 1), show(v), muted = v.value == null && v.text == null)
            }
        }
        BlockState.NoAnswer -> Text(s(Str.MB_NO_ANSWER), color = C.Muted, fontSize = 13.sp)
        is BlockState.Error -> Text(s(state.message), color = C.Muted, fontSize = 13.sp)
        else -> {}
    }
    Text(s(Str.MB_HINT), color = C.Muted, fontSize = 12.sp)
}

@Composable
private fun show(v: MeasuringValue): String {
    v.text?.let { return it }
    val value = v.value ?: return v.raw
    val (shown, unit) = LocalUnits.current.convert(value, v.unit)
    val decimals = if (v.unit in setOf("V", "ms", "L/h") || (shown != Math.rint(shown) && kotlin.math.abs(shown) < 100)) 1 else 0
    return "${shown.fmt(if (v.unit == "V") 2 else decimals)} $unit".trim()
}
