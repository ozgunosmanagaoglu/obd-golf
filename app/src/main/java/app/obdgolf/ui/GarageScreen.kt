package app.obdgolf.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.obdgolf.LinkState
import app.obdgolf.R
import app.obdgolf.core.CarData
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

val Sora = FontFamily(
    Font(R.font.sora_extralight, FontWeight.ExtraLight),
    Font(R.font.sora_light, FontWeight.Light),
    Font(R.font.sora_semibold, FontWeight.SemiBold),
)

private val Ink = Color(0xFF050608)
private val Label = Color(0xFF9A9EA6)
private val Glass = Color(0x0AFFFFFF)
private val GlassLine = Color(0x12FFFFFF)
private val Fire = Color(0xFFFF5A28)
private val Amber = Color(0xFFFFB020)
private val Hot = Color(0xFFFF2D3D)

/** km/h per 1000 rpm for each DSG gear, roughly, for a 1.4 TSI DSG7. */
private val gearRatios = listOf(7.5, 12.5, 19.5, 28.0, 36.0, 44.0, 52.0)

private fun gearOf(rpm: Double?, speed: Double?): String {
    if (rpm == null || speed == null || speed < 3 || rpm < 500) return "N"
    val perK = speed / (rpm / 1000)
    val i = gearRatios.indices.minBy { kotlin.math.abs(gearRatios[it] - perK) }
    return "D${i + 1}"
}

@Composable
fun GarageScreen(d: CarData, link: LinkState, demo: Boolean, onGas: (Boolean) -> Unit, onLinkClick: () -> Unit) {
    val rpm by animateFloatAsState((d.rpm ?: 0.0).toFloat(), tween(220, easing = LinearEasing), label = "rpm")
    val f = (rpm / 7000f).coerceIn(0f, 1f)
    val red = rpm > 6000

    // Crank angle for the animation, advanced every frame from the current rpm.
    // Shown 40x slower than reality so the pistons can be followed by eye.
    var crank by remember { mutableFloatStateOf(0f) }
    var turbo by remember { mutableFloatStateOf(0f) }
    val rpmNow by rememberUpdatedState(rpm)
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1e9f).coerceAtMost(0.05f)
                    crank = (crank + rpmNow / 60f * 360f * dt / 40f) % 720f
                    turbo = (turbo + rpmNow * dt * 0.5f) % 360f
                }
                last = now
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        // Red ambient light behind the car, stronger with revs.
        Canvas(Modifier.fillMaxWidth().height(520.dp)) {
            drawRect(
                Brush.radialGradient(
                    listOf(Color(0xFFE3001B).copy(alpha = 0.18f + f * 0.4f), Color.Transparent),
                    center = Offset(size.width / 2, size.height * 0.42f),
                    radius = size.width * 0.8f,
                )
            )
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("GARAJ", color = Label, fontFamily = Sora, fontSize = 11.sp, letterSpacing = 3.sp)
                    Row {
                        Text("Golf ", color = C.Text, fontFamily = Sora, fontWeight = FontWeight.Light, fontSize = 24.sp)
                        Text("VI", color = C.Text, fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
                    }
                }
                val (dot, label) = when {
                    link !is LinkState.Connected -> C.Amber to "Bağlı değil"
                    red -> Hot to "Kırmızı bölge"
                    rpm < 1000 -> C.Green to "Rölanti"
                    else -> C.Green to if (demo) "Demo" else "Canlı"
                }
                Row(
                    Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0x0FFFFFFF))
                        .border(1.dp, GlassLine, RoundedCornerShape(18.dp))
                        .pointerInput(Unit) { detectTapGestures { onLinkClick() } }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(7.dp).shadow(6.dp, CircleShape, ambientColor = dot, spotColor = dot).clip(CircleShape).background(dot))
                    Text(label, color = Color(0xFFD7D9DE), fontFamily = Sora, fontSize = 12.sp)
                }
            }

            // The car, gently shaking with the engine.
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.BottomCenter) {
                val shake = sin(crank * PI.toFloat() / 45f) * (0.3f + f) * 1.5f
                Image(
                    painterResource(R.drawable.golf_hero), contentDescription = "Golf VI",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).graphicsLayer { translationY = shake },
                )
                Box(
                    Modifier.fillMaxWidth().height(1.dp).background(
                        Brush.horizontalGradient(listOf(Color.Transparent, Color(0x2EFFFFFF), Color.Transparent))
                    )
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        ((rpm / 10).roundToInt() * 10).toString(),
                        color = if (red) Color(0xFFFF4D5E) else C.Text,
                        fontFamily = Sora, fontWeight = FontWeight.ExtraLight, fontSize = 76.sp, lineHeight = 72.sp, letterSpacing = (-2).sp,
                    )
                    Text("DEVİR / DK", color = Label, fontFamily = Sora, fontSize = 11.sp, letterSpacing = 3.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(d.speedKmh.fmt(), color = C.Text, fontFamily = Sora, fontWeight = FontWeight.Light, fontSize = 40.sp, letterSpacing = (-1).sp)
                    Text("KM/H · ${gearOf(d.rpm, d.speedKmh)}", color = Label, fontFamily = Sora, fontSize = 11.sp, letterSpacing = 3.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
            LedBar(f)

            Spacer(Modifier.height(18.dp))
            GlassCard {
                Row(Modifier.fillMaxWidth()) {
                    Text("MOTOR · 1.4 TSI", color = Label, fontFamily = Sora, fontSize = 11.sp, letterSpacing = 2.5.sp, modifier = Modifier.weight(1f))
                    Text("ATEŞLEME 1-3-4-2", color = Label, fontFamily = Sora, fontSize = 11.sp, letterSpacing = 1.5.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Cylinders(crank, Modifier.weight(1f).height(112.dp))
                    val boost = ((rpm - 1500f) / 4500f * 1.2f).coerceAtLeast(0f)
                    Turbo(turbo, boost, Modifier.size(92.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniTile("SU", d.coolantC.fmt(), "°C", Modifier.weight(1f))
                MiniTile("YÜK", d.loadPct.fmt(), "%", Modifier.weight(1f))
                MiniTile("AKÜ", d.batteryV.fmt(1), "V", Modifier.weight(1f))
            }

            Spacer(Modifier.height(22.dp))
            if (demo) GasButton(f, onGas)
            else Text(
                "Değerler arabadan canlı okunuyor",
                color = C.Dim, fontFamily = Sora, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun LedBar(f: Float) {
    val count = 28
    val lit = (f * count).roundToInt()
    Row(Modifier.fillMaxWidth().height(14.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(count) { i ->
            val col = when { i >= 24 -> Hot; i >= 18 -> Amber; else -> C.Text }
            val on = i < lit
            Box(
                Modifier.weight(1f).height(14.dp)
                    .then(if (on) Modifier.shadow(6.dp, RoundedCornerShape(3.dp), ambientColor = col, spotColor = col) else Modifier)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (on) col else Color(0x12FFFFFF))
            )
        }
    }
}

/** Four cylinders of an inline-4: pistons 1+4 and 2+3 move together, firing order 1-3-4-2. */
@Composable
private fun Cylinders(crank: Float, modifier: Modifier) {
    val phase = floatArrayOf(0f, 180f, 180f, 0f)
    val fireAt = floatArrayOf(0f, 540f, 180f, 360f)
    Canvas(modifier) {
        val gap = 10.dp.toPx()
        val w = (size.width - gap * 3) / 4
        val r = 16.dp.toPx()
        for (i in 0 until 4) {
            val x = i * (w + gap)
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0x0FFFFFFF), Color(0x03FFFFFF)), startY = 0f, endY = size.height),
                Offset(x, 0f), Size(w, size.height), CornerRadius(r),
            )
            drawRoundRect(GlassLine, Offset(x, 0f), Size(w, size.height), CornerRadius(r), style = Stroke(1.dp.toPx()))

            val since = ((crank - fireAt[i]) % 720f + 720f) % 720f
            val glow = if (since < 100f) 1f - since / 100f else 0f
            if (glow > 0f) {
                drawRect(
                    Brush.radialGradient(
                        listOf(Fire.copy(alpha = glow * 0.9f), Color.Transparent),
                        center = Offset(x + w / 2, 0f), radius = w * 0.9f,
                    ),
                    Offset(x, 0f), Size(w, size.height * 0.55f),
                )
            }

            val y = (1 - cos(Math.toRadians((crank + phase[i]).toDouble())).toFloat()) / 2
            val top = 10.dp.toPx() + y * (size.height - 26.dp.toPx() - 30.dp.toPx())
            val inset = 9.dp.toPx()
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFFF1F3F6), Color(0xFF8D939B)), startY = top, endY = top + 26.dp.toPx()),
                Offset(x + inset, top), Size(w - inset * 2, 26.dp.toPx()), CornerRadius(9.dp.toPx()),
            )
        }
    }
}

