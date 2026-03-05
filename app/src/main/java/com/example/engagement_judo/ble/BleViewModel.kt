package com.example.engagement_judo.ble

import android.app.Application
import android.bluetooth.BluetoothManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class BleViewModel(app: Application) : AndroidViewModel(app) {
    private val bluetoothManager = app.getSystemService(BluetoothManager::class.java)
    private val adapter = bluetoothManager?.adapter

    private val repo = BleRepository(app.applicationContext, adapter)

    val state: StateFlow<BleRepository.UiState> = repo.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repo.state.value)

    fun startScan() = repo.startScan()
    fun stopScan() = repo.stopScan()
    fun connect(deviceIndex: Int) {
        val device = state.value.devices.getOrNull(deviceIndex) ?: return
        repo.connect(device)
    }

    fun disconnect() = repo.disconnect()
    fun enableNotifications() = repo.enableLiveNotifications()
}

