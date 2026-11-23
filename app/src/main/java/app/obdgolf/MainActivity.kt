package app.obdgolf

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import app.obdgolf.ui.C
import app.obdgolf.ui.CarScreen
import app.obdgolf.ui.ConnectScreen
import app.obdgolf.ui.DashboardScreen
import app.obdgolf.ui.DetailsScreen
import app.obdgolf.ui.GarageScreen
import app.obdgolf.ui.ObdTheme

class MainActivity : ComponentActivity() {
    private val vm: ObdViewModel by viewModels()

    private val askBluetooth = registerForActivityResult(ActivityResultContracts.RequestPermission()) { vm.onReady() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            askBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            vm.onReady()
        }

        setContent { ObdTheme { App(vm) } }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshDevices()
    }
}

private enum class Tab(val label: String) { Garage("Garaj"), Dashboard("Gösterge"), Details("Veriler"), Connect("Bağlantı") }

@Composable
private fun App(vm: ObdViewModel) {
    val data by vm.data.collectAsState()
    val link by vm.link.collectAsState()
    val devices by vm.devices.collectAsState()
    val auto by vm.autoConnect.collectAsState()
    val demo by vm.demo.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.Garage) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding().navigationBarsPadding()) {
        if (landscape && (tab == Tab.Dashboard || tab == Tab.Garage)) {
            CarScreen(data, link) { tab = Tab.Connect }
            return@Box
        }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    Tab.Garage -> GarageScreen(data, link, demo, onGas = vm::setGas) { tab = Tab.Connect }
                    Tab.Dashboard -> DashboardScreen(data, link) { tab = Tab.Connect }
                    Tab.Details -> DetailsScreen(data)
                    Tab.Connect -> ConnectScreen(
                        link = link,
                        bluetoothReady = vm.bluetoothReady,
                        devices = devices,
                        autoConnect = auto,
                        demo = demo,
                        onConnect = { vm.connect(it); tab = Tab.Garage },
                        onDisconnect = vm::disconnect,
                        onRefresh = vm::refreshDevices,
                        onAutoConnect = vm::setAutoConnect,
                        onDemo = { vm.setDemo(it); if (it) tab = Tab.Garage },
                    )
                }
            }
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp)).background(C.Card).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Tab.entries.forEach { t ->
                    val selected = t == tab
                    TextButton(
                        onClick = { tab = t },
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.textButtonColors(containerColor = if (selected) C.Red else Color.Transparent),
                    ) {
                        Text(t.label, color = if (selected) Color.White else Color(0xFFD6D7DA), fontSize = 13.sp, maxLines = 1,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}
