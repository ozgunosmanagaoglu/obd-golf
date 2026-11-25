package app.obdgolf

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Opens Revora when the device starts, for screens built into the car (Android head units start
 * with the ignition). Off by default; turned on in Settings ("Open when the car starts").
 *
 * Android 10+ only lets an app open its screen from the background when it may "display over other
 * apps", so the setting asks for that. Head units that sleep instead of shutting down keep the app
 * open, and the connection loop reconnects by itself when the adapter wakes up.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in BOOT_ACTIONS) return
        if (!startOnBoot(context)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !Settings.canDrawOverlays(context)) return
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
    }

    companion object {
        /** Standard boot, plus the "quick boot" broadcasts many head units send instead. */
        val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
        )

        const val PREF = "start_on_boot"

        fun startOnBoot(context: Context) =
            context.getSharedPreferences("obd", Context.MODE_PRIVATE).getBoolean(PREF, false)
    }
}
