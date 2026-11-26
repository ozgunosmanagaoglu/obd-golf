package app.obdgolf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.obdgolf.bt.PairedDevice
import app.obdgolf.core.CarData
import app.obdgolf.core.ControlUnit
import app.obdgolf.core.UnitInfo
import app.obdgolf.core.Dashboard
import app.obdgolf.core.Metric
import app.obdgolf.core.Preset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.obdgolf.ui.ThemeMode
import kotlin.math.sin
import app.obdgolf.core.Dtc
import app.obdgolf.core.DtcKind
import app.obdgolf.core.AdapterCheck
import app.obdgolf.core.DriveAnalysis
import app.obdgolf.core.FaultReport
import app.obdgolf.core.Readiness
import app.obdgolf.core.Lang
import app.obdgolf.core.MakerGroup
import app.obdgolf.core.MilStatus
import app.obdgolf.core.Pid
import app.obdgolf.core.Strings
import app.obdgolf.core.Units
import app.obdgolf.core.VehicleInfo
import app.obdgolf.core.WifiAddress
import app.obdgolf.ui.C
import app.obdgolf.ui.ConnectScreen
import app.obdgolf.ui.DashboardScreen
import app.obdgolf.ui.DetailsScreen
import app.obdgolf.ui.DriveAnalysisCard
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
abstract class ScreenshotTest(private val device: String, private val lang: Lang, private val car: Boolean = false) {
    @get:Rule val compose = createComposeRule()

    private val data = CarData(
        rpm = 2150.0, speedKmh = 72.0, coolantC = 89.0, intakeC = 31.0, ambientC = 6.0,
        loadPct = 38.0, throttlePct = 17.0, batteryV = 14.1, refreshHz = 6.2,
        extra = mapOf(Pid.FUEL_LEVEL to 64.0, Pid.MAP to 112.0, Pid.TIMING_ADVANCE to 18.5, Pid.OIL_TEMP to 94.0),
    )
    private val link = LinkState.Connected("V-LINK", "ELM327 v2.3 · CAN 11 bit 500k")
    private val vehicle = VehicleInfo("WVWZZZ1KZBP000000", "Volkswagen", MakerGroup.VAG, 2011)

    /** A minute of made-up but plausible history, so graph tiles show a line. */
    private val history: Map<String, List<Double>> = Metric.all.associate { m ->
        val now = m.read(data) ?: ((m.range.start + m.range.endInclusive) / 2)
        val span = (m.range.endInclusive - m.range.start) * 0.08
        m.id to List(60) { i -> now + span * sin(i / 6.0) - span * (60 - i) / 120.0 }
    }

    private fun shot(
        name: String,
        theme: ThemeMode = ThemeMode.DARK,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalStrings provides Strings(lang),
                LocalUnits provides Units(),
                LocalDensity provides Density(density.density, fontScale),
            ) {
                ObdTheme(theme) { Box(Modifier.fillMaxSize().background(C.Bg)) { content() } }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$device/$name.png")
    }

    /** The dashboard as the app shows it on this device: in the tab frame on phones and tablets, full screen in a car. */
    @Composable
    private fun Dash(dash: Dashboard, d: CarData = data, l: LinkState = link, editing: Boolean = false) {
        val screen: @Composable () -> Unit = { DashboardScreen(d, l, dash, history, {}, {}, carScreen = car, startEditing = editing) }
        if (car) screen() else AppFrame(Tab.Dashboard, {}, screen)
    }

    /** The preset with its page [page] moved to the front, since a screenshot only shows the first page. */
    private fun Preset.page(page: Int): Dashboard = dashboard.let { it.copy(pages = it.pages.drop(page) + it.pages.take(page)) }

    @Test fun minimal() = shot("1-minimal") { Dash(Preset.MINIMAL.dashboard) }
    @Test fun minimal2() = shot("1-minimal-p2") { Dash(Preset.MINIMAL.page(1)) }
    @Test fun sport() = shot("1-sport") { Dash(Preset.SPORT.dashboard) }
    @Test fun sport2() = shot("1-sport-p2") { Dash(Preset.SPORT.page(1)) }
    @Test fun eco() = shot("1-eco") { Dash(Preset.ECO.dashboard) }
    @Test fun eco2() = shot("1-eco-p2") { Dash(Preset.ECO.page(1)) }
    @Test fun diagnostic() = shot("1-diagnostic") { Dash(Preset.DIAGNOSTIC.dashboard) }
    @Test fun diagnostic2() = shot("1-diagnostic-p2") { Dash(Preset.DIAGNOSTIC.page(1)) }