@Composable
private fun Turbo(angle: Float, boostBar: Float, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 4.dp.toPx()
            val inset = sw / 2 + 2.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(Color(0x14FFFFFF), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw))
            drawArc(Color(0xFFE3001B), -90f, 360f * (boostBar / 1.4f).coerceIn(0f, 1f), false, Offset(inset, inset), arcSize,
                style = Stroke(sw, cap = StrokeCap.Round))
            val c = Offset(size.width / 2, size.height / 2)
            val len = size.width * 0.3f
            rotate(angle, c) {
                for (k in 0 until 6) {
                    val a = Math.toRadians(k * 60.0)
                    val end = Offset(c.x + (cos(a) * len).toFloat(), c.y + (sin(a) * len).toFloat())
                    drawLine(Color(0xE6C9CED4), c, end, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
            }
            drawCircle(Ink, radius = size.width * 0.17f, center = c)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("%.1f".format(boostBar), color = C.Text, fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text("BAR", color = Label, fontFamily = Sora, fontSize = 8.sp, letterSpacing = 2.sp)
        }
    }
}

@Composable
private fun GlassCard(content: @Composable () -> Unit) = Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Glass)
        .border(1.dp, GlassLine, RoundedCornerShape(26.dp)).padding(16.dp)
) { content() }

@Composable
private fun MiniTile(label: String, value: String, unit: String, modifier: Modifier) = Column(
    modifier.clip(RoundedCornerShape(20.dp)).background(Glass)
        .border(1.dp, GlassLine, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
) {
    Text(label, color = Label, fontFamily = Sora, fontSize = 10.sp, letterSpacing = 2.sp)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, color = C.Text, fontFamily = Sora, fontWeight = FontWeight.Light, fontSize = 20.sp)
        if (value != "—") Text(" $unit", color = Label, fontFamily = Sora, fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
    }
}

@Composable
private fun GasButton(f: Float, onGas: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(31.dp)
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(62.dp)
            .shadow((10 + f * 20).dp, shape, ambientColor = Color(0xFFE3001B), spotColor = Color(0xFFE3001B))
            .clip(shape).background(Color(0x0DFFFFFF)).border(1.dp, Color(0x1FFFFFFF), shape)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    onGas(true)
                    try { tryAwaitRelease() } finally { onGas(false) }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxSize().padding(end = maxWidth * (1 - f)).background(Color(0xFFE3001B)))
        Text("BASILI TUT · GAZ", color = Color.White, fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 3.sp)
    }
}
