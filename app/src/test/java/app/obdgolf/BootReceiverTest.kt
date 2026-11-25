package app.obdgolf

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BootReceiverTest {
    private val app: android.app.Application = ApplicationProvider.getApplicationContext()

    @Before fun clear() {
        app.getSharedPreferences("obd", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun boot(action: String = Intent.ACTION_BOOT_COMPLETED) = BootReceiver().onReceive(app, Intent(action))

    private fun enable() = app.getSharedPreferences("obd", Context.MODE_PRIVATE).edit().putBoolean(BootReceiver.PREF, true).commit()

    @Test fun offByDefault() {
        ShadowSettings.setCanDrawOverlays(true)
        boot()
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun needsOverlayPermission() {
        enable()
        ShadowSettings.setCanDrawOverlays(false)
        boot()
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun opensTheAppAfterBoot() {
        enable()
        ShadowSettings.setCanDrawOverlays(true)
        boot()
        assertEquals(MainActivity::class.java.name, shadowOf(app).nextStartedActivity?.component?.className)
    }

    @Test fun quickBootOfHeadUnits() {
        enable()
        ShadowSettings.setCanDrawOverlays(true)
        boot("android.intent.action.QUICKBOOT_POWERON")
        assertEquals(MainActivity::class.java.name, shadowOf(app).nextStartedActivity?.component?.className)
    }

    @Test fun ignoresOtherBroadcasts() {
        enable()
        ShadowSettings.setCanDrawOverlays(true)
        boot(Intent.ACTION_SCREEN_ON)
        assertNull(shadowOf(app).nextStartedActivity)
    }
}
