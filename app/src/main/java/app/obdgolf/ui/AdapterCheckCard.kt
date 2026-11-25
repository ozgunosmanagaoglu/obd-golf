package app.obdgolf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.obdgolf.core.AdapterCheck
import app.obdgolf.core.Str

/**
 * What the adapter check found (Faz 5.3). A good adapter gets one calm line; a clone or a limited one
 * gets the reasons and the adapters we recommend, so the user knows why control units or coding may fail.
 */
@Composable
fun AdapterCheckCard(check: AdapterCheck) {
    val color = when (check.verdict) {
        AdapterCheck.Verdict.STN, AdapterCheck.Verdict.OK -> C.Green
        AdapterCheck.Verdict.CLONE -> C.Amber
        AdapterCheck.Verdict.LIMITED -> C.Alarm
    }
    val good = check.verdict == AdapterCheck.Verdict.STN || check.verdict == AdapterCheck.Verdict.OK
    SectionLabel(s(Str.ADAPTER_CHECK))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (good) C.Card else color.copy(alpha = 0.12f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(s(check.verdict.label), color = if (good) C.Text else color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
        check.reasons.forEach { r ->
            val text = if (r == AdapterCheck.Reason.MISSING_COMMANDS) s(r.label, check.missing.joinToString(", ")) else s(r.label)
            Text("• $text", color = C.Text, fontSize = 13.sp, lineHeight = 18.sp)
        }
        if (!good) Text(s(Str.ADAPTER_RECOMMENDED, AdapterCheck.RECOMMENDED), color = C.Muted, fontSize = 13.sp, lineHeight = 18.sp)
    }
}
