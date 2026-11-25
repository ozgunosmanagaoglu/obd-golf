package app.obdgolf.core

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Talks to an ELM327-compatible adapter (genuine ELM327, cheap clones, STN chips like OBDLink/vLinker):
 * sets it up once, finds out what the car supports, then keeps polling.
 * Fast values (rpm, speed, load, throttle) every loop; slow ones every [slowEvery] loops.
 */
class ObdSession(
    private val io: ElmIo,
    private val slowEvery: Int = 10,
    /** Requests in a row without an answer before we call the link lost (car switched off, adapter gone). */
    private val maxMisses: Int = 6,
    /** Extra PIDs read per slow round. */
    private val extrasPerRound: Int = 4,
) {

    var adapter: AdapterInfo = AdapterInfo()
        private set
    val adapterVersion: String? get() = adapter.elmVersion

    /** Mode 01 PIDs the car said it supports. Unread ranges count as supported. */
    var supported: SupportedPids = SupportedPids()
        private set

    /** From the VIN (mode 09), null when the car does not report one (common before ~2005). */
    var vehicle: VehicleInfo? = null
        private set

    /**
     * Where live requests go. "7DF" asks every control unit (on many cars the engine and the gearbox both
     * answer, with slightly different values). On 11-bit CAN we switch to the engine alone ("7E0") when it answers.
     */
    var homeHeader: String = "7DF"
        private set

    /** The adapter returns as soon as one answer is in ("010C1"), instead of waiting for more units. */
    var singleReply: Boolean = false
        private set

    /** Maker-specific values this car answered while connecting (see [BrandProfiles]). */
    var brandValues: List<BrandValue> = emptyList()
        private set

    // Live polling and fault reading share one adapter: one command at a time.
    private val lock = Mutex()
    private suspend fun ask(command: String, timeoutMs: Long = 2_000) = lock.withLock { io.exchange(command, timeoutMs) }

    /**
     * Sends [command] to one control unit ([header], e.g. 7E0) and switches back to [homeHeader],
     * all under one lock so fault reading never goes to the wrong unit.
     */
    private suspend fun askUnit(header: String, command: String, timeoutMs: Long = 1_000): String? = lock.withLock {
        try {
            io.exchange("ATSH$header", 1_000)
            io.exchange(command, timeoutMs)
        } catch (_: ElmTimeoutException) {
            null
        } finally {
            runCatching { io.exchange("ATSH$homeHeader", 1_000) }
        }
    }

    private suspend fun readBrand(value: BrandValue): Double? =
        askUnit(value.header, value.request)?.let { BrandProfiles.parse(value, it) }

    suspend fun initialize() {
        val reset = resetAdapter()
        val elm = reset.split('\r', '\n').map { it.trim() }.firstOrNull { "ELM" in it }?.let { it.substring(it.indexOf("ELM")) }

        // Echo, linefeeds, spaces and headers off keep replies short and easy to parse.
        for (cmd in listOf("ATE0", "ATL0", "ATS0", "ATH0")) ask(cmd)
        // Adaptive timing: faster on most cars. A clone that does not know it answers "?", which is fine.
        ask("ATAT1")
        // STN chips (OBDLink, vLinker) name themselves here; ELM327 and clones answer "?".
        val stn = runCatching { ask("STI", timeoutMs = 1_000) }.getOrNull()
            ?.split('\r', '\n')?.map { it.trim() }?.firstOrNull { it.startsWith("STN") }
        ask("ATSP0") // find the car's protocol automatically

        // The first real request makes the adapter search for the protocol: CAN takes ~1 s,
        // the older K-line protocols (ISO 9141 / KWP) up to ~5 s.
        val first = try {
            ask("0100", timeoutMs = 10_000)
        } catch (e: ElmTimeoutException) {
            throw VehicleNotRespondingException(null)
        }
        val base = SupportedPids.parseBitmap(0x00, first) ?: throw VehicleNotRespondingException(ElmError.of(first))
        supported = discoverSupported(SupportedPids().with(0x00, base))

        val protocol = runCatching { ObdProtocol.fromDpn(ask("ATDPN")) }.getOrNull()
        adapter = AdapterInfo(elmVersion = elm, stnId = stn, protocol = protocol)
        vehicle = runCatching { Vin.parse(ask("0902", timeoutMs = 3_000)) }.getOrNull()?.let { Vin.decode(it) }
        if (protocol?.isCan11 == true) {
            try {
                useEngineOnly()
            } catch (_: ElmTimeoutException) {
                homeHeader = "7DF"
                singleReply = false
                runCatching { ask("ATSH7DF") }
            }
        }
        brandValues = probeBrand()
    }

    /**
     * Talks to the engine alone when it answers on 7E0: one reply per request, no mixing with the gearbox,
     * and with a reply count ("01001") the adapter does not wait for other units, which makes live values faster.
     */
    private suspend fun useEngineOnly() {
        ask("ATSH7E0")
        if (SupportedPids.parseBitmap(0x00, runCatching { ask("0100") }.getOrDefault("")) == null) {
            ask("ATSH7DF")
            return
        }
        homeHeader = "7E0"
        singleReply = SupportedPids.parseBitmap(0x00, runCatching { ask("01001") }.getOrDefault("")) != null
    }

    /** Tries each value of the car's maker profile once; keeps the ones that answer sensibly. */
    private suspend fun probeBrand(): List<BrandValue> {
        val profile = BrandProfiles.forGroup(vehicle?.group) ?: return emptyList()
        if (adapter.protocol?.isCan11 != true) return emptyList()
        return profile.values.filter { readBrand(it) != null }
    }

    /** ATZ, tried twice: some clones drop the first command after power-up. */
    private suspend fun resetAdapter(): String {
        repeat(2) {
            try {
                return ask("ATZ", timeoutMs = 5_000)
            } catch (_: ElmTimeoutException) {
            }
        }
        throw AdapterNotRespondingException()
    }

    /** Follows the "next range supported" bit: 0120, 0140, ... up to 01C0. */
    private suspend fun discoverSupported(start: SupportedPids): SupportedPids {
        var s = start
        var base = 0x00
        while (base < 0xC0 && s.hasNextRange(base)) {
            base += 0x20
            val raw = runCatching { ask("01%02X".format(base)) }.getOrNull() ?: break
            val pids = SupportedPids.parseBitmap(base, raw) ?: break
            s = s.with(base, pids)
        }
        return s
    }

    fun poll(): Flow<CarData> = flow {
        val fast = listOf(Pid.RPM, Pid.SPEED, Pid.ENGINE_LOAD, Pid.THROTTLE).filter(supported::isSupported)
        val slow = listOf(Pid.COOLANT_TEMP, Pid.INTAKE_TEMP, Pid.AMBIENT_TEMP).filter(supported::isSupported)
        // Extras only when the car lists them, a few per slow round so the live values stay fast.
        val extras = Pid.EXTRAS.filter(supported::isKnownSupported)
        var nextExtra = 0
        var data = CarData()
        var loop = 0
        var misses = 0
        var windowStart = System.currentTimeMillis()
        var windowLoops = 0

        // One PID that times out or gets "NO DATA" is skipped; many in a row means the car is gone.
        suspend fun read(pid: Pid, counts: Boolean = true): Double? {
            val value = try {
                ElmParser.parsePid(pid, ask(if (singleReply) pid.command + "1" else pid.command))
            } catch (_: ElmTimeoutException) {
                null
            }
            if (!counts) return value
            if (value == null) {
                if (++misses >= maxMisses) throw LocalizedIOException(Str.VEHICLE_LINK_LOST)
            } else {
                misses = 0
            }
            return value
        }

        while (currentCoroutineContext().isActive) {
            for (pid in fast) data = data.with(pid, read(pid))
            if (loop % slowEvery == 0) {
                for (pid in slow) data = data.with(pid, read(pid))
                data = data.copy(batteryV = runCatching { ElmParser.parseVoltage(ask("ATRV")) }.getOrNull())
                repeat(minOf(extrasPerRound, extras.size)) {
                    val pid = extras[nextExtra++ % extras.size]
                    data = data.with(pid, read(pid, counts = false))
                }
                for (v in brandValues) data = data.with(v, readBrand(v))
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
    suspend fun readFaults(nowMs: Long = System.currentTimeMillis()): FaultReport = toAllUnits { readFaultsHere(nowMs) }

    /** Fault codes live in every control unit, so these requests go to all of them (7DF). */
    private suspend fun <T> toAllUnits(block: suspend () -> T): T {
        if (homeHeader == "7DF") return block()
        ask("ATSH7DF")
        try {
            return block()
        } finally {
            runCatching { ask("ATSH$homeHeader") }
        }
    }

    private suspend fun readFaultsHere(nowMs: Long): FaultReport {
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
    suspend fun clearFaults(): Boolean = toAllUnits {
        ElmParser.clean(ask("04", timeoutMs = 5_000)).any { it.startsWith("44") }
    }

    /** Older VW group cars (PQ35/PQ46) can show measuring blocks over VW TP2.0; needs CAN 11 bit 500k. */
    val supportsMeasuringBlocks: Boolean
        get() = vehicle?.group == MakerGroup.VAG && adapter.protocol == ObdProtocol.CAN_11_500

    /**
     * Reads one measuring block over VW TP2.0. Holds the adapter for the whole read (about a second),
     * so live values pause meanwhile, and leaves the adapter in normal OBD mode. Null when the unit or group does not answer.
     */
    suspend fun readMeasuringBlock(unit: Int, group: Int): MeasuringBlock? {
        if (!supportsMeasuringBlocks) return null
        return lock.withLock { Tp20Channel(io, homeHeader).readBlock(unit, group) }
    }

    /**
     * Identification and coding of one VW group control unit, read only: UDS first (ReadDataByIdentifier),
     * then KWP2000 over TP2.0 for older units. Holds the adapter meanwhile and leaves it in normal OBD mode.
     * Null when the unit does not answer either way.
     */
    suspend fun readUnitInfo(unit: ControlUnit): UnitInfo? {
        if (!supportsMeasuringBlocks) return null
        return lock.withLock {
            readUds(unit) ?: Tp20Channel(io, homeHeader).readIdentification(unit.address)?.let { UnitIdent.kwp(unit, it) }
        }
    }

    private suspend fun readUds(unit: ControlUnit): UnitInfo? {
        suspend fun send(cmd: String, timeoutMs: Long = 1_000) = try { io.exchange(cmd, timeoutMs) } catch (_: ElmTimeoutException) { "" }
        val tx = "%03X".format(unit.udsTx)
        val rx = "%03X".format(unit.udsRx)
        try {
            // Answers come from rx; long replies need flow control frames sent to tx (not to rx - 8, the ELM default).
            for (c in listOf("ATSH$tx", "ATCRA$rx", "ATFCSH$tx", "ATFCSD300000", "ATFCSM1")) send(c)
            suspend fun did(id: String) = UnitIdent.udsData(id, send("22$id"))
            val part = did(UnitIdent.DID_PART) ?: return null
            return UnitInfo(
                unit, UnitInfo.Protocol.UDS,
                partNumber = UnitIdent.ascii(part),
                softwareVersion = UnitIdent.ascii(did(UnitIdent.DID_SOFTWARE)),
                hardwareNumber = UnitIdent.ascii(did(UnitIdent.DID_HARDWARE)),
                systemName = UnitIdent.ascii(did(UnitIdent.DID_NAME)),
                coding = did(UnitIdent.DID_CODING)?.toList()?.takeIf { it.isNotEmpty() },
            )
        } finally {
            for (c in listOf("ATFCSM0", "ATCRA", "ATSH$homeHeader")) send(c)
        }
    }

    fun close() = io.close()
}
