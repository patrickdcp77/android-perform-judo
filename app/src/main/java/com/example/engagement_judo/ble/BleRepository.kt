package com.example.engagement_judo.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

class BleRepository(
    private val appContext: Context,
    private val bluetoothAdapter: BluetoothAdapter?
) {
    data class UiState(
        val scanning: Boolean = false,
        val lastScanError: String? = null,
        val devices: List<BluetoothDevice> = emptyList(),
        val connectingTo: String? = null,
        val connectedTo: String? = null,
        val gattReady: Boolean = false,
        val lastNotifyAtElapsedMs: Long? = null,
        val lastPacket: LiveDataPacket? = null,
        val lastPacketParseError: Boolean = false,
        val notificationsEnabled: Boolean = false,
        val error: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var liveDataChar: BluetoothGattCharacteristic? = null

    private val deviceMap = LinkedHashMap<String, BluetoothDevice>()

    private val notifyCount = AtomicInteger(0)
    private var notifyWindowStartElapsedMs: Long = 0L

    @SuppressLint("MissingPermission")
    fun startScan() {
        val adapter = bluetoothAdapter ?: run {
            _state.value = _state.value.copy(error = "Bluetooth non disponible")
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: run {
            _state.value = _state.value.copy(error = "Scanner BLE non disponible")
            return
        }

        deviceMap.clear()
        _state.value = _state.value.copy(scanning = true, lastScanError = null, devices = emptyList(), error = null)

        val serviceFilter = ScanFilter.Builder()
            .setServiceUuid(android.os.ParcelUuid(BleUuids.JUDO_STREAM_SERVICE))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(listOf(serviceFilter), settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        runCatching { scanner.stopScan(scanCallback) }
        _state.value = _state.value.copy(scanning = false)
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        stopScan()
        disconnect()

        _state.value = _state.value.copy(connectingTo = device.address, connectedTo = null, gattReady = false, error = null)
        gatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        liveDataChar = null
        gatt?.close()
        gatt = null
        _state.value = _state.value.copy(
            connectingTo = null,
            connectedTo = null,
            gattReady = false,
            notificationsEnabled = false
        )
    }

    @SuppressLint("MissingPermission")
    fun enableLiveNotifications() {
        val g = gatt ?: return
        val c = liveDataChar ?: return

        val ok = g.setCharacteristicNotification(c, true)
        if (!ok) {
            _state.value = _state.value.copy(error = "Impossible d'activer les notifications (setCharacteristicNotification=false)")
            return
        }

        val cccd = c.getDescriptor(BleUuids.CLIENT_CHARACTERISTIC_CONFIG)
        if (cccd == null) {
            _state.value = _state.value.copy(error = "CCCD (0x2902) introuvable sur LIVE_DATA")
            return
        }

        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        val wrote = g.writeDescriptor(cccd)
        if (!wrote) {
            _state.value = _state.value.copy(error = "writeDescriptor(CCCD) a échoué")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val key = device.address
            if (!deviceMap.containsKey(key)) {
                deviceMap[key] = device
                _state.value = _state.value.copy(devices = deviceMap.values.toList())
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _state.value = _state.value.copy(scanning = false, lastScanError = "Scan échoué: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = _state.value.copy(error = "GATT erreur status=$status", connectingTo = null, connectedTo = null)
                runCatching { gatt.close() }
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _state.value = _state.value.copy(connectingTo = null, connectedTo = gatt.device.address)
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _state.value = _state.value.copy(connectingTo = null, connectedTo = null, gattReady = false, notificationsEnabled = false)
                    runCatching { gatt.close() }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = _state.value.copy(error = "discoverServices échoué: $status")
                return
            }
            val svc: BluetoothGattService? = gatt.getService(BleUuids.JUDO_STREAM_SERVICE)
            val live = svc?.getCharacteristic(BleUuids.LIVE_DATA)
            if (svc == null || live == null) {
                _state.value = _state.value.copy(error = "Service/char LIVE_DATA introuvable (UUIDs à vérifier)")
                return
            }

            liveDataChar = live
            _state.value = _state.value.copy(gattReady = true, error = null)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (descriptor.uuid == BleUuids.CLIENT_CHARACTERISTIC_CONFIG) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    notifyCount.set(0)
                    notifyWindowStartElapsedMs = SystemClock.elapsedRealtime()
                    _state.value = _state.value.copy(notificationsEnabled = true, error = null)
                } else {
                    _state.value = _state.value.copy(error = "CCCD write status=$status")
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid != BleUuids.LIVE_DATA) return

            val now = SystemClock.elapsedRealtime()
            val payload = characteristic.value ?: return
            val parsed = LiveDataParser.parseV1(payload)
            _state.value = _state.value.copy(
                lastNotifyAtElapsedMs = now,
                lastPacket = parsed,
                lastPacketParseError = parsed == null
            )

            notifyCount.incrementAndGet()
        }
    }
}

