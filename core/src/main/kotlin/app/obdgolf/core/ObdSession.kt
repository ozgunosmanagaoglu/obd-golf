package app.obdgolf.core

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/**
 * Talks to an ELM327: sets it up once, then keeps polling.
 * Fast values (rpm, speed, load, throttle) every loop; slow ones every [slowEvery] loops.
 */
class ObdSession(private val io: ElmIo, private val slowEvery: Int = 10) {

    var adapterVersion: String? = null
        private set

    suspend fun initialize() {
        val reset = io.exchange("ATZ", timeoutMs = 5_000)
        adapterVersion = reset.split('\r', '\n').map { it.trim() }.firstOrNull { it.startsWith("ELM") }
        for (cmd in listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATAT1", "ATSP0")) {
            io.exchange(cmd)
        }
        // First real request makes the adapter detect the protocol (CAN 11-bit 500k on a Mk6).
        io.exchange("0100", timeoutMs = 10_000)
    }

    fun poll(): Flow<CarData> = flow {
        val fast = listOf(Pid.RPM, Pid.SPEED, Pid.ENGINE_LOAD, Pid.THROTTLE)
        val slow = listOf(Pid.COOLANT_TEMP, Pid.INTAKE_TEMP, Pid.AMBIENT_TEMP)
        var data = CarData()
        var loop = 0
        var windowStart = System.currentTimeMillis()
        var windowLoops = 0
        while (currentCoroutineContext().isActive) {
            for (pid in fast) data = data.with(pid, ElmParser.parsePid(pid, io.exchange(pid.command)))
            if (loop % slowEvery == 0) {
                for (pid in slow) data = data.with(pid, ElmParser.parsePid(pid, io.exchange(pid.command)))
                data = data.copy(batteryV = ElmParser.parseVoltage(io.exchange("ATRV")))
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

    fun close() = io.close()
}
