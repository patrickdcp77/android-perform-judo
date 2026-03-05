package com.example.engagement_judo

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.engagement_judo.ble.BleRepository
import com.example.engagement_judo.ble.BleViewModel
import com.example.engagement_judo.ui.theme.EngagementjudoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EngagementjudoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BleTestScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun BleTestScreen(
    modifier: Modifier = Modifier,
    vm: BleViewModel = viewModel()
) {
    val state by vm.state.collectAsState()

    var permissionGranted by remember { mutableStateOf(false) }

    val requestPermissions = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionGranted = result.values.all { it }
    }

    LaunchedEffect(Unit) {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        requestPermissions.launch(perms.toTypedArray())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Test BLE (MPU seulement)",
            style = MaterialTheme.typography.titleLarge
        )

        if (!permissionGranted) {
            Text("Permissions BLE non accordées.")
            return@Column
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("État")
                Text("Scan: ${state.scanning}  |  Devices: ${state.devices.size}")
                Text("Connecting: ${state.connectingTo ?: "-"}")
                Text("Connected: ${state.connectedTo ?: "-"}")
                Text("GATT prêt: ${state.gattReady}  |  Notify: ${state.notificationsEnabled}")
                state.lastScanError?.let { Text("Scan error: $it") }
                state.error?.let { Text("Erreur: $it") }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::startScan, enabled = !state.scanning) { Text("Scan") }
            Button(onClick = vm::stopScan, enabled = state.scanning) { Text("Stop") }
            Button(onClick = vm::disconnect, enabled = state.connectedTo != null) { Text("Disconnect") }
            Button(onClick = vm::enableNotifications, enabled = state.gattReady && !state.notificationsEnabled) {
                Text("Enable Notify")
            }
        }

        Text("Périphériques")
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(state.devices) { index, device ->
                DeviceRow(device = device, index = index, onClick = { vm.connect(index) })
                Spacer(Modifier.height(8.dp))
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Dernier LIVE_DATA")
                val packet = state.lastPacket
                if (packet == null) {
                    Text(if (state.lastPacketParseError) "Parse erreur (payload inattendu)" else "- (aucune donnée)")
                } else {
                    Text("t0_ms=${packet.t0Ms} | n=${packet.samples.size}")
                    val last = packet.samples.lastOrNull()
                    if (last != null) {
                        Text("a_dyn_mg=${last.aDynMg} | engage_inst=${last.engageInst}")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: android.bluetooth.BluetoothDevice, index: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("#${index + 1} ${device.name ?: "(sans nom)"}")
            Text(device.address)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewBleScreen() {
    EngagementjudoTheme {
        // Preview statique
        Column(Modifier.padding(16.dp)) {
            Text("Test BLE (MPU seulement)")
        }
    }
}