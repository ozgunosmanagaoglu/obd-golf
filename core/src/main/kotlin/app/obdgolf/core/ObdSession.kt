package app.obdgolf.core

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Talks to an ELM327: sets it up once, then keeps polling.
 * Fast values (rpm, speed, load, throttle) every loop; slow ones every [slowEvery] loops.
 */
class ObdSession(private val io: ElmIo, private val slowEvery: Int = 10) {

    var adapterVersion: String? = null
        private set

    // Live polling and fault reading share one adapter: one command at a time.
    private val lock = Mutex()
    private suspend fun ask(command: String, timeoutMs: Long = 2_000) = lock.withLock { io.exchange(command, timeoutMs) }

    suspend fun initialize() {
        val reset = ask("ATZ", timeoutMs = 5_000)
        adapterVersion = reset.split('\r', '\n').map { it.trim() }.firstOrNull { it.startsWith("ELM") }
        for (cmd in listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATAT1", "ATSP0")) {
            ask(cmd)
        }
        // First real request makes the adapter detect the protocol (CAN 11-bit 500k on a Mk6).
        ask("0100", timeoutMs = 10_000)
    }

    fun poll(): Flow<CarData> = flow {
        val fast = listOf(Pid.RPM, Pid.SPEED, Pid.ENGINE_LOAD, Pid.THROTTLE)
        val slow = listOf(Pid.COOLANT_TEMP, Pid.INTAKE_TEMP, Pid.AMBIENT_TEMP)
        var data = CarData()
        var loop = 0
        var windowStart = System.currentTimeMillis()
        var windowLoops = 0
        while (currentCoroutineContext().isActive) {
            for (pid in fast) data = data.with(pid, ElmParser.parsePid(pid, ask(pid.command)))
            if (loop % slowEvery == 0) {
                for (pid in slow) data = data.with(pid, ElmParser.parsePid(pid, ask(pid.command)))
                data = data.copy(batteryV = ElmParser.parseVoltage(ask("ATRV")))
            }
            loop++
            windowLoops++
            val elapsed = System.currentTimeMillis() - windowStart
            if (elapsed >= 2_000) {
                data = data.copy(refreshHz = windowLoops * 1000.0 / elapsed)
                windowStart = System.currentTimeMillis()
                windowLoops = 0
            }
            emit(data)
        }
    }

    /** Reads engine lamp, stored / pending / permanent codes and freeze frame 0. */
    suspend fun readFaults(nowMs: Long = System.currentTimeMillis()): FaultReport {
        val mil = DtcParser.parseMil(ask("0101"))
        val codes = mutableListOf<Dtc>()
        for ((mode, kind) in listOf("03" to DtcKind.STORED, "07" to DtcKind.PENDING, "0A" to DtcKind.PERMANENT)) {
            val raw = runCatching { ask(mode, timeoutMs = 5_000) }.getOrDefault("")
            DtcParser.parse(raw, mode).forEach { c -> if (codes.none { it.code == c }) codes += Dtc(c, kind) }
        }
        val freezeDtc = runCatching { DtcParser.parseFreezeDtc(ask("020200")) }.getOrNull()
        val freeze = freezeDtc?.let { dtc ->
            suspend fun f(pid: Pid) = runCatching { DtcParser.parseFreezePid(pid, ask("02${pid.code}00")) }.getOrNull()
            FreezeFrame(
                dtc = dtc,
                rpm = f(Pid.RPM), speedKmh = f(Pid.SPEED), coolantC = f(Pid.COOLANT_TEMP),
                loadPct = f(Pid.ENGINE_LOAD), throttlePct = f(Pid.THROTTLE), intakeC = f(Pid.INTAKE_TEMP),
            )
        }
        return FaultReport(mil, codes, freeze, nowMs)
    }

    /** Mode 04: clears codes and freeze frame and resets the readiness tests. True if the car confirmed. */
    suspend fun clearFaults(): Boolean =
        ElmParser.clean(ask("04", timeoutMs = 5_000)).any { it.startsWith("44") }

    fun close() = io.close()
}
