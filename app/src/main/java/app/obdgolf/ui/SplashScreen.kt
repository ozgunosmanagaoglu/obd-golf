package app.obdgolf.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Loading screen: the rev arc sweeps to the red zone and back like an ignition-on needle
 * check, while the app gets Bluetooth ready underneath. Calls [onDone] when finished.
 */
@Composable
fun SplashScreen(status: String, onDone: () -> Unit) {
    val sweep = remember { Animatable(0f) }
    val logo = remember { Animatable(0f) }
    var textAlpha by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        launch { logo.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
        delay(200)
        textAlpha = 1f
        sweep.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
        sweep.animateTo(0f, tween(650, easing = LinearEasing))
        delay(150)
        onDone()
    }

    Box(Modifier.fillMaxSize().background(C.Bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val sw = 10.dp.toPx()
                    val topLeft = Offset(sw / 2, sw / 2)
                    val arc = Size(size.width - sw, size.height - sw)
                    drawArc(C.Track, 135f, 270f, false, topLeft, arc, style = Stroke(sw, cap = StrokeCap.Round))
                    val redStart = (RPM_RED / RPM_MAX * 270).toFloat()
                    drawArc(C.RedZone, 135f + redStart, 270f - redStart, false, topLeft, arc, style = Stroke(sw))
                    if (sweep.value > 0.005f) {
                        drawArc(C.Red, 135f, 270f * sweep.value, false, topLeft, arc, style = Stroke(sw, cap = StrokeCap.Round))
                    }
                }
                Box(Modifier.scale(0.6f + 0.4f * logo.value).alpha(logo.value)) {
                    Box(Modifier.scale(1.6f)) { Badge("G6") }
                }
            }
            Column(
                Modifier.alpha(textAlpha).padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("GOLF MK6", color = C.Text, fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, letterSpacing = 3.sp)
                Text("1.4 TSI · DSG", color = C.Muted, fontFamily = Sora, fontWeight = FontWeight.Light, fontSize = 14.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(28.dp))
                Text(status, color = C.Dim, fontSize = 13.sp)
            }
        }
    }
}