    @Test fun warning() = shot("1-state-warning") {
        Dash(Preset.MINIMAL.dashboard, data.copy(coolantC = 109.0, batteryV = 11.4))
    }
    @Test fun offline() = shot("1-state-not-connected") { Dash(Preset.MINIMAL.dashboard, CarData(), LinkState.Disconnected) }
    @Test fun editing() = shot("1-state-editing") { Dash(Preset.SPORT.dashboard, editing = true) }
    @Test fun light() = shot("1-theme-light", ThemeMode.LIGHT) { Dash(Preset.SPORT.dashboard) }
    @Test fun contrast() = shot("1-theme-high-contrast", ThemeMode.HIGH_CONTRAST) { Dash(Preset.SPORT.dashboard) }
    @Test fun bigFont() = shot("1-font-1.3x", fontScale = 1.3f) { Dash(Preset.DIAGNOSTIC.dashboard) }

    @Test fun detailsCloneAdapter() = shot("2-details-clone-adapter") {
        AppFrame(Tab.Details, {}) { DetailsScreen(
            data, vehicle, "ELM327 v1.5 · CAN 11 bit 500k",
            adapterCheck = AdapterCheck(
                AdapterCheck.Verdict.LIMITED,
                listOf(AdapterCheck.Reason.FAKE_V15, AdapterCheck.Reason.MISSING_COMMANDS),
                listOf("ATFCSM0"),
            ),
        ) }
    }

    @Test fun details() = shot("2-details") {
        AppFrame(Tab.Details, {}) { DetailsScreen(
            data, vehicle, link.adapterVersion, measuring = true,
            controlUnits = UnitsState.Done(listOf(
                UnitInfo(ControlUnit.ENGINE, UnitInfo.Protocol.KWP2000, "03C906027BH", systemName = "1.4l R4/4V TFSI  0010"),
                UnitInfo(ControlUnit.CENTRAL_ELECTRICS, UnitInfo.Protocol.UDS, "5K0937087AC", "0507",
                    systemName = "BCM PQ35 H+3", coding = listOf(0x0E, 0x1A, 0x00, 0x07, 0x0F, 0x00, 0xC4, 0x20, 0x08, 0x06, 0x1F, 0x00)),
            )),
        ) }
    }

    @Test fun faults() = shot("3-faults") {
        val report = FaultReport(
            MilStatus(milOn = true, dtcCount = 2),
            listOf(Dtc("P0299", DtcKind.STORED), Dtc("P0301", DtcKind.STORED), Dtc("P0171", DtcKind.PENDING)),
            null, 0L,
        )
        AppFrame(Tab.Faults, {}) { FaultScreen(FaultState.Ready(report), link, {}, {}, {}) }
    }

    @Test fun faultsReadiness() = shot("3-faults-readiness") {
        // Codes just cleared: catalyst, EVAP and O2 sensor tests not complete yet.
        val report = FaultReport(MilStatus(milOn = false, dtcCount = 0), emptyList(), null, 0L, Readiness.parse("4101000765 25"))
        AppFrame(Tab.Faults, {}) { FaultScreen(FaultState.Ready(report), link, {}, {}, {}) }
    }

    @Test fun driveAnalysis() = shot("5-drive-analysis") {
        // A cold start with revs, two hard stops, and a thermostat that never let the engine warm up.
        val a = DriveAnalysis(
            score = 72,
            findings = listOf(
                DriveAnalysis.Finding(DriveAnalysis.Kind.NEVER_WARM, 18, value = 63.0),
                DriveAnalysis.Finding(DriveAnalysis.Kind.HARSH_BRAKE, 2),
                DriveAnalysis.Finding(DriveAnalysis.Kind.COLD_REVS, 24),
                DriveAnalysis.Finding(DriveAnalysis.Kind.LONG_IDLE, 4, percent = 22),
            ),
        )
        Column(Modifier.padding(20.dp)) { DriveAnalysisCard(a) }
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

}

@Config(qualifiers = "w360dp-h640dp-port-xhdpi")
class SmallPhoneScreenshots : ScreenshotTest("small-phone-360x640", Lang.TR)

@Config(qualifiers = "w800dp-h1280dp-port-hdpi")
class TabletScreenshots : ScreenshotTest("tablet-800x1280", Lang.EN)

@Config(qualifiers = "w1024dp-h600dp-land-mdpi")
class HeadUnitScreenshots : ScreenshotTest("head-unit-1024x600", Lang.PL, car = true)

@Config(qualifiers = "w1280dp-h720dp-land-mdpi")
class WideHeadUnitScreenshots : ScreenshotTest("head-unit-1280x720", Lang.DE, car = true)

/** Many 1024×600 / 1280×720 head units run at 240 dpi, which leaves much less room in dp. */
@Config(qualifiers = "w683dp-h400dp-land-hdpi")
class DenseHeadUnitScreenshots : ScreenshotTest("head-unit-1024x600-hdpi", Lang.TR, car = true)

@Config(qualifiers = "w853dp-h480dp-land-hdpi")
class DenseWideHeadUnitScreenshots : ScreenshotTest("head-unit-1280x720-hdpi", Lang.EN, car = true)
