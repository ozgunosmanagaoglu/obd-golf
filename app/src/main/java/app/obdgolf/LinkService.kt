package app.obdgolf

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

/**
 * Keeps the app alive while an adapter is connected: a foreground service with a quiet notification,
 * plus a partial wake lock so polling and the drive log go on with the screen off.
 *
 * It owns no connection logic. [ObdViewModel] starts it when the user connects (the app is in front then,
 * which Android 12+ requires), updates its text as the link changes, and stops it on disconnect.
 */
class LinkService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: ""
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: ""
        val n = notification(this, title, text)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(ID, n)
            }
        } catch (e: Exception) {
            // Not allowed right now (e.g. missing Bluetooth permission on Android 14+): the connection
            // still works while the app is open, it just may stop in the background.
            stopSelf()
            return START_NOT_STICKY
        }
        if (wakeLock == null) {
            wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Revora:link")
                .apply { setReferenceCounted(false); acquire(MAX_WAKE_MS) }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        private const val ID = 1
        private const val CHANNEL = "link"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_TITLE = "title"
        /** Safety net: the wake lock never outlives a very long drive even if stop is missed. */
        private const val MAX_WAKE_MS = 6 * 60 * 60 * 1000L

        @Volatile var running = false
            private set

        /** Starts the service; call only while the app is in front. False when Android refused. */
        fun start(context: Context, channelName: String, title: String, text: String): Boolean {
            ensureChannel(context, channelName)
            val intent = Intent(context, LinkService::class.java).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_TEXT, text)
            return try {
                context.startForegroundService(intent)
                running = true
                true
            } catch (e: Exception) {
                running = false
                false
            }
        }

        /** Changes the notification text without restarting the service (allowed from the background). */
        fun update(context: Context, title: String, text: String) {
            if (!running) return
            runCatching { context.getSystemService(NotificationManager::class.java).notify(ID, notification(context, title, text)) }
        }

        fun stop(context: Context) {
            if (!running) return
            running = false
            context.stopService(Intent(context, LinkService::class.java))
        }

        private fun ensureChannel(context: Context, name: String) {
            val nm = context.getSystemService(NotificationManager::class.java)
            // Low importance: no sound, no pop-up, just an icon while connected.
            nm.createNotificationChannel(NotificationChannel(CHANNEL, name, NotificationManager.IMPORTANCE_LOW))
        }

        private fun notification(context: Context, title: String, text: String): Notification {
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_link)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build()
        }
    }
}
