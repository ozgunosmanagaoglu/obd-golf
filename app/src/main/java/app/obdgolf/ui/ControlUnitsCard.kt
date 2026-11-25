package app.obdgolf.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.obdgolf.UnitsState
import app.obdgolf.core.Str
import app.obdgolf.core.UnitInfo

/**
 * VW group cars: part number, software and coding of each control unit. Read only (Faz 4d, step 1);
 * the coding shown here is what a later change would be checked against and restored from.
 */
@Composable
fun ControlUnitsCard(state: UnitsState, onRead: () -> Unit) {
    val reading = state as? UnitsState.Reading
    val found = when (state) {
        is UnitsState.Reading -> state.found
        is UnitsState.Done -> state.found
        UnitsState.Idle -> emptyList()
    }
    SectionLabel(s(Str.CU_TITLE))
    Text(s(Str.CU_HINT), color = C.Muted, fontSize = 12.sp)
    Button(
        onClick = onRead, enabled = reading == null,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = C.Red, disabledContainerColor = C.Track),
    ) {
        Text(
            reading?.let { s(Str.CU_READING, "${it.current.code} ${s(it.current.label)}") } ?: s(Str.CU_READ),
            color = C.OnAccent, fontWeight = FontWeight.SemiBold,
        )
    }
    if (state is UnitsState.Done && found.isEmpty()) Text(s(Str.CU_NONE), color = C.Muted, fontSize = 13.sp)
    if (found.isNotEmpty()) {
        Card {
            found.forEachIndexed { i, info ->
                if (i > 0) HorizontalDivider(color = C.Divider)
                UnitRow(info)
            }
        }
    }
}

@Composable
private fun UnitRow(info: UnitInfo) = Column(
    Modifier.fillMaxWidth().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
) {
    Text("${info.unit.code} · ${s(info.unit.label)}", color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    info.systemName?.let { Text(it, color = C.Muted, fontSize = 13.sp) }
    // Selectable, so part numbers and coding can be copied into a forum post or a note.
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            info.partNumber?.let { Field(s(Str.CU_PART), it) }
            info.softwareVersion?.let { Field(s(Str.CU_SOFTWARE), it) }
            info.codingHex?.let { Field(s(Str.CU_CODING), it.chunked(2).joinToString(" ")) }
            Text(info.protocol.name, color = C.Dim, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Field(label: String, value: String) = Column {
    Text(label, color = C.Dim, fontSize = 11.sp)
    Text(value, color = C.Text, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
}
