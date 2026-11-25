package app.obdgolf.core

import kotlin.math.abs

/**
 * VW TP2.0: the transport older Volkswagen group cars (PQ35/PQ46, about 2004–2013, e.g. Golf Mk6)
 * use for diagnostics beyond the OBD standard. Runs on CAN 11 bit 500k next to OBD.
 *
 * - The tester asks for a channel on 0x200 ("<unit> C0 00 10 00 03 01"); the unit answers on 0x200+unit
 *   with the CAN ids to use ("00 D0 <rx lo> <rx hi> <tx lo> <tx hi> 01").
 * - Channel parameters: "A0 0F 8A FF 32 FF" → "A1 …".
 * - Data frames: first byte is opcode (high nibble) + sequence (low nibble).
 *   0x1_ = last frame, wants an ACK; 0x2_ = more frames follow, no ACK; 0x3_ = last, no ACK; 0x0_ = more, wants ACK.
 *   The first frame of a message carries the 2-byte length. ACK is 0xB0 | (seq + 1).
 * - Keep alive 0xA3, disconnect 0xA8.
 * Inside the channel the unit speaks KWP2000: "10 89" starts a session, "21 GG" reads measuring block GG.
 *
 * This object only builds and parses frames; [Tp20Channel] drives the adapter.
 */
object Tp20 {
    data class Frame(val id: Int, val data: IntArray) {
        val opcode: Int get() = data.firstOrNull()?.let { it and 0xF0 } ?: -1
        val seq: Int get() = (data.firstOrNull() ?: 0) and 0x0F
        val isData: Boolean get() = opcode in listOf(0x00, 0x10, 0x20, 0x30)
        val wantsAck: Boolean get() = opcode == 0x00 || opcode == 0x10
        val isLast: Boolean get() = opcode == 0x10 || opcode == 0x30
        override fun equals(other: Any?) = other is Frame && other.id == id && other.data.contentEquals(data)
        override fun hashCode() = id * 31 + data.contentHashCode()
    }

    fun hex(bytes: IntArray) = bytes.joinToString("") { "%02X".format(it) }

    /** Frames printed by the adapter with headers on (ATH1) and raw CAN (ATCAF0): "201 00 D0 00 03 40 07 01". */
    fun frames(raw: String): List<Frame> = ElmParser.clean(raw).mapNotNull { line ->
        if (line.length < 5 || line.length % 2 == 0) return@mapNotNull null
        val id = line.substring(0, 3).toIntOrNull(16) ?: return@mapNotNull null
        val data = line.substring(3).chunked(2).map { it.toIntOrNull(16) ?: return@mapNotNull null }
        if (data.isEmpty() || data.size > 8) null else Frame(id, data.toIntArray())
    }

    fun setupRequest(unit: Int) = "%02XC00010000301".format(unit)

    /** The ids from the setup reply: (id the unit listens on = our TX, id the unit sends on = our RX). */
    fun parseSetup(unit: Int, raw: String): Pair<Int, Int>? {
        val f = frames(raw).firstOrNull { it.id == 0x200 + unit && it.data.size >= 6 && it.data[1] == 0xD0 } ?: return null
        val rx = f.data[2] or ((f.data[3] and 0x07) shl 8)
        val tx = f.data[4] or ((f.data[5] and 0x07) shl 8)
        return tx to rx
    }

    const val PARAMS = "A00F8AFF32FF"
    const val DISCONNECT = "A8"
    fun ack(seq: Int) = "%02X".format(0xB0 or ((seq + 1) and 0x0F))

    /** Splits one KWP message into data frames, starting at [seq]; the last one asks for an ACK. */
    fun dataFrames(payload: IntArray, seq: Int): List<String> {
        val body = intArrayOf(payload.size shr 8, payload.size and 0xFF) + payload
        val chunks = body.toList().chunked(7)
        return chunks.mapIndexed { i, chunk ->
            val op = if (i == chunks.lastIndex) 0x10 else 0x20
            "%02X".format(op or ((seq + i) and 0x0F)) + hex(chunk.toIntArray())
        }
    }

    /** Puts one message back together from the unit's data frames. Null until the last frame is in. */
    fun reassemble(frames: List<Frame>): IntArray? {
        val data = frames.filter { it.isData }
        if (data.isEmpty() || data.none { it.isLast }) return null
        val bytes = data.flatMap { it.data.drop(1) }
        if (bytes.size < 2) return null
        val len = bytes[0] * 256 + bytes[1]
        if (bytes.size - 2 < len) return null
        return bytes.subList(2, 2 + len).toIntArray()
    }
}

/** One value of a measuring block, decoded from its (type, a, b) triple. */
data class MeasuringValue(val type: Int, val a: Int, val b: Int, val value: Double?, val unit: String, val text: String? = null) {
    /** What to show when the type is not known: the raw bytes. */
    val raw: String get() = "%02X %02X %02X".format(type, a, b)
}

data class MeasuringBlock(val unit: Int, val group: Int, val values: List<MeasuringValue>)

/**
 * KWP2000 measuring blocks: the reply to "21 GG" is "61 GG" followed by triples (type, a, b).
 * The type byte says how to turn a and b into a value and which unit it has, so a block can be shown
 * without knowing the car. Only types we are sure of are decoded; the rest keep their raw bytes.
 */
