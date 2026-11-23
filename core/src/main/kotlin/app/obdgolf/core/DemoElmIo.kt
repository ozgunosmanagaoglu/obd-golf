package app.obdgolf.core

import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A fake ELM327 that answers like the Golf, so the app can be tried without the car.
 * Idles at 800 rpm; while [gas] is true it revs towards 6400 like a stationary rev in the garage.
 */
class DemoElmIo(private val clock: () -> Long = System::currentTimeMillis) : ElmIo {
    @Volatile var gas: Boolean = false

    private val start = clock()
    private var last = clock()
    private var rpm = 800.0

    override suspend fun exchange(command: String, timeoutMs: Long): String {
        delay(15)
        step()
        val t = (clock() - start) / 1000.0
        val f = (rpm - 800) / 5600
        val speed = max(0.0, f) * 140
        val coolant = min(90.0, 40 + t * 2)
        return when (command) {
            "ATZ" -> "\r\rELM327 v2.3\r\r"
            "ATRV" -> "14.1V\r"
            "0100" -> "4100BE3FA813\r"
            "010C" -> "410C" + hex2((rpm * 4).roundToInt()) + "\r"
            "010D" -> "410D" + hex1(speed.roundToInt()) + "\r"
            "0104" -> "4104" + hex1(((0.14 + (if (gas) 0.68 else 0.0) + f * 0.18) * 255).roundToInt()) + "\r"
            "0111" -> "4111" + hex1(((if (gas) 0.7 else 0.0) * 255).roundToInt()) + "\r"
            "0105" -> "4105" + hex1(coolant.roundToInt() + 40) + "\r"
            "010F" -> "410F" + hex1(30 + 40) + "\r"
            "0146" -> "4146" + hex1(6 + 40) + "\r"
            else -> if (command.startsWith("AT")) "OK\r" else "NO DATA\r"
        }
    }

    private fun step() {
        val now = clock()
        val dt = min((now - last) / 1000.0, 0.1)
        last = now
        val target = if (gas) 6400.0 else 800.0
        val k = if (gas) 1.4 else 2.2
        rpm += (target - rpm) * min(1.0, k * dt)
    }

    override fun close() {}

    private fun hex1(v: Int) = "%02X".format(v.coerceIn(0, 255))
    private fun hex2(v: Int) = "%04X".format(v.coerceIn(0, 65535))
}
