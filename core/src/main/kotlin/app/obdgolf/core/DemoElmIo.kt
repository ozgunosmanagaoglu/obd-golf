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

    // Demo faults: underboost, misfire and intake flap stored, lean mixture pending. Cleared by mode 04.
    @Volatile private var faults = true

    private val start = clock()
    private var last = clock()
    private var rpm = 800.0
    private var header = "7DF"

    // Older VW cars: engine, DSG and climate measuring blocks over TP2.0.
    private val tp20 = Tp20Sim(setOf(0x01, 0x02, 0x08), { unit, group -> demoBlock(unit, group) }, ::demoIdent)

    /** KWP2000 identification like a PQ35 car: part number, then component name and version. */
    private fun demoIdent(unit: Int): IntArray? = when (unit) {
        0x01 -> ascii("03C906027BH ") + intArrayOf(0x00, 0x01) + ascii("1.4l R4/4V TFSI  0010")
        0x02 -> ascii("02E300042C  ") + intArrayOf(0x00, 0x02) + ascii("GSG DSG       5404")
        0x08 -> ascii("1K0907044CA ") + intArrayOf(0x00, 0x00) + ascii("Climatronic   0510")
        else -> null
    }

    private fun ascii(text: String) = text.map { it.code }.toIntArray()

    /** Central electrics answers UDS on 70E/778, as on later PQ35 cars. */
    private fun demoUds(command: String): String? {
        val ident = command in setOf("22F187", "22F189", "22F191", "22F197", "220600")
        if (header != "70E") return if (ident) "NO DATA\r" else null
        fun text(did: String, value: String) = "62$did" + value.map { "%02X".format(it.code) }.joinToString("") + "\r"
        return when (command) {
            "22F187" -> text("F187", "5K0937087AC")
            "22F189" -> text("F189", "0507")
            "22F191" -> text("F191", "5K0937087AC")
            "22F197" -> text("F197", "BCM PQ35 H+3")
            // Long coding, 30 bytes: one multi-frame message as the adapter prints it.
            "220600" -> "021\r0:6206000E1A00\r1:070F00C4200806\r2:1F008040000000\r3:00010000002800\r4:00000000000000\r"
            else -> if (command.startsWith("22") && command.length == 6) "7F2231\r" else null
        }
    }

    private fun demoBlock(unit: Int, group: Int): IntArray? {
        val t = (clock() - start) / 1000.0
        val warm = min(90.0, 40 + t * 2)
        return when (unit to group) {
            // rpm (0.2·a·b), coolant (a·(b−100)·0.1), lambda control (a·(b−128)/128 %), status bits
            0x01 to 1 -> intArrayOf(0x01, 0xC8, (rpm / 40).roundToInt().coerceIn(0, 255), 0x05, 0x0A, (warm + 100).roundToInt(), 0x14, 0x64, 0x83, 0x10, 0x00, 0x24)
            // DSG oil temperature (b − a)
            0x02 to 11 -> intArrayOf(0x1A, 0x00, min(80.0, 22 + t).roundToInt(), 0x08, 0x0A, 0x00, 0x08, 0x0A, 0x00, 0x08, 0x0A, 0x00)
            // Climate: outside temperature, then the fan
            0x08 to 1 -> intArrayOf(0x05, 0x0A, 106, 0x21, 0x64, 45, 0x08, 0x0A, 0x00, 0x08, 0x0A, 0x00)
            else -> null
        }
    }

    override suspend fun exchange(command: String, timeoutMs: Long): String {
        delay(15)
        step()
        val t = (clock() - start) / 1000.0
        val f = (rpm - 800) / 5600
        val speed = max(0.0, f) * 140
        val coolant = min(90.0, 40 + t * 2)
        tp20.exchange(command)?.let { reply ->
            if (command.startsWith("ATSH")) header = command.removePrefix("ATSH")
            return reply
        }
        if (command.startsWith("ATFC") || command.startsWith("ATCRA")) return "OK\r"
        demoUds(command)?.let { return it }
        return when (command) {
            // VW group (MQB) extras: engine oil and DSG temperature, each from its own control unit.
            "2211BE" -> if (header == "7E0") "6211BE" + hex2((min(96.0, 25 + t * 1.5) * 10).roundToInt() + 2731) + "\r" else "NO DATA\r"
            "222104" -> if (header == "7E1") "622104" + hex1(min(78.0, 22 + t).roundToInt()) + "\r" else "NO DATA\r"
            "ATZ" -> "\r\rELM327 v2.3\r\r"
            "ATRV" -> "14.1V\r"
            "STI" -> "?\r"
            AdapterCheck.BOGUS -> "?\r"
            "ATDPN" -> "A6\r"
            "0100" -> "4100BE3FA813\r"
            "0120" -> "41208002A011\r"
            "0140" -> "4140FED00000\r"
            "010C" -> "410C" + hex2((rpm * 4).roundToInt()) + "\r"
            "010D" -> "410D" + hex1(speed.roundToInt()) + "\r"
            "0104" -> "4104" + hex1(((0.14 + (if (gas) 0.68 else 0.0) + f * 0.18) * 255).roundToInt()) + "\r"
            "0111" -> "4111" + hex1(((if (gas) 0.7 else 0.0) * 255).roundToInt()) + "\r"
            "0105" -> "4105" + hex1(coolant.roundToInt() + 40) + "\r"
            "010F" -> "410F" + hex1(30 + 40) + "\r"
            "0146" -> "4146" + hex1(6 + 40) + "\r"
            "0902" -> "014\r0:490201575657\r1:5A5A5A314B5A42\r2:50303030303030\r" // WVWZZZ1KZBP000000 (fake)
            "0106" -> "4106" + hex1(128 + 3) + "\r"
            "0107" -> "4107" + hex1(128 - 4) + "\r"
            "010B" -> "410B" + hex1((35 + f * 150).roundToInt()) + "\r"
            "010E" -> "410E" + hex1(((12 + f * 20 + 64) * 2).roundToInt()) + "\r"
            "0110" -> "4110" + hex2(((3 + f * 90) * 100).roundToInt()) + "\r"
            "011F" -> "411F" + hex2(t.roundToInt()) + "\r"
            "0121" -> "41210000\r"
            "012F" -> "412F" + hex1(158) + "\r"
            "0131" -> "4131" + hex2(1834) + "\r"
            "0133" -> "413365\r"
            "013C" -> "413C" + hex2(((420 + f * 250 + 40) * 10).roundToInt()) + "\r"
            "0142" -> "4142" + hex2(14100) + "\r"
            "0143" -> "4143" + hex2(((0.18 + f * 0.6) * 255).roundToInt()) + "\r"
            "0144" -> "41448000\r"
            "0145" -> "4145" + hex1(((if (gas) 0.6 else 0.02) * 255).roundToInt()) + "\r"
            "0147" -> "4147" + hex1(((if (gas) 0.7 else 0.15) * 255).roundToInt()) + "\r"
            "0149" -> "4149" + hex1(((if (gas) 0.8 else 0.16) * 255).roundToInt()) + "\r"
            "014A" -> "414A" + hex1(((if (gas) 0.4 else 0.08) * 255).roundToInt()) + "\r"
            "014C" -> "414C" + hex1(((if (gas) 0.7 else 0.03) * 255).roundToInt()) + "\r"
            "0101" -> if (faults) "41 01 83 07 64 04\r" else "4101000764 00\r"
            // Engine answers in two CAN frames, the DSG answers too (no codes).
            "03" -> if (faults) "008\r0:43 03 02 99 03 01\r1:20 15 00 00 00 00 00\r4300\r" else "4300\r4300\r"
            "07" -> if (faults) "47010171\r" else "4700\r"
            "0A" -> "4A00\r"
            "04" -> { faults = false; "44\r44\r" }
            "020200" -> if (faults) "4202000299\r" else "4202000000\r"
            "020C00" -> "420C00" + hex2(2850 * 4) + "\r"
            "020D00" -> "420D00" + hex1(64) + "\r"
            "020500" -> "420500" + hex1(88 + 40) + "\r"
            "020400" -> "420400" + hex1(92 * 255 / 100) + "\r"
            "021100" -> "421100" + hex1(81 * 255 / 100) + "\r"
            "020F00" -> "420F00" + hex1(34 + 40) + "\r"
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
