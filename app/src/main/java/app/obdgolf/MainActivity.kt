package app.obdgolf

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import app.obdgolf.bt.PairedDevice
import app.obdgolf.ui.C
import app.obdgolf.ui.ConnectScreen
import app.obdgolf.ui.DashboardScreen
import app.obdgolf.ui.DetailsScreen
import app.obdgolf.ui.FaultScreen
import app.obdgolf.ui.SplashScreen
import app.obdgolf.ui.HistoryScreen
import app.obdgolf.ui.LocalStrings
import app.obdgolf.ui.LocalUnits
import app.obdgolf.ui.s
import app.obdgolf.core.Msg
import app.obdgolf.core.Str
import app.obdgolf.ui.ObdTheme

class MainActivity : ComponentActivity() {
    private val vm: ObdViewModel by viewModels()

    /** Device the user tapped while a permission was missing; connected once it is granted. */
    private var pendingDevice: PairedDevice? = null
    /** True when the dialog was opened by the "Allow" button, so a silent refusal opens app settings. */
    private var fromButton = false

    private val askBluetooth =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val missing = missingBluetoothPermissions()
            vm.setPermissionMissing(missing.isNotEmpty())
            vm.refreshDevices()
            if (missing.isEmpty()) {
                askNotificationsOnce()
                pendingDevice?.let { vm.connect(it) } ?: vm.onReady()
            } else if (fromButton && missing.none { shouldShowRequestPermissionRationale(it) }) {
                // Android (or MIUI) no longer shows the dialog: only the settings page can fix it.
                openAppSettings()
            } else {
                vm.onReady()
            }
            pendingDevice = null
            fromButton = false
        }

    /** Permissions a BLE scan needs: "Nearby devices" on Android 12+, location before that. */
    private fun missingScanPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) missingBluetoothPermissions()
        else listOf(Manifest.permission.ACCESS_FINE_LOCATION).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

    private val askScan =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val missing = missingScanPermissions()
            if (missing.isEmpty()) {
                vm.setPermissionMissing(false)
                vm.scanBle()
            } else if (missing.none { shouldShowRequestPermissionRationale(it) }) {
                openAppSettings()
            } else {
                vm.setBleStatus(Msg.of(Str.SCAN_PERMISSION_NEEDED))
            }
        }

    // Android 13+: the "Connected" notification of the background service needs this. Asked once, after
    // Bluetooth, never again if declined (the service still runs, only its notification stays hidden).
    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private fun askNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val prefs = getSharedPreferences("obd", MODE_PRIVATE)
        if (prefs.getBoolean("notif_asked", false)) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        prefs.edit().putBoolean("notif_asked", true).apply()
        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun scanBleWithPermission() {
        val missing = missingScanPermissions()
        if (missing.isEmpty()) vm.scanBle() else askScan.launch(missing.toTypedArray())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val missing = missingBluetoothPermissions()
        if (missing.isNotEmpty()) {
            askBluetooth.launch(missing.toTypedArray())
        } else {
            askNotificationsOnce()
            vm.onReady()
        }

        setContent {
            val theme by vm.theme.collectAsState()
    val startOnBoot by vm.startOnBoot.collectAsState()
    val overlayAllowed by vm.overlayAllowed.collectAsState()
            ObdTheme(theme) {
                App(
                    vm,
                    onConnect = ::connectWithPermission,
                    onGrantPermission = ::requestBluetoothPermission,
                    onScanBle = ::scanBleWithPermission,
                    onShareLog = ::shareLog,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may come back from the settings page with the permission turned on.
        if (vm.permissionMissing.value) vm.setPermissionMissing(missingBluetoothPermissions().isNotEmpty())
        vm.refreshDevices()
        vm.refreshBattery()
        vm.refreshOverlay()
    }

    private fun missingBluetoothPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN).filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
        } else emptyList()

    private fun connectWithPermission(device: PairedDevice) {
        val missing = missingBluetoothPermissions()
        if (missing.isEmpty()) {
            vm.connect(device)
        } else {
            pendingDevice = device
            fromButton = true
            askBluetooth.launch(missing.toTypedArray())
        }
    }

    private fun requestBluetoothPermission() {
        val missing = missingBluetoothPermissions()
        if (missing.isEmpty()) {
            vm.setPermissionMissing(false)
            return
        }
        fromButton = true
        askBluetooth.launch(missing.toTypedArray())
    }

    private fun shareLog() {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, vm.strings()[Str.SHARE_LOG_SUBJECT])
            .putExtra(Intent.EXTRA_TEXT, vm.exportLog())
        startActivity(Intent.createChooser(send, vm.strings()[Str.SHARE_LOG]))
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

internal enum class Tab(val label: Str) {
    Dashboard(Str.TAB_DASHBOARD), Details(Str.TAB_DATA), Faults(Str.TAB_FAULTS), History(Str.TAB_HISTORY), Connect(Str.CONNECTION)
}

/** Where the app lands after connecting. */
internal val homeTab = Tab.Dashboard

@Composable
private fun App(vm: ObdViewModel, onConnect: (PairedDevice) -> Unit, onGrantPermission: () -> Unit, onScanBle: () -> Unit, onShareLog: () -> Unit) {
    val link by vm.link.collectAsState()
    val language by vm.language.collectAsState()
    var splash by rememberSaveable { mutableStateOf(true) }

    CompositionLocalProvider(LocalStrings provides remember(language) { vm.strings(language) }) {
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        MainContent(vm, onConnect, onGrantPermission, onScanBle, onShareLog)
        // Loading screen on top while Bluetooth and auto-connect start underneath.
        AnimatedVisibility(visible = splash, exit = fadeOut(tween(350))) {
            val status = when (val l = link) {
                is LinkState.Connecting -> if (l.name == DEMO) s(Str.SPLASH_DEMO_STARTING) else s(Str.SPLASH_CONNECTING, l.name)
                is LinkState.Connected -> if (l.name == DEMO) s(Str.SPLASH_DEMO_READY) else s(Str.SPLASH_CONNECTED, l.name)
                else -> s(Str.SPLASH_PREPARING)
            }
            SplashScreen(status) { splash = false }
        }
    }
    }
}

