package com.example.engagement_judo.ble

/**
 * Packet LIVE_DATA v1.
 *
 * Format (little-endian) :
 * - uint32 t0_ms
 * - uint8 n
 * - répété n fois:
 *   - int16 a_dyn_mg
 *   - uint16 engage_inst
 *
 * (La température est ignorée pour le test MPU-only.)
 */
data class LiveDataPacket(
    val t0Ms: Long,
    val samples: List<LiveSample>
)

data class LiveSample(
    val aDynMg: Int,
    val engageInst: Int
)
