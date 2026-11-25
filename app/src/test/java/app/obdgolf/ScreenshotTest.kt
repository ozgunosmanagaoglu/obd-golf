package app.obdgolf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.obdgolf.bt.PairedDevice
import app.obdgolf.core.CarData
import app.obdgolf.core.Dtc
import app.obdgolf.core.DtcKind
import app.obdgolf.core.FaultReport
import app.obdgolf.core.Lang
import app.obdgolf.core.MakerGroup
import app.obdgolf.core.MilStatus
import app.obdgolf.core.Pid
import app.obdgolf.core.Strings
import app.obdgolf.core.Units
import app.obdgolf.core.VehicleInfo
import app.obdgolf.core.WifiAddress
import app.obdgolf.ui.C
import app.obdgolf.ui.CarScreen
import app.obdgolf.ui.ConnectScreen
import app.obdgolf.ui.DashboardScreen
import app.obdgolf.ui.DetailsScreen
import app.obdgolf.ui.FaultScreen
import app.obdgolf.ui.LocalStrings
import app.obdgolf.ui.LocalUnits
import app.obdgolf.ui.ObdTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the main screens at the sizes Faz 4 promises. CI records them on every build
 * (`recordRoborazziDebug`) and uploads them as the "screenshots" artifact; a layout that crashes fails the build.
 * One subclass per device size; each runs the same screens.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
abstract class ScreenshotTest(private val device: String, private val lang: Lang) {
    @get:Rule val compose = createComposeRule()

    private val data = CarData(
        rpm = 2150.0, speedKmh = 72.0, coolantC = 89.0, intakeC = 31.0, ambientC = 6.0,
        loadPct = 38.0, throttlePct = 17.0, batteryV = 14.1, refreshHz = 6.2,
        extra = mapOf(Pid.FUEL_LEVEL to 64.0, Pid.MAP to 112.0, Pid.TIMING_ADVANCE to 18.5, Pid.OIL_TEMP to 94.0),
    )
    private val link = LinkState.Connected("V-LINK", "ELM327 v2.3 · CAN 11 bit 500k")
    private val vehicle = VehicleInfo("WVWZZZ1KZBP000000", "Volkswagen", MakerGroup.VAG, 2011)

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalStrings provides Strings(lang), LocalUnits provides Units()) {
                ObdTheme { Box(Modifier.fillMaxSize().background(C.Bg)) { content() } }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$device/$name.png")
    }

    @Test fun dashboard() = shot("1-dashboard") {
        AppFrame(Tab.Dashboard, {}) { DashboardScreen(data, link) {} }
    }

    @Test fun details() = shot("2-details") {
        AppFrame(Tab.Details, {}) { DetailsScreen(data, vehicle, link.adapterVersion, measuring = true) }
    }

    @Test fun faults() = shot("3-faults") {
        val report = FaultReport(
            MilStatus(milOn = true, dtcCount = 2),
            listOf(Dtc("P0299", DtcKind.STORED), Dtc("P0301", DtcKind.STORED), Dtc("P0171", DtcKind.PENDING)),
            null, 0L,
        )
        AppFrame(Tab.Faults, {}) { FaultScreen(FaultState.Ready(report), link, {}, {}, {}) }
    }

    @Test fun connect() = shot("4-connect") {
        AppFrame(Tab.Connect, {}) {
            ConnectScreen(
                link = link, bluetoothReady = true,
                devices = listOf(PairedDevice("V-LINK", "00:1D:A5:00:00:01")),
                autoConnect = true, demo = false, permissionMissing = false,
                onGrantPermission = {}, onConnect = {},
                wifiAddress = WifiAddress("192.168.0.10", 35000), onConnectWifi = {},
                bleDevices = emptyList(), bleScanning = false, bleStatus = null, onScanBle = {}, onConnectBle = {},
                onShareLog = {}, onDisconnect = {}, onRefresh = {}, onAutoConnect = {}, onDemo = {},
                units = Units(), onUnits = {}, language = lang, onLanguage = {},
                batteryUnrestricted = false, onOpenBattery = {},
            )
        }
    }

    @Test fun carScreen() = shot("5-car-screen") { CarScreen(data, link) {} }
}

@Config(qualifiers = "w360dp-h640dp-port-xhdpi")
class SmallPhoneScreenshots : ScreenshotTest("small-phone-360x640", Lang.TR)

@Config(qualifiers = "w800dp-h1280dp-port-hdpi")
class TabletScreenshots : ScreenshotTest("tablet-800x1280", Lang.EN)

@Config(qualifiers = "w1024dp-h600dp-land-mdpi")
class HeadUnitScreenshots : ScreenshotTest("head-unit-1024x600", Lang.PL)

@Config(qualifiers = "w1280dp-h720dp-land-mdpi")
class WideHeadUnitScreenshots : ScreenshotTest("head-unit-1280x720", Lang.DE)
