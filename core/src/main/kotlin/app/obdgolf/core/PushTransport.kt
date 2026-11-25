package app.obdgolf.core

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/**
 * A [Transport] for links that push bytes to us in callbacks, like Bluetooth LE notifications.
 * The platform code calls [onBytes] for every notification and [onClosed] when the link drops,
 * and supplies [sendChunk] that writes one GATT packet and waits until the stack accepted it.
 * Writes are cut into [chunkSize]-byte pieces (20 bytes fits every BLE adapter, a bigger MTU allows more).
 */
class PushTransport(
    private val sendChunk: suspend (ByteArray) -> Unit,
    @Volatile var chunkSize: Int = 20,
    private val onClose: () -> Unit = {},
) : Transport {

    private val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    @Volatile private var closedReason: String? = null

    /** Called from the platform callback thread with each received packet. */
    fun onBytes(bytes: ByteArray) {
        if (bytes.isNotEmpty()) incoming.trySend(bytes.copyOf())
    }

    /** The link dropped: pending and future reads fail with [reason]. */
    fun onClosed(reason: String) {
        if (closedReason == null) closedReason = reason
        incoming.close()
    }

    override suspend fun write(data: ByteArray) {
        closedReason?.let { throw IOException(it) }
        var i = 0
        while (i < data.size) {
            val end = minOf(i + chunkSize.coerceAtLeast(1), data.size)
            sendChunk(data.copyOfRange(i, end))
            i = end
        }
    }

    override suspend fun read(timeoutMs: Long): ByteArray {
        val first = incoming.tryReceive().getOrNull()
            ?: run {
                if (incoming.isClosedForReceive) throw IOException(closedReason ?: "Bağlantı kapandı")
                if (timeoutMs <= 0) return ByteArray(0)
                try {
                    withTimeoutOrNull(timeoutMs) { incoming.receive() }
                } catch (_: ClosedReceiveChannelException) {
                    throw IOException(closedReason ?: "Bağlantı kapandı")
                } ?: return ByteArray(0)
            }
        // Join everything that is already waiting, so callers see fewer, bigger pieces.
        var out = first
        while (true) {
            val next = incoming.tryReceive().getOrNull() ?: break
            out += next
        }
        return out
    }

    override fun close() {
        onClosed("Bağlantı kapatıldı")
        onClose()
    }
}