object MeasuringBlocks {
    fun decode(type: Int, a: Int, b: Int): MeasuringValue {
        fun v(value: Double, unit: String) = MeasuringValue(type, a, b, value, unit)
        return when (type) {
            0x01 -> v(0.2 * a * b, "rpm")
            0x02 -> v(a * 0.002 * b, "%")
            0x03 -> v(0.002 * a * b, "°")
            0x04 -> v(abs(b - 127) * 0.01 * a, "°")
            0x05 -> v(a * (b - 100) * 0.1, "°C")
            0x06 -> v(0.001 * a * b, "V")
            0x07 -> v(0.01 * a * b, "km/h")
            0x08 -> v(0.1 * a * b, "")
            0x0F -> v(0.01 * a * b, "ms")
            0x10 -> MeasuringValue(type, a, b, null, "", text = Integer.toBinaryString(b).padStart(8, '0'))
            0x11 -> MeasuringValue(type, a, b, null, "", text = "${a.toChar()}${b.toChar()}")
            0x12 -> v(0.04 * a * b, "mbar")
            0x14 -> v(a * (b - 128) / 128.0, "%")
            0x15 -> v(0.001 * a * b, "V")
            0x16 -> v(0.001 * a * b, "ms")
            0x17 -> v(b / 256.0 * a, "%")
            0x1A -> v((b - a).toDouble(), "°C")
            0x21 -> if (a == 0) v(0.0, "%") else v(100.0 * b / a, "%")
            0x22 -> v((b - 128) * 0.01 * a, "kW")
            0x23 -> v(0.01 * a * b, "L/h")
            0x24 -> v(a * 2560.0 + b * 10, "km")
            else -> MeasuringValue(type, a, b, null, "")
        }
    }

    /** Parses the KWP reply payload; null for a negative reply ("7F 21 xx") or another group. */
    fun parse(unit: Int, group: Int, payload: IntArray): MeasuringBlock? {
        if (payload.size < 2 || payload[0] != 0x61 || payload[1] != group) return null
        val values = payload.drop(2).chunked(3).filter { it.size == 3 }.map { (t, a, b) -> decode(t, a, b) }
        return MeasuringBlock(unit, group, values)
    }
}

/** Well-known TP2.0 unit addresses. */
enum class VagUnit(val address: Int, val label: Str) {
    ENGINE(0x01, Str.MB_UNIT_ENGINE),
    GEARBOX(0x02, Str.MB_UNIT_GEARBOX),
    CLIMATE(0x08, Str.MB_UNIT_CLIMATE),
    INSTRUMENTS(0x17, Str.MB_UNIT_INSTRUMENTS),
}

/**
 * Reads one measuring block over TP2.0 with a plain ELM327: switches the adapter to raw CAN, opens a channel,
 * starts a KWP session, reads, disconnects and puts the adapter back into normal OBD mode.
 * The caller holds the adapter lock for the whole time.
 */
internal class Tp20Channel(private val io: ElmIo) {
    private suspend fun send(cmd: String, timeoutMs: Long = 1_000): String =
        try { io.exchange(cmd, timeoutMs) } catch (_: ElmTimeoutException) { "" }

    suspend fun readBlock(unit: Int, group: Int): MeasuringBlock? {
        try {
            // Raw CAN with ids shown; 200 ms between frames is plenty for a unit that answers.
            for (c in listOf("ATH1", "ATCAF0", "ATST32", "ATSH200", "ATCRA%03X".format(0x200 + unit))) send(c)
            val (tx, rx) = Tp20.parseSetup(unit, send(Tp20.setupRequest(unit))) ?: return null
            send("ATSH%03X".format(tx))
            send("ATCRA%03X".format(rx))
            if (Tp20.frames(send(Tp20.PARAMS)).none { it.data.firstOrNull() == 0xA1 }) return null

            var mySeq = 0
            suspend fun fromUnit(cmd: String, timeoutMs: Long) = Tp20.frames(send(cmd, timeoutMs)).filter { it.id == rx }

            // Sends one KWP request and collects the answer. The adapter prints every frame that arrives
            // until the line goes quiet, so the answer normally comes back with our last frame.
            suspend fun request(payload: IntArray): IntArray? {
                val got = mutableListOf<Tp20.Frame>()
                for (f in Tp20.dataFrames(payload, mySeq)) {
                    got += fromUnit(f, 2_000)
                    mySeq = (mySeq + 1) and 0x0F
                }
                val acked = mutableListOf<Tp20.Frame>()
                repeat(3) {
                    val needAck = got.lastOrNull { it.isData && it.wantsAck && it !in acked }
                    if (needAck != null) {
                        acked += needAck
                        got += fromUnit(Tp20.ack(needAck.seq), 500)
                    }
                    Tp20.reassemble(got)?.let { return it }
                    if (needAck == null) return null
                }
                return Tp20.reassemble(got)
            }

            val session = request(intArrayOf(0x10, 0x89)) ?: return null
            if (session.firstOrNull() != 0x50) return null
            val reply = request(intArrayOf(0x21, group)) ?: return null
            return MeasuringBlocks.parse(unit, group, reply)
        } finally {
            send(Tp20.DISCONNECT, 300)
            for (c in listOf("ATCRA", "ATSH7DF", "ATST32", "ATCAF1", "ATH0")) send(c)
        }
    }
}
