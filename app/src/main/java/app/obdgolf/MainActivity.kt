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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import app.obdgolf.bt.PairedDevice
import app.obdgolf.ui.C
import app.obdgolf.ui.CarScreen
import app.obdgolf.ui.ConnectScreen
import app.obdgolf.ui.DashboardScreen
import app.obdgolf.ui.DetailsScreen
import app.obdgolf.ui.FaultScreen
import app.obdgolf.ui.SplashScreen
import app.obdgolf.ui.HistoryScreen
import app.obdgolf.ui.ObdTheme

class MainActivity : ComponentActivity() {
    private val vm: ObdViewModel by viewModels()

    /** Device the user tapped while a permission was missing; connected once it is granted. */
    private var pendingDevice: PairedDevice? = null
    /** True when the dialog was opened by the "İzin ver" button, so a silent refusal opens app settings. */
    private var fromButton = false

    private val askBluetooth =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val missing = missingBluetoothPermissions()
            vm.setPermissionMissing(missing.isNotEmpty())
            vm.refreshDevices()
            if (missing.isEmpty()) {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val missing = missingBluetoothPermissions()
        if (missing.isNotEmpty()) {
            askBluetooth.launch(missing.toTypedArray())
        } else {
            vm.onReady()
        }

        setContent {
            ObdTheme {
                App(vm, onConnect = ::connectWithPermission, onGrantPermission = ::requestBluetoothPermission)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may come back from the settings page with the permission turned on.
        if (vm.permissionMissing.value) vm.setPermissionMissing(missingBluetoothPermissions().isNotEmpty())
        vm.refreshDevices()
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

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private enum class Tab(val label: String) {
    Dashboard("Gösterge"), Details("Veriler"), Faults("Arıza"), History("Geçmiş"), Connect("Bağlantı")
}

/** Where the app lands after connecting. */
private val homeTab = Tab.Dashboard

@Composable
private fun App(vm: ObdViewModel, onConnect: (PairedDevice) -> Unit, onGrantPermission: () -> Unit) {
    val link by vm.link.collectAsState()
    var splash by rememberSaveable { mutableStateOf(true) }

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        MainContent(vm, onConnect, onGrantPermission)
        // Loading screen on top while Bluetooth and auto-connect start underneath.
        AnimatedVisibility(visible = splash, exit = fadeOut(tween(350))) {
            val status = when (val l = link) {
                is LinkState.Connecting -> if (l.name == "Demo") "Demo modu başlatılıyor…" else "${l.name} bağlanıyor…"
                is LinkState.Connected -> if (l.name == "Demo") "Demo modu hazır" else "${l.name} bağlandı"
                else -> "Hazırlanıyor…"
            }
            SplashScreen(status) { splash = false }
        }
    }
}

@Composable
private fun MainContent(vm: ObdViewModel, onConnect: (PairedDevice) -> Unit, onGrantPermission: () -> Unit) {
    val data by vm.data.collectAsState()
    val link by vm.link.collectAsState()
    val devices by vm.devices.collectAsState()
    val auto by vm.autoConnect.collectAsState()
    val demo by vm.demo.collectAsState()
    val drives by vm.drives.collectAsState()
    val faults by vm.faults.collectAsState()
    val permissionMissing by vm.permissionMissing.collectAsState()
    val wifiAddress by vm.wifiAddress.collectAsState()
    var tab by rememberSaveable { mutableStateOf(homeTab) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding().navigationBarsPadding()) {
        if (landscape && tab == Tab.Dashboard) {
            CarScreen(data, link) { tab = Tab.Connect }
            return@Box
        }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    Tab.Dashboard -> DashboardScreen(data, link) { tab = Tab.Connect }
                    Tab.Details -> DetailsScreen(data)
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
                        onDisconnect = vm::disconnect,
                        onRefresh = vm::refreshDevices,
                        onAutoConnect = vm::setAutoConnect,
                        onDemo = { vm.setDemo(it); if (it) tab = homeTab },
                    )
                }
            }
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 10.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp)).background(C.Card).padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Tab.entries.forEach { t ->
                    val selected = t == tab
                    TextButton(
                        onClick = { tab = t },
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 1.dp),
                        colors = ButtonDefaults.textButtonColors(containerColor = if (selected) C.Red else Color.Transparent),
                    ) {
                        Text(t.label, color = if (selected) Color.White else Color(0xFFD6D7DA), fontSize = 11.sp, maxLines = 1,
                            softWrap = false, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}
