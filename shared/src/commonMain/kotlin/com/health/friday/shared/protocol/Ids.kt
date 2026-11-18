package com.health.friday.shared.protocol

import kotlin.random.Random

/** Random identifiers. Not secrets: they only need to be unique. */
object Ids {
    private const val HEX = "0123456789abcdef"

    fun randomHex(byteCount: Int): String {
        val bytes = Random.Default.nextBytes(byteCount)
        val sb = StringBuilder(byteCount * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    fun newMessageId(): String = randomHex(8)   // 16 hex chars
    fun newDeviceId(): String = randomHex(16)   // 32 hex chars, generated once per install
}
