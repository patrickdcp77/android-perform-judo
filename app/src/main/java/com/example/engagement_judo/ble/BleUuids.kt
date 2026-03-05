package com.example.engagement_judo.ble

import java.util.UUID

/**
 * UUIDs BLE du projet.
 *
 * V1: on garde des placeholders tant que le firmware ESP32 n'a pas figé les UUIDs.
 * Remplace-les par tes UUID 128-bit dès qu'ils sont définis.
 */
object BleUuids {
    // TODO: définir les UUIDs finaux côté ESP32 et les reporter ici.
    val JUDO_STREAM_SERVICE: UUID = UUID.fromString("0000fe40-cc7a-482a-984a-7f2ed5b3e58f")

    val CONTROL: UUID = UUID.fromString("0000fe41-cc7a-482a-984a-7f2ed5b3e58f")
    val LIVE_DATA: UUID = UUID.fromString("0000fe42-cc7a-482a-984a-7f2ed5b3e58f")
    val EVENT_MARK: UUID = UUID.fromString("0000fe43-cc7a-482a-984a-7f2ed5b3e58f")
    val STATUS: UUID = UUID.fromString("0000fe44-cc7a-482a-984a-7f2ed5b3e58f")

    // CCCD standard pour activer les notifications/indications
    val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

