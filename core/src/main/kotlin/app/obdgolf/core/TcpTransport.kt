package app.obdgolf.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import javax.net.SocketFactory

/**
 * Where a Wi-Fi ELM327 listens. Almost all of them (cheap clones, Vgate iCar Wi-Fi, vLinker Wi-Fi)
 * use 192.168.0.10:35000; a few use 192.168.1.10 or port 23.
 */
data class WifiAddress(val host: String, val port: Int) {
    override fun toString() = "$host:$port"

    companion object {
        val DEFAULT = WifiAddress("192.168.0.10", 35000)

        /** Parses "192.168.0.10:35000" or just "192.168.0.10" (default port). Null if it makes no sense. */
        fun parse(text: String): WifiAddress? {
            val t = text.trim()
            if (t.isEmpty()) return null
            val host = t.substringBefore(':').trim()
            val port = if (':' in t) t.substringAfter(':').trim().toIntOrNull() ?: return null else DEFAULT.port
            if (host.isEmpty() || host.any { it.isWhitespace() } || port !in 1..65535) return null
            return WifiAddress(host, port)
        }
    }
}

/** Opens a TCP link to a Wi-Fi OBD adapter. */
object TcpTransport {
    /**
     * [socketFactory] lets Android bind the socket to the adapter's Wi-Fi network; otherwise
     * the phone may send it over mobile data, since that Wi-Fi has no internet.
     */
    suspend fun open(
        address: WifiAddress,
        timeoutMs: Int = 5_000,
        socketFactory: SocketFactory = SocketFactory.getDefault(),
    ): Transport = withContext(Dispatchers.IO) {
        val socket = socketFactory.createSocket()
        try {
            socket.connect(InetSocketAddress(address.host, address.port), timeoutMs)
            socket.tcpNoDelay = true
        } catch (e: IOException) {
            runCatching { socket.close() }
            throw LocalizedIOException(Msg.of(Str.WIFI_CONNECT_FAILED, address), e)
        }
        StreamTransport(socket.getInputStream(), socket.getOutputStream()) { runCatching { socket.close() } }
    }
}