@Composable
private fun MainContent(vm: ObdViewModel, onConnect: (PairedDevice) -> Unit, onGrantPermission: () -> Unit, onScanBle: () -> Unit, onShareLog: () -> Unit) {
    val data by vm.data.collectAsState()
    val link by vm.link.collectAsState()
    val devices by vm.devices.collectAsState()
    val auto by vm.autoConnect.collectAsState()
    val demo by vm.demo.collectAsState()
    val drives by vm.drives.collectAsState()
    val faults by vm.faults.collectAsState()
    val measuring by vm.measuring.collectAsState()
    val block by vm.block.collectAsState()
    val permissionMissing by vm.permissionMissing.collectAsState()
    val wifiAddress by vm.wifiAddress.collectAsState()
    val vehicle by vm.vehicle.collectAsState()
    val bleDevices by vm.bleDevices.collectAsState()
    val bleScanning by vm.bleScanning.collectAsState()
    val bleStatus by vm.bleStatus.collectAsState()
    val units by vm.units.collectAsState()
    val language by vm.language.collectAsState()
    val batteryUnrestricted by vm.batteryUnrestricted.collectAsState()
    val dashboard by vm.dashboard.collectAsState()
    val history by vm.history.collectAsState()
    val theme by vm.theme.collectAsState()
    var tab by rememberSaveable { mutableStateOf(homeTab) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    CompositionLocalProvider(LocalUnits provides units) {
    Box(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding().navigationBarsPadding()) {
        val carMode = landscape && tab == Tab.Dashboard
        ImmersiveWhile(carMode)
        if (carMode) {
            DashboardScreen(data, link, dashboard, history, { tab = Tab.Connect }, vm::setDashboard, carScreen = true)
            return@Box
        }
        val screen: @Composable () -> Unit = {
            when (tab) {
                Tab.Dashboard -> DashboardScreen(data, link, dashboard, history, { tab = Tab.Connect }, vm::setDashboard)
                Tab.Details -> DetailsScreen(
                    data, vehicle, (link as? LinkState.Connected)?.adapterVersion,
                    measuring = measuring && link is LinkState.Connected,
                    block = block,
                    onReadBlock = vm::readBlock,
                )
                Tab.Faults -> FaultScreen(
                    state = faults,
                    link = link,
                    onRead = vm::readFaults,
                    onClear = vm::clearFaults,
                    onLinkClick = { tab = Tab.Connect },
                )
                Tab.History -> HistoryScreen(drives, onDelete = vm::deleteDrive)
                Tab.Connect -> ConnectScreen(
                    link = link,
                    bluetoothReady = vm.bluetoothReady,
                    devices = devices,
                    autoConnect = auto,
                    demo = demo,
                    permissionMissing = permissionMissing,
                    onGrantPermission = onGrantPermission,
                    onConnect = { onConnect(it); tab = homeTab },
                    wifiAddress = wifiAddress,
                    onConnectWifi = { vm.connectWifi(it); tab = homeTab },
                    bleDevices = bleDevices,
                    bleScanning = bleScanning,
                    bleStatus = bleStatus,
                    onScanBle = onScanBle,
                    onConnectBle = { vm.connectBle(it); tab = homeTab },
                    onShareLog = onShareLog,
                    onDisconnect = vm::disconnect,
                    onRefresh = vm::refreshDevices,
                    onAutoConnect = vm::setAutoConnect,
                    onDemo = { vm.setDemo(it); if (it) tab = homeTab },
                    units = units,
                    onUnits = vm::setUnits,
                    language = language,
                    onLanguage = vm::setLanguage,
                    batteryUnrestricted = batteryUnrestricted,
                    onOpenBattery = vm::openBatterySettings,
                    theme = theme,
                    onTheme = vm::setTheme,
                    startOnBoot = startOnBoot,
                    onStartOnBoot = vm::setStartOnBoot,
                    overlayAllowed = overlayAllowed,
                    onAllowOverlay = vm::openOverlaySettings,
                )
            }
        }
        AppFrame(tab, { tab = it }, screen)
    }
    }
}

/**
 * The tabs around a screen. Phones in portrait get a bottom bar; from [WIDE_DP] (tablets, foldables,
 * landscape phones) the tabs move to a side bar and the content is kept to a readable width.
 */
@Composable
internal fun AppFrame(tab: Tab, onSelect: (Tab) -> Unit, screen: @Composable () -> Unit) {
    if (LocalConfiguration.current.screenWidthDp >= WIDE_DP) {
        Row(Modifier.fillMaxSize()) {
            TabBar(tab, vertical = true, onSelect = onSelect)
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = MAX_CONTENT_DP.dp).fillMaxSize()) { screen() }
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { screen() }
            TabBar(tab, vertical = false, onSelect = onSelect)
        }
    }
}

