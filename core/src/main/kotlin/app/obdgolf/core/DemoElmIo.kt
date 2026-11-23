package app.obdgolf.core

import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** A fake ELM327 that answers like a Golf on a short drive, so the app can be tried without the car. */
class DemoElmIo(private val clock: () -> Long = System::currentTimeMillis) : ElmIo {
    private val start = clock()

    override suspend fun exchange(command: String, timeoutMs: Long): String {
        delay(15)
        val t = (clock() - start) / 1000.0
        val wave = (sin(t * 2 * PI / 20) + 1) / 2 // 0..1 over 20 s
        val rpm = 800 + wave * 3200
        val speed = wave * 110
        val coolant = (40 + t * 2).coerceAtMost(90.0)
        return when (command) {
            "ATZ" -> "\r\rELM327 v2.3\r\r"
            "ATRV" -> "14.1V\r"
            "0100" -> "4100BE3FA813\r"
            "010C" -> "410C" + hex2((rpm * 4).roundToInt()) + "\r"
            "010D" -> "410D" + hex1(speed.roundToInt()) + "\r"
            "0104" -> "4104" + hex1((wave * 0.8 * 255).roundToInt()) + "\r"
            "0111" -> "4111" + hex1((wave * 0.6 * 255).roundToInt()) + "\r"
            "0105" -> "4105" + hex1(coolant.roundToInt() + 40) + "\r"
            "010F" -> "410F" + hex1(30 + 40) + "\r"
            "0146" -> "4146" + hex1(6 + 40) + "\r"
            else -> if (command.startsWith("AT")) "OK\r" else "NO DATA\r"
        }
    }

    override fun close() {}

    private fun hex1(v: Int) = "%02X".format(v.coerceIn(0, 255))
    private fun hex2(v: Int) = "%04X".format(v.coerceIn(0, 65535))
}
