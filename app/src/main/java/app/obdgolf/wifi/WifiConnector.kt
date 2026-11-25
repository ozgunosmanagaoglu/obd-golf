package app.obdgolf.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import app.obdgolf.core.ElmIo
import app.obdgolf.core.TcpTransport
import app.obdgolf.core.Transport
import app.obdgolf.core.TransportElmIo
import app.obdgolf.core.WifiAddress
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.SocketFactory
import kotlin.coroutines.resume

/**
 * Wi-Fi ELM327 adapters (192.168.0.10:35000 on most of them).
 *
 * The adapter's Wi-Fi has no internet, so Android would normally keep sending traffic over
 * mobile data. We ask for the Wi-Fi network explicitly and open the socket on it.
 */
class WifiConnector(context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    suspend fun connect(address: WifiAddress): ElmIo {
        val wifi = wifiNetwork()
        val factory = wifi?.network?.socketFactory ?: SocketFactory.getDefault()
        val transport = try {
            TcpTransport.open(address, socketFactory = factory)
        } catch (e: Exception) {
            wifi?.release?.invoke()
            throw e
        }
        // Keep the network request alive while connected; release it with the socket.
        return TransportElmIo(object : Transport by transport {
            override fun close() {
                transport.close()
                wifi?.release?.invoke()
            }
        })
    }

    private class WifiNetwork(val network: Network, val release: () -> Unit)

    /** The current Wi-Fi network even without internet, or null if there is none within a few seconds. */
    private suspend fun wifiNetwork(): WifiNetwork? {
        val manager = cm ?: return null
        return withTimeoutOrNull(4_000) {
            suspendCancellableCoroutine<WifiNetwork?> { cont ->
                val done = AtomicBoolean(false)
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        if (done.compareAndSet(false, true)) {
                            cont.resume(WifiNetwork(network) { runCatching { manager.unregisterNetworkCallback(this) } })
                        }
                    }
                }
                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                try {
                    manager.requestNetwork(request, callback)
                } catch (e: Exception) {
                    // No permission or no Wi-Fi hardware: fall back to the default socket.
                    if (done.compareAndSet(false, true)) cont.resume(null)
                    return@suspendCancellableCoroutine
                }
                cont.invokeOnCancellation {
                    if (done.compareAndSet(false, true)) runCatching { manager.unregisterNetworkCallback(callback) }
                }
            }
        }
    }
}