/** Width from which the tabs move to a side bar (Material's "medium" window class). */
private const val WIDE_DP = 600
/** Wider than this, text lines and cards get hard to read on a tablet. */
private const val MAX_CONTENT_DP = 760

@Composable
private fun TabBar(tab: Tab, vertical: Boolean, onSelect: (Tab) -> Unit) {
    val bar = Modifier.padding(10.dp).clip(RoundedCornerShape(22.dp)).background(C.Card).padding(5.dp)
    val item: @Composable (Tab, Modifier) -> Unit = { t, m ->
        val selected = t == tab
        TextButton(
            onClick = { onSelect(t) },
            modifier = m.heightIn(min = 44.dp),
            shape = RoundedCornerShape(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = if (vertical) 12.dp else 1.dp),
            colors = ButtonDefaults.textButtonColors(containerColor = if (selected) C.Red else Color.Transparent),
        ) {
            Text(s(t.label), color = if (selected) C.OnAccent else C.Text, fontSize = if (vertical) 13.sp else 11.sp,
                maxLines = 1, softWrap = false, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
    if (vertical) {
        Column(bar.fillMaxHeight().width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
            Tab.entries.forEach { item(it, Modifier.fillMaxWidth()) }
        }
    } else {
        Row(bar.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Tab.entries.forEach { item(it, Modifier.weight(1f)) }
        }
    }
}

/**
 * Full screen on the car screen: status and navigation bars hide (a swipe from the edge shows them
 * for a moment), so a head unit shows only the gauges. Restored as soon as the car screen closes.
 */
@Composable
private fun ImmersiveWhile(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(on) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (on) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { if (on) controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}
