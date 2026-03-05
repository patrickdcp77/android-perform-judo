package com.example.engagement_judo.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

object LiveDataParser {
    // V1 sans température: 2 + 2 = 4 octets par sample
    private const val BYTES_PER_SAMPLE = 4

    /**
     * Parse un paquet LIVE_DATA v1.
     * Retourne null si le payload est incomplet/invalide.
     */
    fun parseV1(payload: ByteArray): LiveDataPacket? {
        if (payload.size < 5) return null // 4 (t0) + 1 (n)

        val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)

        val t0 = bb.int.toLong() and 0xFFFF_FFFFL
        val n = bb.get().toInt() and 0xFF
        if (n <= 0) return LiveDataPacket(t0Ms = t0, samples = emptyList())

        val remaining = bb.remaining()
        val needed = n * BYTES_PER_SAMPLE
        if (remaining < needed) return null

        val samples = ArrayList<LiveSample>(n)
        repeat(n) {
            val aDynMg = bb.short.toInt() // signé
            val engageInst = bb.short.toInt() and 0xFFFF
            samples += LiveSample(aDynMg = aDynMg, engageInst = engageInst)
        }

        return LiveDataPacket(t0Ms = t0, samples = samples)
    }
}

